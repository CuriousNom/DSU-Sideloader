package curiousnom.dsusideloader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.topjohnwu.superuser.Shell
import curiousnom.dsusideloader.util.FilenameUtils
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object PayloadManager {

    private const val TAG = "PayloadManager"
    private const val BINARY_NAME = "payload-dumper-go"
    private const val TARGET_PATH = "/data/local/tmp/$BINARY_NAME"

    data class PayloadPartition(
        val name: String,
        val sizeInKb: Long,
    ) {
        fun getFormattedSize(): String {
            val mb = sizeInKb / 1024.0
            return if (mb >= 1024.0) {
                String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.0f MB", mb)
            }
        }

        fun isPhysical(): Boolean = SlotPartitionManager.isPhysicalPartition(name)
    }

    /**
     * Extracts bundled payload-dumper-go to /data/local/tmp and sets executable permissions.
     */
    fun ensurePayloadDumper(context: Context): String {
        try {
            val localDest = File(context.filesDir, BINARY_NAME)
            if (!localDest.exists() || localDest.length() == 0L) {
                context.assets.open(BINARY_NAME).use { input ->
                    FileOutputStream(localDest).use { output ->
                        input.copyTo(output)
                    }
                }
                localDest.setExecutable(true, false)
            }

            // Verify or copy to /data/local/tmp where root shell can execute without SELinux denials
            val check = Shell.cmd("test -x '$TARGET_PATH' && ls -s '$TARGET_PATH'").exec()
            val localSize = localDest.length()
            val remoteOk = check.isSuccess && check.out.any { it.trim().isNotEmpty() }

            if (!remoteOk || !File(TARGET_PATH).exists() || File(TARGET_PATH).length() != localSize) {
                Shell.cmd(
                    "cp '${localDest.absolutePath}' '$TARGET_PATH'",
                    "chmod 755 '$TARGET_PATH'",
                ).exec()
                Log.d(TAG, "Deployed payload-dumper-go to $TARGET_PATH")
            }
            return TARGET_PATH
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure payload dumper binary: ${e.message}", e)
            return TARGET_PATH
        }
    }

    /**
     * Checks if a ZIP file contains payload.bin without full extraction.
     */
    fun isPayloadZip(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zis = ZipInputStream(inputStream)
                var count = 0
                var entry = zis.nextEntry
                while (entry != null && count < 30) {
                    val name = entry.name.removePrefix("/")
                    if (name.equals("payload.bin", ignoreCase = true) ||
                        name.equals("payload_properties.txt", ignoreCase = true)
                    ) {
                        return true
                    }
                    count++
                    entry = zis.nextEntry
                }
                false
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if a binary file begins with the OTA payload magic 'CrAU'.
     */
    fun isPayloadBin(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val magic = ByteArray(4)
                val read = stream.read(magic)
                read == 4 &&
                    magic[0] == 0x43.toByte() && // 'C'
                    magic[1] == 0x72.toByte() && // 'r'
                    magic[2] == 0x41.toByte() && // 'A'
                    magic[3] == 0x55.toByte() // 'U'
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves an Android URI to an accessible filesystem path.
     */
    fun getRealPath(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }

        // 1. Decode SAF Document URI (e.g. content://com.android.externalstorage.documents/document/primary%3ADownload%2F...)
        try {
            val decodedUri = Uri.decode(uri.toString())
            if (decodedUri.contains("/document/")) {
                val docPart = decodedUri.substringAfter("/document/")
                if (docPart.startsWith("primary:")) {
                    val subPath = docPart.removePrefix("primary:")
                    val candidate = File("/storage/emulated/0", subPath)
                    if (candidate.exists()) return candidate.absolutePath
                    val candidateMedia = File("/data/media/0", subPath)
                    if (candidateMedia.exists()) return candidateMedia.absolutePath
                    val candidateSd = File("/sdcard", subPath)
                    if (candidateSd.exists()) return candidateSd.absolutePath
                } else if (docPart.startsWith("raw:")) {
                    val rawPath = docPart.removePrefix("raw:")
                    if (File(rawPath).exists()) return rawPath
                } else if (docPart.contains(":")) {
                    val uuid = docPart.substringBefore(":")
                    val subPath = docPart.substringAfter(":")
                    val candidate = File("/storage/$uuid", subPath)
                    if (candidate.exists()) return candidate.absolutePath
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SAF decode failed: ${e.message}")
        }

        // 2. Try resolving via android.system.Os.readlink on proc fd
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val link = android.system.Os.readlink("/proc/self/fd/${pfd.fd}")
                if (link.isNotEmpty() && !link.startsWith("/proc/") && File(link).exists()) {
                    return link
                }
            }
        } catch (_: Exception) {}

        // 3. Try FilenameUtils.getFilePath
        try {
            val path = FilenameUtils.getFilePath(uri, false).removePrefix("file://")
            if (File(path).exists()) {
                return path
            }
        } catch (_: Exception) {}

        // 4. Fallback: match by filename in standard directories
        try {
            val name = FilenameUtils.queryName(context.contentResolver, uri)
            val candidates = listOf(
                File("/storage/emulated/0/Download", name),
                File("/sdcard/Download", name),
                File("/data/media/0/Download", name),
                File("/storage/emulated/0", name),
                File("/sdcard", name),
                File("/data/media/0", name),
            )
            for (c in candidates) {
                if (c.exists()) return c.absolutePath
            }
        } catch (_: Exception) {}

        return null
    }

    /**
     * Lists all partitions in the OTA payload package with sizes.
     */
    fun listPartitions(context: Context, targetPath: String): List<PayloadPartition> {
        val dumper = ensurePayloadDumper(context)
        val result = Shell.cmd("'$dumper' -m -l '$targetPath'").exec()
        if (!result.isSuccess) {
            val errorMsg = (result.err + result.out).joinToString("\n")
            Log.e(TAG, "payload-dumper-go list failed: $errorMsg (code=${result.code})")
            return emptyList()
        }

        val list = mutableListOf<PayloadPartition>()
        for (line in result.out) {
            val parts = line.split(":")
            if (parts.size == 2) {
                val name = parts[0].trim().lowercase()
                val sizeKb = parts[1].trim().toLongOrNull() ?: 0L
                if (name.isNotEmpty()) {
                    list.add(PayloadPartition(name, sizeKb))
                }
            }
        }
        return list
    }

    /**
     * Extracts a single partition from the payload package with live progress reporting.
     */
    fun extractPartition(
        context: Context,
        targetPath: String,
        partitionName: String,
        outputDir: File,
        onProgress: ((Float) -> Unit)? = null,
    ): File? {
        val dumper = ensurePayloadDumper(context)
        outputDir.mkdirs()
        val expectedFile = File(outputDir, "$partitionName.img")
        if (expectedFile.exists()) {
            expectedFile.delete()
        }

        val cmd = "'$dumper' -m -p '$partitionName' -o '${outputDir.absolutePath}' '$targetPath'"
        val callbackList = object : com.topjohnwu.superuser.CallbackList<String>() {
            override fun onAddElement(s: String) {
                val parts = s.split(":")
                if (parts.size == 2 && parts[0].trim().equals(partitionName, ignoreCase = true)) {
                    val pct = parts[1].trim().toFloatOrNull() ?: 0f
                    onProgress?.invoke(pct / 100f)
                }
            }
        }

        val result = Shell.cmd(cmd).to(callbackList).exec()
        if (!result.isSuccess || !expectedFile.exists()) {
            Log.e(TAG, "Failed to extract $partitionName: ${result.err.joinToString("\n")}")
            return null
        }
        return expectedFile
    }
}
