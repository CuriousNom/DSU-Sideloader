package curiousnom.dsusideloader.util

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.lsposed.hiddenapibypass.HiddenApiBypass

class DevicePropUtils {

    companion object {

        private val propCache = ConcurrentHashMap<String, String>()

        private val systemPropertiesClass: Class<*>? by lazy {
            try {
                Class.forName("android.os.SystemProperties")
            } catch (_: Exception) {
                null
            }
        }

        fun getSystemProperty(key: String, cache: Boolean = true): String {
            if (!cache) {
                return try {
                    val clazz = systemPropertiesClass ?: return ""
                    val value = HiddenApiBypass.invoke(clazz, null, "get", key)
                    value?.toString() ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
            return propCache.getOrPut(key) {
                try {
                    val clazz = systemPropertiesClass ?: return@getOrPut ""
                    val value = HiddenApiBypass.invoke(clazz, null, "get", key)
                    value?.toString() ?: ""
                } catch (_: Exception) {
                    ""
                }
            }
        }

        /**
         * Check if custom gsid binary prop is set, when this prop is set
         * we will return the minimum percentage allocation allowed by custom gsid binary.
         * If not set, then return default values from gsid (which is 40%)
         *
         * @return Minimum percentage allowed by gsid binary (default is 0.40F)
         * @see :magisk-module
         */
        private val cachedGsidBinaryAllowedPerc: Float by lazy {
            val minAllowed = getSystemProperty("ro.curiousnom.dsusideloader.gsid_min_alloc")
                .ifEmpty { getSystemProperty("ro.vegabobo.dsusideloader.gsid_min_alloc") }
            if (minAllowed.isNotEmpty()) minAllowed.toFloat() else 0.40F
        }

        fun getGsidBinaryAllowedPerc(): Float = cachedGsidBinaryAllowedPerc

        private val cachedDynamicPartitions: Boolean by lazy {
            getSystemProperty("ro.boot.dynamic_partitions") == "true"
        }

        fun hasDynamicPartitions(): Boolean = cachedDynamicPartitions

        private val cachedKernelPageSize: Long by lazy {
            try {
                android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE)
            } catch (e: Exception) {
                4096L
            }
        }

        fun getKernelPageSize(): Long = cachedKernelPageSize

        private val cachedIs16KbPageSize: Boolean by lazy {
            cachedKernelPageSize == 16384L
        }

        fun is16KbPageSize(): Boolean = cachedIs16KbPageSize

        private val cachedKernelPageSizeFormatted: String by lazy {
            val size = cachedKernelPageSize
            if (size > 0L) "${size / 1024} KB" else "4 KB"
        }

        fun getKernelPageSizeFormatted(): String = cachedKernelPageSizeFormatted

        private fun hasDsuMounts(): Boolean {
            return try {
                val mountsFile = File("/proc/mounts")
                if (!mountsFile.exists() || !mountsFile.canRead()) return false
                mountsFile.useLines { lines ->
                    lines.any { line ->
                        line.contains("/mapper/gsi_") ||
                            line.contains("/mapper/dsu") ||
                            line.contains("/gsi_data") ||
                            line.contains("/dsu_data") ||
                            line.contains("/dev/block/by-name/gsi_") ||
                            line.contains("/dev/block/by-name/dsu")
                    }
                }
            } catch (_: Exception) {
                false
            }
        }

        fun isDsuRunning(): Boolean {
            val dsuSlot = getSystemProperty("ro.boot.dsu_slot")
            if (dsuSlot.isNotEmpty()) {
                return true
            }
            val dsuSlotSuffix = getSystemProperty("ro.boot.dsu_slot_suffix")
            if (dsuSlotSuffix.isNotEmpty()) {
                return true
            }
            val sysDsuSlot = getSystemProperty("sys.gsi.dsu_slot")
            if (sysDsuSlot.isNotEmpty()) {
                return true
            }
            if (hasDsuMounts()) {
                return true
            }
            return false
        }

        fun isDsuInstalled(): Boolean {
            return getSystemProperty("ro.gsid.image_installed", cache = false) == "1"
        }

        private val cachedActiveSlot: String by lazy {
            val suffix = getSystemProperty("ro.boot.slot_suffix")
            when (suffix) {
                "_a" -> "Slot A"
                "_b" -> "Slot B"
                else -> if (suffix.isNotEmpty()) "Slot ${suffix.trimStart('_').uppercase()}" else "Slot A"
            }
        }

        fun getActiveSlot(): String = cachedActiveSlot

        private val cachedKernelVersion: String by lazy {
            try {
                val full = System.getProperty("os.version") ?: ""
                full.split("-").firstOrNull() ?: full.ifEmpty { "Linux" }
            } catch (e: Exception) {
                "Linux"
            }
        }

        fun getKernelVersion(): String = cachedKernelVersion

        fun getAndroidRelease(): String {
            return "Android ${android.os.Build.VERSION.RELEASE}"
        }

        private val cachedRomName: String by lazy {
            computeRomName()
        }

        fun getRomName(): String = cachedRomName

        /**
         * Dynamically detects custom ROM flavors and OEM skins from system properties.
         * Returns empty string if no specific ROM skin is detected (pure/generic AOSP).
         */
        private fun computeRomName(): String {
            return try {
                // Xiaomi HyperOS
                val hyperOsName = getSystemProperty("ro.mi.os.version.name")
                if (hyperOsName.isNotEmpty()) {
                    return "Xiaomi HyperOS $hyperOsName".trim()
                }

                // Xiaomi MIUI
                val miuiVersionCode = getSystemProperty("ro.miui.ui.version.code")
                val miuiVersionName = getSystemProperty("ro.miui.ui.version.name")
                if (miuiVersionCode.isNotEmpty() || miuiVersionName.isNotEmpty()) {
                    val code = miuiVersionCode.ifEmpty { miuiVersionName.removePrefix("V") }
                    return if (code.toIntOrNull() != null && code.toInt() >= 816) {
                        "Xiaomi HyperOS"
                    } else {
                        "MIUI ${code.ifEmpty { miuiVersionName }}".trim()
                    }
                }

                // LineageOS
                val lineageVer = getSystemProperty("ro.lineage.display.version")
                    .ifEmpty { getSystemProperty("ro.lineage.version") }
                if (lineageVer.isNotEmpty()) {
                    return "LineageOS $lineageVer"
                }

                // crDroid
                val crdroidVer = getSystemProperty("ro.crdroid.display.version")
                    .ifEmpty { getSystemProperty("ro.crdroid.version") }
                if (crdroidVer.isNotEmpty()) {
                    return "crDroid $crdroidVer"
                }

                // PixelOS
                val pixelOsVer = getSystemProperty("ro.pixelos.version")
                if (pixelOsVer.isNotEmpty()) {
                    return "PixelOS $pixelOsVer"
                }

                // Evolution X
                val evoVer = getSystemProperty("ro.evolution.version")
                if (evoVer.isNotEmpty()) {
                    return "Evolution X $evoVer"
                }

                // ArrowOS
                val arrowVer = getSystemProperty("ro.arrow.version")
                if (arrowVer.isNotEmpty()) {
                    return "ArrowOS $arrowVer"
                }

                // Nothing OS
                val nothingVer = getSystemProperty("ro.nothing.version")
                if (nothingVer.isNotEmpty()) {
                    return "Nothing OS $nothingVer"
                }

                // OxygenOS / ColorOS
                val oxygenVer = getSystemProperty("ro.oxygen.version")
                if (oxygenVer.isNotEmpty()) {
                    return "OxygenOS $oxygenVer"
                }
                val oppoVer = getSystemProperty("ro.build.version.opporom")
                if (oppoVer.isNotEmpty()) {
                    return "ColorOS $oppoVer"
                }

                // Samsung One UI
                val oneUiVer = getSystemProperty("ro.build.version.oneui")
                if (oneUiVer.isNotEmpty()) {
                    return "One UI $oneUiVer"
                }

                ""
            } catch (e: Exception) {
                ""
            }
        }

        private val cachedOsDisplay: String by lazy {
            val rom = getRomName()
            val androidVer = "Android ${android.os.Build.VERSION.RELEASE}"
            when {
                rom.isNotEmpty() -> "$rom ($androidVer)"
                else -> androidVer
            }
        }

        fun getOsDisplay(): String = cachedOsDisplay

        private val cachedDeviceModel: String by lazy {
            val marketName = getSystemProperty("ro.product.marketname")
                .ifEmpty { getSystemProperty("ro.product.vendor.marketname") }
                .ifEmpty { getSystemProperty("ro.product.odm.marketname") }
            if (marketName.isNotEmpty()) {
                marketName
            } else {
                val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
                val model = android.os.Build.MODEL
                if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            }
        }

        fun getDeviceModel(): String = cachedDeviceModel
    }
}
