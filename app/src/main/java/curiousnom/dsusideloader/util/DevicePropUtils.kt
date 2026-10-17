package curiousnom.dsusideloader.util

import com.topjohnwu.superuser.Shell
import org.lsposed.hiddenapibypass.HiddenApiBypass

class DevicePropUtils {

    companion object {

        /**
         * Check if custom gsid binary prop is set, when this prop is set
         * we will return the minimum percentage allocation allowed by custom gsid binary.
         * If not set, then return default values from gsid (which is 40%)
         *
         * @return Minimum percentage allowed by gsid binary (default is 0.40F)
         * @see :magisk-module
         */
        fun getGsidBinaryAllowedPerc(): Float {
            val minAllowed = getSystemProperty("ro.curiousnom.dsusideloader.gsid_min_alloc")
                .ifEmpty { getSystemProperty("ro.vegabobo.dsusideloader.gsid_min_alloc") }
            return if (minAllowed.isNotEmpty()) minAllowed.toFloat() else 0.40F
        }

        fun hasDynamicPartitions(): Boolean {
            return getSystemProperty("ro.boot.dynamic_partitions") == "true"
        }

        fun getKernelPageSize(): Long {
            return try {
                android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
            } catch (e: Exception) {
                4096L
            }
        }

        fun is16KbPageSize(): Boolean {
            return getKernelPageSize() == 16384L
        }

        fun getKernelPageSizeFormatted(): String {
            val size = getKernelPageSize()
            return if (size > 0L) "${size / 1024} KB" else "4 KB"
        }

        fun isDsuRunning(): Boolean {
            val prop = getSystemProperty("ro.gsid.image_installed")
            if (prop == "1") return true
            return try {
                val r = Shell.cmd("getprop ro.gsid.image_installed").exec().out.firstOrNull()?.trim()
                r == "1"
            } catch (e: Exception) {
                false
            }
        }

        fun getActiveSlot(): String {
            val suffix = getSystemProperty("ro.boot.slot_suffix")
            return when (suffix) {
                "_a" -> "Slot A"
                "_b" -> "Slot B"
                else -> if (suffix.isNotEmpty()) "Slot $suffix" else "Slot A"
            }
        }

        fun getKernelVersion(): String {
            return try {
                val full = System.getProperty("os.version") ?: ""
                full.split("-").firstOrNull() ?: full.ifEmpty { "Linux" }
            } catch (e: Exception) {
                "Linux"
            }
        }

        fun getAndroidRelease(): String {
            return "Android ${android.os.Build.VERSION.RELEASE}"
        }

        fun getDeviceModel(): String {
            val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = android.os.Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        }

        private fun getSystemProperty(key: String): String {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val value = HiddenApiBypass.invoke(systemPropertiesClass, null, "get", key)
            return value.toString()
        }
    }
}
