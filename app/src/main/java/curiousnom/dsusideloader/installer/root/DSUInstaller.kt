package curiousnom.dsusideloader.installer.root

import android.app.Application
import android.gsi.IGsiService
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.util.Log
import curiousnom.dsusideloader.core.PayloadManager
import curiousnom.dsusideloader.core.SlotPartitionManager
import curiousnom.dsusideloader.model.DSUInstallationSource
import curiousnom.dsusideloader.model.ImagePartition
import curiousnom.dsusideloader.model.Type
import curiousnom.dsusideloader.preparation.InstallationStep
import curiousnom.dsusideloader.service.PrivilegedProvider
import curiousnom.dsusideloader.util.DualBootNotificationHelper
import curiousnom.dsusideloader.util.FilenameUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.URL
import java.nio.ByteBuffer
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * Direct DSU Installer implementation using low-level Android Dynamic System APIs.
 * Directly communicates with IDynamicSystemService / gsid daemon over privileged root IPC.
 *
 * Provides high-speed direct installation bypassing file staging and recompression,
 * with robust chunk verification, buffer error checking, and automated rollback.
 */
class DSUInstaller(
    private val application: Application,
    private val userdataSize: Long,
    private val dsuInstallation: DSUInstallationSource,
    private var installationJob: Job = Job(),
    private val selectedPayloadPartitions: Set<String>? = null,
    private val onInstallationError: (error: InstallationStep, errorInfo: String) -> Unit,
    private val onInstallationProgressUpdate: (progress: Float, partition: String) -> Unit,
    private val onCreatePartition: (partition: String) -> Unit,
    private val onInstallationStepUpdate: (step: InstallationStep) -> Unit,
    private val onInstallationSuccess: () -> Unit,
) : () -> Unit, DynamicSystemImpl() {

    private val tag = this.javaClass.simpleName

    object Constants {
        const val DEFAULT_SLOT = "dsu"
        const val SHARED_MEM_SIZE: Int = 524288
        const val MIN_PROGRESS_TO_PUBLISH = (1 shl 27).toLong()
    }

    private class MappedMemoryBuffer(var mBuffer: ByteBuffer?) : AutoCloseable {
        override fun close() {
            if (mBuffer != null) {
                SharedMemory.unmap(mBuffer!!)
                mBuffer = null
            }
        }
    }

    private val SUPPORTED_LOGICAL_PARTITIONS: Set<String> = setOf(
        "system",
        "system_ext",
        "product",
        "vendor",
        "odm",
        "system_dlkm",
        "vendor_dlkm",
        "odm_dlkm",
        "cust",
        "mi_ext",
    )

    private fun isPartitionSupported(partitionName: String): Boolean {
        val name = partitionName.lowercase()
        if (SlotPartitionManager.isPhysicalPartition(name)) {
            return false
        }
        return SUPPORTED_LOGICAL_PARTITIONS.contains(name) ||
            name.startsWith("my_") ||
            name.endsWith("_dlkm")
    }

    private fun getFdDup(sharedMemory: SharedMemory): ParcelFileDescriptor {
        return HiddenApiBypass.invoke(
            sharedMemory.javaClass,
            sharedMemory,
            "getFdDup",
        ) as ParcelFileDescriptor
    }

    private fun shouldInstallEntry(name: String): Boolean {
        if (!name.endsWith(".img")) {
            return false
        }
        val partitionName = name.substringBeforeLast(".").substringAfterLast("/")
        return isPartitionSupported(partitionName)
    }

    private fun publishProgress(bytesRead: Long, totalBytes: Long, partition: String) {
        var progress = 0F
        if (totalBytes != 0L && bytesRead != 0L) {
            progress = (bytesRead.toFloat() / totalBytes.toFloat())
        }
        onInstallationProgressUpdate(progress, partition)
    }

    private fun safeRollback() {
        try {
            abort()
            remove()
        } catch (e: Exception) {
            Log.w(tag, "Rollback warning: ${e.message}")
        }
        try {
            File(application.cacheDir, "payload_extracted").deleteRecursively()
        } catch (_: Exception) {}
    }

    private fun installWritablePartition(
        partition: String,
        partitionSize: Long,
        readOnly: Boolean = false,
    ): Boolean {
        if (installationJob.isCancelled) return false
        val job = Job()
        var createOk = true
        CoroutineScope(Dispatchers.IO + job).launch {
            if (!createNewPartition(partition, partitionSize, readOnly)) {
                createOk = false
            }
            job.complete()
        }
        publishProgress(0L, partitionSize, partition)
        var prevInstalledSize = 0L
        while (job.isActive) {
            if (installationJob.isCancelled) {
                job.cancel()
                safeRollback()
                return false
            }
            val progress = try { installationProgress } catch (e: Exception) { null }
            val installedSize = progress?.bytes_processed ?: 0L
            if (installedSize > prevInstalledSize + Constants.MIN_PROGRESS_TO_PUBLISH) {
                prevInstalledSize = installedSize
                publishProgress(installedSize, partitionSize, partition)
            }
            runBlocking { delay(100) }
        }

        if (!createOk || installationJob.isCancelled) {
            safeRollback()
            return false
        }

        if (!closePartition()) {
            Log.e(tag, "Failed to close $partition partition")
            safeRollback()
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, partition)
            return false
        }

        if (prevInstalledSize != partitionSize) {
            publishProgress(partitionSize, partitionSize, partition)
        }
        Log.d(
            tag,
            "Partition $partition installed, readOnly: $readOnly, partitionSize: $partitionSize",
        )
        return true
    }

    private fun installImage(
        partition: String,
        uncompressedSize: Long,
        inputStream: InputStream,
        readOnly: Boolean = true,
    ): Boolean {
        if (installationJob.isCancelled) return false
        try {
            val sis = SparseInputStream(
                BufferedInputStream(inputStream),
            )
            var partitionSize = if (sis.unsparseSize != -1L) sis.unsparseSize else uncompressedSize
            if (partitionSize <= 0L) {
                Log.e(tag, "Invalid partition size for $partition: $partitionSize")
                safeRollback()
                onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "$partition (invalid size: $partitionSize)")
                return false
            }
            if (partitionSize % 4096L != 0L) {
                partitionSize = ((partitionSize + 4095L) / 4096L) * 4096L
            }
            onCreatePartition(partition)
            if (!createNewPartition(partition, partitionSize, readOnly) || installationJob.isCancelled) {
                safeRollback()
                return false
            }
            onInstallationStepUpdate(InstallationStep.INSTALLING_ROOTED)
            SharedMemory.create("dsu_buffer_$partition", Constants.SHARED_MEM_SIZE)
                .use { sharedMemory ->
                    MappedMemoryBuffer(sharedMemory.mapReadWrite()).use { mappedBuffer ->
                        val fdDup = getFdDup(sharedMemory)
                        if (!setAshmem(fdDup, sharedMemory.size.toLong())) {
                            Log.e(tag, "setAshmem failed for $partition")
                            installationJob.cancel()
                            safeRollback()
                            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, partition)
                            return false
                        }
                        publishProgress(0L, partitionSize, partition)
                        var installedSize: Long = 0
                        val readBuffer = ByteArray(sharedMemory.size)
                        val buffer = mappedBuffer.mBuffer
                        var numBytesRead: Int
                        while (0 < sis.read(readBuffer, 0, readBuffer.size).also { numBytesRead = it }) {
                            if (installationJob.isCancelled) {
                                safeRollback()
                                return false
                            }
                            buffer!!.position(0)
                            buffer.put(readBuffer, 0, numBytesRead)
                            val ok = submitFromAshmem(numBytesRead.toLong())
                            if (!ok) {
                                Log.e(tag, "submitFromAshmem failed for $partition at $installedSize bytes")
                                installationJob.cancel()
                                safeRollback()
                                onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "$partition chunk write failed")
                                return false
                            }
                            installedSize += numBytesRead.toLong()
                            publishProgress(installedSize, partitionSize, partition)
                        }
                        publishProgress(partitionSize, partitionSize, partition)
                    }
                }

            if (installationJob.isCancelled) {
                safeRollback()
                return false
            }

            if (!closePartition()) {
                Log.e(tag, "Failed to close $partition partition")
                safeRollback()
                onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, partition)
                return false
            }
            Log.d(
                tag,
                "Partition $partition installed, readOnly: $readOnly, partitionSize: $partitionSize",
            )
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error writing partition $partition: ${e.message}", e)
            installationJob.cancel()
            safeRollback()
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "$partition: ${e.message ?: "unknown"}")
            return false
        }
    }

    private fun installStreamingZipUpdate(uri: Uri): Boolean {
        val entrySizes = PayloadManager.getZipEntrySizes(application, uri)
        val stream = openInputStream(uri) ?: run {
            Log.e(tag, "Cannot open stream for $uri")
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "cannot read zip")
            return false
        }
        return stream.use { inputStream ->
            installStreamingZipUpdate(inputStream, entrySizes)
        }
    }

    private fun installStreamingZipUpdate(inputStream: InputStream, entrySizes: Map<String, Long> = emptyMap()): Boolean {
        try {
            val zis = ZipInputStream(inputStream)
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val currentEntry = entry ?: break
                val fileName = currentEntry.name
                if (!fileName.endsWith(".img")) {
                    continue
                }
                val partitionName = fileName.substringBeforeLast(".").substringAfterLast("/").lowercase()

                if (SlotPartitionManager.isPhysicalPartition(partitionName)) {
                    val targetSlotName = "Slot ${SlotPartitionManager.getTargetSlotSuffix().trimStart('_').uppercase()}"
                    Log.d(tag, "Physical boot partition detected: $partitionName. Flashing to $targetSlotName...")
                    onInstallationStepUpdate(InstallationStep.INSTALLING_ROOTED)
                    val flashed = SlotPartitionManager.flashPhysicalPartition(
                        partitionName = partitionName,
                        inputStream = zis,
                        cacheDir = application.cacheDir,
                    )
                    if (flashed) {
                        SlotPartitionManager.setSecondaryPhysicalSlot(true)
                        onInstallationProgressUpdate(1f, "$partitionName ($targetSlotName)")
                        Log.d(tag, "Physical partition $partitionName successfully flashed to $targetSlotName.")
                    } else {
                        Log.w(tag, "Failed or skipped physical partition $partitionName.")
                    }
                } else if (shouldInstallEntry(fileName)) {
                    var realSize = entrySizes[currentEntry.name]
                        ?: entrySizes[currentEntry.name.lowercase()]
                        ?: entrySizes[fileName]
                        ?: entrySizes[fileName.lowercase()]
                        ?: entrySizes[partitionName]
                        ?: entrySizes["$partitionName.img"]
                        ?: currentEntry.size

                    if (realSize <= 0L) {
                        Log.w(tag, "Size unknown for $partitionName, buffering entry to disk to determine size...")
                        val tmpFile = File(application.cacheDir, "temp_$partitionName.img")
                        try {
                            java.io.FileOutputStream(tmpFile).use { outStream ->
                                zis.copyTo(outStream)
                            }
                            realSize = tmpFile.length()
                            Log.d(tag, "Buffered $partitionName: size=$realSize bytes")
                            val ok = FileInputStream(tmpFile).use { inStream ->
                                installImage(partitionName, realSize, inStream)
                            }
                            tmpFile.delete()
                            if (!ok || installationJob.isCancelled) {
                                safeRollback()
                                return false
                            }
                            continue
                        } catch (e: Exception) {
                            tmpFile.delete()
                            Log.e(tag, "Failed to buffer and install $partitionName: ${e.message}", e)
                            safeRollback()
                            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "$partitionName (buffer failed: ${e.message})")
                            return false
                        }
                    }

                    val ok = installImage(partitionName, realSize, zis)
                    if (!ok || installationJob.isCancelled) {
                        safeRollback()
                        return false
                    }
                } else {
                    Log.d(tag, "$fileName installation is not supported, skip it.")
                }
                if (installationJob.isCancelled) {
                    safeRollback()
                    return false
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error streaming zip update: ${e.message}", e)
            installationJob.cancel()
            safeRollback()
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "zip stream: ${e.message}")
            return false
        }
    }

    private fun installImageFromAnEntry(entry: ZipEntry, inputStream: InputStream): Boolean {
        val fileName = entry.name
        Log.d(tag, "Installing: $fileName")
        val partitionName = fileName.substringBeforeLast(".").substringAfterLast("/")
        val uncompressedSize = entry.size
        return installImage(partitionName, uncompressedSize, inputStream)
    }

    private fun installPayloadPackage(uri: Uri): Boolean {
        try {
            val realPath = PayloadManager.getRealPath(application, uri)
            if (realPath == null) {
                Log.e(tag, "Could not resolve real filesystem path for payload: $uri")
                onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "Cannot resolve file path for payload package")
                return false
            }

            Log.d(tag, "Resolved payload package path: $realPath")
            val partitions = PayloadManager.listPartitions(application, realPath)
            if (partitions.isEmpty()) {
                Log.e(tag, "No partitions found in payload: $realPath")
                onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "Invalid or empty OTA payload")
                return false
            }

            val partitionNames = partitions.map { it.name }.toSet()
            Log.d(tag, "Found ${partitions.size} partitions in payload: ${partitionNames.joinToString(", ")}")

            val workspaceDir = File("/data/local/tmp/dsu_payload_workspace")
            if (workspaceDir.exists()) workspaceDir.deleteRecursively()
            workspaceDir.mkdirs()

            val userSelected = selectedPayloadPartitions

            // 1. Physical boot/kernel partitions to flash to secondary slot
            val physicalToFlash = partitions
                .map { it.name }
                .filter { SlotPartitionManager.isPhysicalPartition(it) && (userSelected == null || userSelected.contains(it)) }

            if (physicalToFlash.isNotEmpty()) {
                val targetSlotName = "Slot ${SlotPartitionManager.getTargetSlotSuffix().trimStart('_').uppercase()}"
                Log.d(tag, "Extracting and flashing physical partitions to $targetSlotName: ${physicalToFlash.joinToString(", ")}")
                onInstallationStepUpdate(InstallationStep.INSTALLING_ROOTED)

                for (partName in physicalToFlash) {
                    if (installationJob.isCancelled) {
                        safeRollback()
                        return false
                    }
                    onInstallationProgressUpdate(0f, "$partName ($targetSlotName)")
                    val extractedFile = PayloadManager.extractPartition(
                        context = application,
                        targetPath = realPath,
                        partitionName = partName,
                        outputDir = workspaceDir,
                    ) { progress ->
                        onInstallationProgressUpdate(progress, "$partName ($targetSlotName)")
                    }

                    if (extractedFile != null && extractedFile.exists()) {
                        val flashed = SlotPartitionManager.flashPhysicalPartition(
                            partitionName = partName,
                            imageFile = extractedFile,
                        )
                        extractedFile.delete()
                        if (flashed) {
                            Log.d(tag, "Successfully flashed physical partition $partName to $targetSlotName")
                            onInstallationProgressUpdate(1f, "$partName ($targetSlotName)")
                        } else {
                            Log.w(tag, "Warning: Failed to flash physical partition $partName to $targetSlotName")
                        }
                    } else {
                        Log.w(tag, "Failed to extract physical partition $partName")
                    }
                }
                SlotPartitionManager.setSecondaryPhysicalSlot(true)
            }

            // 2. Logical dynamic partitions to install into DSU
            val logicalToInstall = partitions
                .map { it.name }
                .filter { isPartitionSupported(it) && (userSelected == null || userSelected.contains(it)) }

            Log.d(tag, "Installing logical partitions into DSU: ${logicalToInstall.joinToString(", ")}")

            for (partName in logicalToInstall) {
                if (installationJob.isCancelled) {
                    safeRollback()
                    return false
                }
                onCreatePartition(partName)
                onInstallationProgressUpdate(0f, "$partName (extracting)")

                val extractedFile = PayloadManager.extractPartition(
                    context = application,
                    targetPath = realPath,
                    partitionName = partName,
                    outputDir = workspaceDir,
                ) { progress ->
                    onInstallationProgressUpdate(progress, "$partName (extracting)")
                }

                if (extractedFile == null || !extractedFile.exists()) {
                    Log.e(tag, "Failed to extract logical partition $partName from payload")
                    safeRollback()
                    onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "Failed to extract $partName")
                    return false
                }

                val uncompressedSize = extractedFile.length()
                Log.d(tag, "Writing extracted $partName ($uncompressedSize bytes) into DSU...")
                val installOk = FileInputStream(extractedFile).use { stream ->
                    installImage(partName, uncompressedSize, stream)
                }
                extractedFile.delete()

                if (!installOk || installationJob.isCancelled) {
                    safeRollback()
                    return false
                }
            }

            if (workspaceDir.exists()) {
                workspaceDir.deleteRecursively()
            }
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error installing payload package: ${e.message}", e)
            safeRollback()
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "payload: ${e.message}")
            return false
        }
    }

    private fun startInstallation() {
        try {
            PrivilegedProvider.getService().setDynProp()
            if (isInstalled) {
                onInstallationError(InstallationStep.ERROR_REQUIRES_DISCARD_DSU, "")
                return
            }
            if (isInUse) {
                onInstallationError(InstallationStep.ERROR_ALREADY_RUNNING_DYN_OS, "")
                return
            }
            DualBootNotificationHelper.suppressOfficialDsuNotification()

            if (!startInstallation(Constants.DEFAULT_SLOT)) {
                Log.e(tag, "Failed to initialize DSU slot '${Constants.DEFAULT_SLOT}'")
                onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "slot init")
                return
            }

            // Allocate suggested scratch partition if requested by system (fixes devices with tight super partition limits)
            val scratchSize = try { suggestScratchSize() } catch (e: Exception) { 0L }
            if (scratchSize > 0) {
                Log.d(tag, "Allocating suggested scratch partition: $scratchSize bytes")
                val scratchOk = installWritablePartition("scratch", scratchSize)
                if (!scratchOk || installationJob.isCancelled) {
                    safeRollback()
                    return
                }
            }

            val userdataOk = installWritablePartition("userdata", userdataSize)
            if (!userdataOk || installationJob.isCancelled) {
                safeRollback()
                return
            }

            val imageOk = when (dsuInstallation.type) {
                Type.SINGLE_SYSTEM_IMAGE -> {
                    val rawName = FilenameUtils.queryName(application.contentResolver, dsuInstallation.uri)
                        .substringBeforeLast(".")
                        .lowercase()
                    if (SlotPartitionManager.isPhysicalPartition(rawName)) {
                        val targetSlotName = "Slot ${SlotPartitionManager.getTargetSlotSuffix().trimStart('_').uppercase()}"
                        Log.d(tag, "Flashing single physical partition $rawName to $targetSlotName...")
                        onInstallationStepUpdate(InstallationStep.INSTALLING_ROOTED)
                        val ok = openInputStream(dsuInstallation.uri)?.use { stream ->
                            SlotPartitionManager.flashPhysicalPartition(
                                partitionName = rawName,
                                inputStream = stream,
                                cacheDir = application.cacheDir,
                            )
                        } ?: false
                        if (ok) {
                            SlotPartitionManager.setSecondaryPhysicalSlot(true)
                            onInstallationProgressUpdate(1f, "$rawName ($targetSlotName)")
                        }
                        ok
                    } else {
                        val targetPartition = when {
                            isPartitionSupported(rawName) -> rawName
                            rawName.startsWith("system") -> "system"
                            rawName.startsWith("vendor") -> "vendor"
                            rawName.startsWith("product") -> "product"
                            else -> "system"
                        }
                        installImage(
                            targetPartition,
                            dsuInstallation.fileSize,
                            dsuInstallation.uri,
                        )
                    }
                }

                Type.MULTIPLE_IMAGES -> {
                    installImages(dsuInstallation.images)
                }

                Type.DSU_PACKAGE -> {
                    installStreamingZipUpdate(dsuInstallation.uri)
                }

                Type.PAYLOAD -> {
                    installPayloadPackage(dsuInstallation.uri)
                }

                Type.URL -> {
                    val url = URL(dsuInstallation.uri.toString())
                    url.openStream().use { stream ->
                        installStreamingZipUpdate(stream)
                    }
                }

                else -> false
            }

            if (!imageOk || installationJob.isCancelled) {
                safeRollback()
                return
            }

            if (!finishInstallation()) {
                Log.e(tag, "finishInstallation returned false")
                safeRollback()
                onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "finish")
                return
            }

            Log.d(tag, "Installation finished successfully.")
            DualBootNotificationHelper.suppressOfficialDsuNotification()
            onInstallationSuccess()
        } catch (e: Exception) {
            Log.e(tag, "Unhandled error during direct DSU installation: ${e.message}", e)
            safeRollback()
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, e.message ?: "unknown error")
        }
    }

    private fun installImages(images: List<ImagePartition>): Boolean {
        for (image in images) {
            if (isPartitionSupported(image.partitionName)) {
                val ok = installImage(image.partitionName, image.fileSize, image.uri)
                if (!ok || installationJob.isCancelled) {
                    safeRollback()
                    return false
                }
            }
        }
        return true
    }

    private fun installImage(partitionName: String, uncompressedSize: Long, uri: Uri): Boolean {
        var size = uncompressedSize
        if (size <= 0L) {
            size = FilenameUtils.getLengthFromFile(application, uri)
        }
        val stream = openInputStream(uri) ?: run {
            Log.e(tag, "Cannot open stream for $uri")
            installationJob.cancel()
            safeRollback()
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "cannot read image $partitionName")
            return false
        }
        val result = stream.use {
            installImage(
                partitionName,
                size,
                it,
            )
        }
        if (installationJob.isCancelled || !result) {
            safeRollback()
            return false
        }
        return true
    }

    fun openInputStream(uri: Uri): InputStream? {
        return try {
            application.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(tag, "Error opening InputStream for $uri: ${e.message}")
            null
        }
    }

    fun createNewPartition(partition: String, partitionSize: Long, readOnly: Boolean): Boolean {
        val result = try {
            createPartition(partition, partitionSize, readOnly)
        } catch (e: Exception) {
            Log.e(tag, "Exception during createPartition for $partition: ${e.message}", e)
            IGsiService.INSTALL_ERROR_GENERIC
        }
        if (result != IGsiService.INSTALL_OK) {
            Log.e(
                tag,
                "Failed to create $partition partition, error code: $result (check: IGsiService.INSTALL_*)",
            )
            installationJob.cancel()
            safeRollback()
            onInstallationError(InstallationStep.ERROR_CREATE_PARTITION, "$partition (code $result)")
            return false
        }
        return true
    }

    override fun invoke() {
        startInstallation()
    }
}
