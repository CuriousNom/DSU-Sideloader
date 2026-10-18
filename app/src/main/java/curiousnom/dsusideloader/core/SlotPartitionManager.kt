package curiousnom.dsusideloader.core

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import curiousnom.dsusideloader.util.DevicePropUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manages physical A/B slot partitions (boot, vendor_boot, dtbo, vbmeta)
 * to enable true dual-booting with different kernels and vendor modules
 * while DSU handles the logical partitions and isolated userdata.
 */
object SlotPartitionManager {

    private const val TAG = "SlotPartitionManager"

    var hasFlashedPhysicalSlot: Boolean = false

    // Set of physical boot partitions that should be flashed to the secondary physical slot
    val PHYSICAL_PARTITIONS = setOf(
        "boot",
        "vendor_boot",
        "init_boot",
        "dtbo",
        "vbmeta",
        "vbmeta_system",
        "vbmeta_vendor",
        "recovery",
    )

    fun isPhysicalPartition(partitionName: String): Boolean {
        return PHYSICAL_PARTITIONS.contains(partitionName.lowercase())
    }

    fun isAbDevice(): Boolean {
        val suffix = DevicePropUtils.getSystemProperty("ro.boot.slot_suffix")
        val abUpdate = DevicePropUtils.getSystemProperty("ro.build.ab_update")
        return suffix.isNotEmpty() || abUpdate == "true"
    }

    fun getCurrentSlotSuffix(): String {
        val suffix = DevicePropUtils.getSystemProperty("ro.boot.slot_suffix")
        return when (suffix) {
            "_a" -> "_a"
            "_b" -> "_b"
            else -> if (suffix.isNotEmpty()) suffix else "_a"
        }
    }

    fun getCurrentSlotIndex(): Int {
        return if (getCurrentSlotSuffix() == "_b") 1 else 0
    }

    /**
     * Determines whether we are currently running inside DSU (guest OS).
     * Fast in-process check without shell execution or service blocking.
     */
    fun isRunningInDsu(): Boolean {
        val prop = DevicePropUtils.isDsuRunning()
        Log.d(TAG, "isRunningInDsu: isDsuRunning=$prop")
        return prop
    }

