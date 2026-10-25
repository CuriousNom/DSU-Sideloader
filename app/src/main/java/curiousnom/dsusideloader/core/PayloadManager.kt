package curiousnom.dsusideloader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.topjohnwu.superuser.Shell
import curiousnom.dsusideloader.util.FilenameUtils
import curiousnom.dsusideloader.util.OperationModeUtils
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
        // Fast path 1: Try reading central directory using real filesystem path (< 5ms)
        val realPath = getRealPath(context, uri)
        if (realPath != null) {
            try {
                java.util.zip.ZipFile(File(realPath)).use { zip ->
                    return zip.getEntry("payload.bin") != null ||
                        zip.getEntry("payload_properties.txt") != null
                }
            } catch (_: Exception) {}
        }

        // Fast path 2: Try reading central directory using /proc/self/fd (< 5ms)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val procPath = "/proc/self/fd/${pfd.fd}"
                try {
                    java.util.zip.ZipFile(File(procPath)).use { zip ->
                        return zip.getEntry("payload.bin") != null ||
                            zip.getEntry("payload_properties.txt") != null
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // Fallback: If ZipFile cannot be used, only inspect the first entry without scanning the whole zip
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zis = ZipInputStream(inputStream)
                val entry = zis.nextEntry
                entry != null && (
                    entry.name.equals("payload.bin", ignoreCase = true) ||
                        entry.name.equals("payload_properties.txt", ignoreCase = true)
                    )
            } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads uncompressed sizes of all entries in a ZIP instantly via root unzip -l, Java ZipFile, or Central Directory.
     */
    fun getZipEntrySizes(context: Context, uri: Uri): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        val realPath = getRealPath(context, uri)

        // 1. Root shell fast unzip -l (handles any permissions, scoped storage, ZIP64, and files owned by root)
        if (realPath != null && (com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true || OperationModeUtils.isSuAvailable())) {
            try {
                val res = com.topjohnwu.superuser.Shell.cmd("unzip -l '$realPath'").exec()
                if (res.isSuccess) {
                    for (line in res.out) {
                        val trimmed = line.trim()
                        val parts = trimmed.split(Regex("\\s+"))
                        if (parts.size >= 4) {
                            val size = parts[0].toLongOrNull()
                            val name = parts.last()
                            if (size != null && name.endsWith(".img", ignoreCase = true)) {
                                val cleanName = name.substringAfterLast("/")
                                val partName = cleanName.substringBeforeLast(".")
                                map[name] = size
                                map[cleanName] = size
                                map[partName] = size
                                map[name.lowercase()] = size
                                map[cleanName.lowercase()] = size
                                map[partName.lowercase()] = size
                            }
                        }
                    }
                    if (map.isNotEmpty()) {
                        Log.d(TAG, "getZipEntrySizes: loaded ${map.size} entries via root unzip -l")
                        return map
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "unzip -l via root failed: ${e.message}")
            }
        }

        // 2. Chmod 644 via root + Java ZipFile
        if (realPath != null) {
            try {
                if (com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true || OperationModeUtils.isSuAvailable()) {
                    com.topjohnwu.superuser.Shell.cmd("chmod 644 '$realPath'").exec()
                }
                java.util.zip.ZipFile(File(realPath)).use { zip ->
                    for (entry in zip.entries()) {
                        val name = entry.name
                        val size = entry.size
                        if (size > 0L) {
                            val cleanName = name.substringAfterLast("/")
                            val partName = cleanName.substringBeforeLast(".")
                            map[name] = size
                            map[cleanName] = size
                            map[partName] = size
                            map[name.lowercase()] = size
                            map[cleanName.lowercase()] = size
                            map[partName.lowercase()] = size
                        }
                    }
                    if (map.isNotEmpty()) return map
                }
            } catch (e: Exception) {
                Log.w(TAG, "ZipFile failed on realPath: ${e.message}")
            }
        }

        // 3. FileChannel Central Directory parser on pfd
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val cdSizes = parseZipCentralDirectory(channel)
                    if (cdSizes.isNotEmpty()) {
                        map.putAll(cdSizes)
                        return map
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseZipCentralDirectory failed: ${e.message}")
        }

        return map
    }

    private fun parseZipCentralDirectory(channel: java.nio.channels.FileChannel): Map<String, Long> {
        val map = mutableMapOf<String, Long>()
        try {
            val size = channel.size()
            if (size < 22) return map

            val readLen = minOf(size, 65536L).toInt()
            val buf = java.nio.ByteBuffer.allocate(readLen).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            channel.position(size - readLen)
            channel.read(buf)
            buf.flip()

            var eocdPos = -1
            for (i in readLen - 22 downTo 0) {
                if (buf.getInt(i) == 0x06054b50) {
                    eocdPos = i
                    break
                }
            }
            if (eocdPos == -1) return map

            buf.position(eocdPos + 12)
            val cdSize = buf.getInt().toLong() and 0xFFFFFFFFL
            val cdOffset = buf.getInt().toLong() and 0xFFFFFFFFL

            if (cdSize <= 0 || cdOffset < 0 || cdOffset + cdSize > size) return map

            val cdBuf = java.nio.ByteBuffer.allocate(cdSize.toInt()).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            channel.position(cdOffset)
            channel.read(cdBuf)
            cdBuf.flip()

            while (cdBuf.remaining() >= 46) {
                val sig = cdBuf.getInt()
                if (sig != 0x02014b50) break
                cdBuf.position(cdBuf.position() + 16)
                cdBuf.getInt() // skip compressed size
                val uncompSize = cdBuf.getInt().toLong() and 0xFFFFFFFFL
                val nameLen = cdBuf.getShort().toInt() and 0xFFFF
                val extraLen = cdBuf.getShort().toInt() and 0xFFFF
                val commentLen = cdBuf.getShort().toInt() and 0xFFFF
                cdBuf.position(cdBuf.position() + 8)

                if (cdBuf.remaining() < nameLen) break
                val nameBytes = ByteArray(nameLen)
                cdBuf.get(nameBytes)
                val name = String(nameBytes, Charsets.UTF_8)
                if (cdBuf.remaining() >= extraLen + commentLen) {
                    cdBuf.position(cdBuf.position() + extraLen + commentLen)
                }

                if (name.endsWith(".img", ignoreCase = true) && uncompSize > 0L) {
                    val cleanName = name.substringAfterLast("/")
                    val partName = cleanName.substringBeforeLast(".")
                    map[name] = uncompSize
                    map[cleanName] = uncompSize
                    map[partName] = uncompSize
                    map[name.lowercase()] = uncompSize
                    map[cleanName.lowercase()] = uncompSize
                    map[partName.lowercase()] = uncompSize
                }
            }
        } catch (_: Exception) {}
        return map
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
