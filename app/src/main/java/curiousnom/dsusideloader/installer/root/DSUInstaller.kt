package curiousnom.dsusideloader.installer.root

import android.app.Application
import android.gsi.IGsiService
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.util.Log
import curiousnom.dsusideloader.core.SlotPartitionManager
import curiousnom.dsusideloader.model.DSUInstallationSource
import curiousnom.dsusideloader.model.ImagePartition
import curiousnom.dsusideloader.model.Type
import curiousnom.dsusideloader.preparation.InstallationStep
import curiousnom.dsusideloader.service.PrivilegedProvider
import java.io.BufferedInputStream
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

    private val UNSUPPORTED_PARTITIONS: Set<String> = setOf(
        "boot",
        "vendor_boot",
        "init_boot",
        "recovery",
        "dtbo",
        "userdata",
        "super_empty",
        "system_other",
        "scratch",
        "metadata",
        "misc",
        "persist",
        "modem",
        "radio",
    )

    private fun isPartitionSupported(partitionName: String): Boolean {
        val name = partitionName.lowercase()
        if (name.startsWith("vbmeta")) {
            return false
        }
        return !UNSUPPORTED_PARTITIONS.contains(name)
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
            val partitionSize = if (sis.unsparseSize != -1L) sis.unsparseSize else uncompressedSize
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

    private fun installStreamingZipUpdate(inputStream: InputStream): Boolean {
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
                    onInstallationProgressUpdate(0.5f, "$partitionName ($targetSlotName)")
                    val flashed = SlotPartitionManager.flashPhysicalPartition(partitionName, zis)
                    if (flashed) {
                        SlotPartitionManager.hasFlashedPhysicalSlot = true
                        onInstallationProgressUpdate(1f, "$partitionName ($targetSlotName)")
                        Log.d(tag, "Physical partition $partitionName successfully flashed to $targetSlotName.")
                    } else {
                        Log.w(tag, "Failed or skipped physical partition $partitionName.")
                    }
                } else if (shouldInstallEntry(fileName)) {
                    val ok = installImageFromAnEntry(currentEntry, zis)
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

    private fun startInstallation() {
        try {
            PrivilegedProvider.getService().setDynProp()
            if (isInUse) {
                onInstallationError(InstallationStep.ERROR_ALREADY_RUNNING_DYN_OS, "")
                return
            }
            if (isInstalled) {
                onInstallationError(InstallationStep.ERROR_REQUIRES_DISCARD_DSU, "")
                return
            }
            forceStopDSU()

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
                    installImage(
                        "system",
                        dsuInstallation.fileSize,
                        dsuInstallation.uri,
                    )
                }

                Type.MULTIPLE_IMAGES -> {
                    installImages(dsuInstallation.images)
                }

                Type.DSU_PACKAGE -> {
                    openInputStream(dsuInstallation.uri)?.use { stream ->
                        installStreamingZipUpdate(stream)
                    } ?: false
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
                uncompressedSize,
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