    fun getRecordedPrimarySlot(): Int? {
        val candidates = listOf(
            "/metadata/gsi/dsu_primary_slot",
            "/data/local/tmp/.dsu_dualboot_active",
        )
        for (path in candidates) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val text = file.readText().trim()
                    val idx = text.substringAfter("primary_slot=").trim().toIntOrNull()
                    if (idx != null) return idx
                }
            } catch (_: Exception) {}
        }
        if (Shell.isAppGrantedRoot() == true) {
            try {
                val res = Shell.cmd("cat /metadata/gsi/dsu_primary_slot 2>/dev/null || cat /data/local/tmp/.dsu_dualboot_active 2>/dev/null").exec()
                val text = res.out.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) {
                    val idx = text.substringAfter("primary_slot=").trim().toIntOrNull()
                    if (idx != null) return idx
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Returns the slot index of the Primary (Host) OS.
     * When running the host OS, it's the current slot.
     * When running inside DSU (guest OS), the host OS is the other slot.
     */
    fun getPrimarySlotIndex(): Int {
        val recorded = getRecordedPrimarySlot()
        if (recorded != null) {
            return recorded
        }
        val current = getCurrentSlotIndex()
        return if (isRunningInDsu()) 1 - current else current
    }

    fun getPrimarySlotSuffix(): String {
        return if (getPrimarySlotIndex() == 0) "_a" else "_b"
    }

    /**
     * Returns the slot index of the Secondary (Guest/DSU) OS.
     * Always the opposite slot of the primary OS.
     */
    fun getSecondarySlotIndex(): Int {
        val primary = getPrimarySlotIndex()
        return 1 - primary
    }

    fun getSecondarySlotSuffix(): String {
        return if (getSecondarySlotIndex() == 0) "_a" else "_b"
    }

    fun getTargetSlotSuffix(): String {
        return getSecondarySlotSuffix()
    }

    fun getTargetSlotIndex(): Int {
        return getSecondarySlotIndex()
    }

    private var cachedDualBootActive: Boolean? = null

    fun hasSecondaryPhysicalSlot(): Boolean {
        if (hasFlashedPhysicalSlot) return true
        cachedDualBootActive?.let { return it }
        val marker1 = File("/data/local/tmp/.dsu_dualboot_active")
        val marker2 = File("/metadata/gsi/dsu_primary_slot")
        var exists = try {
            marker1.exists() || marker2.exists()
        } catch (_: Exception) {
            false
        }
        if (!exists && Shell.isAppGrantedRoot() == true) {
            val res = Shell.cmd("test -f /data/local/tmp/.dsu_dualboot_active -o -f /metadata/gsi/dsu_primary_slot && echo 1").exec()
            exists = res.out.contains("1")
        }
        cachedDualBootActive = exists
        return exists
    }

    fun setSecondaryPhysicalSlot(active: Boolean) {
        hasFlashedPhysicalSlot = active
        cachedDualBootActive = active
        if (active) {
            val primarySlot = getCurrentSlotIndex()
            Shell.cmd(
                "echo 'primary_slot=$primarySlot' > /data/local/tmp/.dsu_dualboot_active",
                "echo 'primary_slot=$primarySlot' > /metadata/gsi/dsu_primary_slot 2>/dev/null || true",
            ).submit()
        } else {
            Shell.cmd(
                "rm -f /data/local/tmp/.dsu_dualboot_active",
                "rm -f /metadata/gsi/dsu_primary_slot",
            ).submit()
        }
    }

    /**
     * Finds the real block device path for a partition on the given slot suffix.
     * Searches standard by-name directories.
     */
    fun findBlockDevice(partitionName: String, slotSuffix: String = getTargetSlotSuffix()): String? {
        val targetName = "${partitionName}$slotSuffix"
        val paths = listOf(
            "/dev/block/by-name/$targetName",
            "/dev/block/bootdevice/by-name/$targetName",
            "/dev/block/mapper/$targetName",
        )

        for (path in paths) {
            val result = Shell.cmd("test -e $path && echo exists").exec()
            if (result.out.contains("exists")) {
                Log.d(TAG, "Found block device for $targetName at $path")
                return path
            }
        }

        // Fallback: search /dev/block
        val search = Shell.cmd("find /dev/block/ -name $targetName 2>/dev/null | head -n 1").exec()
        val found = search.out.firstOrNull()?.trim()
        if (!found.isNullOrEmpty()) {
            Log.d(TAG, "Found block device via search: $found")
            return found
        }

        Log.w(TAG, "Could not find block device for $targetName")
        return null
    }

    /**
     * Patches AVB vbmeta header to disable verification and hashtree.
     * In AVB 2.0 (libavb), offset 120 (0x78) holds a 32-bit big-endian flags field.
     * Bit 0 (0x1) = HASHTREE_DISABLED
     * Bit 1 (0x2) = VERIFICATION_DISABLED
     */
    fun patchVbmetaDisableVerification(bytes: ByteArray): ByteArray {
        if (bytes.size >= 124) {
            // Check magic "AVB0"
            if (bytes[0] == 'A'.code.toByte() &&
                bytes[1] == 'V'.code.toByte() &&
                bytes[2] == 'B'.code.toByte() &&
                bytes[3] == '0'.code.toByte()
            ) {
                // Set flags to 0x00000003 (disable verification and hashtree)
                bytes[120] = 0
                bytes[121] = 0
                bytes[122] = 0
                bytes[123] = 3
                Log.d(TAG, "Patched AVB header: verification and verity disabled")
            }
        }
        return bytes
    }

    const val BACKUP_DIR_PATH = "/sdcard/DSU_Backup"

    fun backupSecondaryPartition(
        partitionName: String,
        targetSlotSuffix: String = getTargetSlotSuffix(),
    ): Boolean {
        val blockDev = findBlockDevice(partitionName, targetSlotSuffix) ?: return false
        val backupFile = "$BACKUP_DIR_PATH/${partitionName}$targetSlotSuffix.img"
        try {
            val existsCheck = Shell.cmd("test -f '$backupFile' && echo exists").exec()
            if (existsCheck.out.contains("exists")) {
                Log.d(TAG, "Backup for $partitionName$targetSlotSuffix already exists at $backupFile")
                return true
            }
            Log.d(TAG, "Backing up original $partitionName$targetSlotSuffix from $blockDev to $backupFile...")
            val result = Shell.cmd(
                "mkdir -p '$BACKUP_DIR_PATH'",
                "dd if='$blockDev' of='$backupFile' bs=4M",
            ).exec()
            val ok = result.isSuccess
            Log.d(TAG, "Backup $partitionName$targetSlotSuffix result: $ok (exitCode=${result.code})")
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "Failed to backup $partitionName$targetSlotSuffix: ${e.message}")
            return false
        }
    }

    fun hasSecondaryBackup(targetSlotSuffix: String = getTargetSlotSuffix()): Boolean {
        return try {
            val res = Shell.cmd("ls '$BACKUP_DIR_PATH'/*$targetSlotSuffix.img 2>/dev/null").exec()
            res.isSuccess && res.out.any { it.trim().endsWith(".img") }
        } catch (_: Exception) {
            false
        }
    }

    fun restoreSecondaryPartitions(targetSlotSuffix: String = getTargetSlotSuffix()): Boolean {
        try {
            val list = Shell.cmd("ls '$BACKUP_DIR_PATH'/*$targetSlotSuffix.img 2>/dev/null").exec()
            if (!list.isSuccess || list.out.isEmpty()) {
                Log.d(TAG, "No backup files found in $BACKUP_DIR_PATH to restore")
                return false
            }
            var allOk = true
            for (filePath in list.out) {
                val path = filePath.trim()
                if (!path.endsWith(".img")) continue
                val fileName = path.substringAfterLast("/")
                val partitionName = fileName.removeSuffix(".img").removeSuffix(targetSlotSuffix)
                val blockDev = findBlockDevice(partitionName, targetSlotSuffix)
                if (blockDev != null) {
                    Log.d(TAG, "Restoring original partition $partitionName from $path to $blockDev...")
                    val res = Shell.cmd("dd if='$path' of='$blockDev' bs=4M").exec()
                    if (!res.isSuccess) {
                        Log.e(TAG, "Failed to restore $partitionName to $blockDev")
                        allOk = false
                    }
                }
            }
            return allOk
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring secondary partitions: ${e.message}", e)
            return false
        }
    }

    /**
     * Flashes an image file of a physical partition directly to the target inactive slot.
     */
    fun flashPhysicalPartition(
        partitionName: String,
        imageFile: File,
        targetSlotSuffix: String = getTargetSlotSuffix(),
    ): Boolean {
        val blockDev = findBlockDevice(partitionName, targetSlotSuffix)
        if (blockDev == null) {
            Log.e(TAG, "Could not find block device for $partitionName$targetSlotSuffix")
            return false
        }

        // Backup existing physical partition before overwriting
        backupSecondaryPartition(partitionName, targetSlotSuffix)

        try {
            if (partitionName.lowercase().startsWith("vbmeta") && imageFile.length() >= 124) {
                patchVbmetaFile(imageFile)
            }

            val cmd = "dd if='${imageFile.absolutePath}' of='$blockDev' bs=4M"
            val result = Shell.cmd(cmd).exec()
            val success = result.isSuccess
            if (success) {
                setSecondaryPhysicalSlot(true)
            }
            Log.d(TAG, "Flashed $partitionName to $blockDev: success=$success (exitCode=${result.code})")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error flashing $partitionName to $blockDev: ${e.message}", e)
            return false
        }
    }

    /**
     * Flashes an input stream of a physical partition to the target inactive slot.
     * Uses 64KB chunk streaming to disk to avoid OutOfMemoryError on large partitions.
     */
    fun flashPhysicalPartition(
        partitionName: String,
        inputStream: InputStream,
        targetSlotSuffix: String = getTargetSlotSuffix(),
        cacheDir: File? = null,
    ): Boolean {
        val blockDev = findBlockDevice(partitionName, targetSlotSuffix)
        if (blockDev == null) {
            Log.e(TAG, "Could not find block device for $partitionName$targetSlotSuffix")
            return false
        }

        // Backup existing physical partition before overwriting
        backupSecondaryPartition(partitionName, targetSlotSuffix)

        val tempDir = cacheDir ?: File("/data/local/tmp")
        if (!tempDir.exists()) {
            tempDir.mkdirs()
        }
        val tempFile = File(tempDir, "dsu_flash_$partitionName.img")
        try {
            // Stream in 64KB chunks to avoid OutOfMemoryError on large boot/vendor_boot images
            FileOutputStream(tempFile).use { out ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                }
            }

            // Patch vbmeta verification directly on disk if this is a vbmeta partition
            if (partitionName.lowercase().startsWith("vbmeta") && tempFile.length() >= 124) {
                patchVbmetaFile(tempFile)
            }

            // Write to block device using root dd
            val cmd = "dd if='${tempFile.absolutePath}' of='$blockDev' bs=4M"
            val result = Shell.cmd(cmd).exec()
            val success = result.isSuccess
            if (success) {
                setSecondaryPhysicalSlot(true)
            }
            Log.d(TAG, "Flashed $partitionName to $blockDev: success=$success (exitCode=${result.code})")
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error flashing $partitionName to $blockDev: ${e.message}", e)
            return false
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun patchVbmetaFile(file: File) {
        try {
            java.io.RandomAccessFile(file, "rw").use { raf ->
                val magic = ByteArray(4)
                raf.readFully(magic)
                if (magic[0] == 'A'.code.toByte() &&
                    magic[1] == 'V'.code.toByte() &&
                    magic[2] == 'B'.code.toByte() &&
                    magic[3] == '0'.code.toByte()
                ) {
                    raf.seek(120)
                    raf.writeInt(3) // 0x00000003: disable verification and hashtree
                    Log.d(TAG, "Patched AVB header in ${file.name}: verity and verification disabled")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to patch AVB flags on ${file.name}: ${e.message}")
        }
    }

    fun ensureBootctlExtracted(context: Context) {
        try {
            val dest = File(context.filesDir, "bootctl")
            if (!dest.exists() || dest.length() == 0L) {
                context.assets.open("bootctl").use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            dest.setExecutable(true, false)
            Log.d(TAG, "Extracted bootctl to ${dest.absolutePath}")

            try {
                val tmpDest = File("/data/local/tmp/bootctl")
                if (!tmpDest.exists() || tmpDest.length() == 0L) {
                    dest.copyTo(tmpDest, overwrite = true)
                    tmpDest.setExecutable(true, false)
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract bundled bootctl: ${e.message}")
        }
    }

    /**
     * Switches the active boot slot using bootctl.
     * Tries system bootctl, bundled bootctl in /data/local/tmp, and other known paths.
     */
    fun setActiveBootSlot(slotIndex: Int): Boolean {
        Log.d(TAG, "setActiveBootSlot: Requesting active boot slot -> $slotIndex")

        // 1. Try system bootctl in PATH
        val r1 = Shell.cmd("bootctl set-active-boot-slot $slotIndex").exec()
        if (r1.isSuccess) {
            Log.d(TAG, "bootctl set-active-boot-slot $slotIndex succeeded via PATH")
            return true
        }

        // 2. Try candidate locations with vendor lib fallback
        val candidates = listOf(
            "/data/local/tmp/bootctl",
            "/data/local/tmp/bootctl.bin",
            "/data/data/curiousnom.dsusideloader/files/bootctl",
            "/data/user/0/curiousnom.dsusideloader/files/bootctl",
            "/data/adb/bootctl",
            "/data/adb/ksu/bin/bootctl",
            "/data/adb/ap/bin/bootctl",
        )
        for (path in candidates) {
            val cmd = "export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:/vendor/lib64:/system/lib64; test -x $path && $path set-active-boot-slot $slotIndex"
            val r = Shell.cmd(cmd).exec()
            if (r.isSuccess) {
                Log.d(TAG, "Switched active boot slot to $slotIndex using $path")
                return true
            }
        }

        Log.e(TAG, "Failed to switch active boot slot to $slotIndex (all methods failed)")
        return false
    }

    fun markBootSuccessful(): Boolean {
        val r1 = Shell.cmd("bootctl mark-boot-successful").exec()
        if (r1.isSuccess) return true
        val r2 = Shell.cmd("export LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:/vendor/lib64:/system/lib64; /data/local/tmp/bootctl mark-boot-successful").exec()
        return r2.isSuccess
    }
}
