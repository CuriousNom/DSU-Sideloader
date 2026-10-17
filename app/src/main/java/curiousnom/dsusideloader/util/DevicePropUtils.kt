package curiousnom.dsusideloader.util

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
            return getActiveSlot() == "Slot B" ||
                getSystemProperty("ro.boot.slot_suffix") == "_b" ||
                getSystemProperty("ro.gsid.image_installed") == "1"
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

        /**
         * Dynamically detects custom ROM flavors and OEM skins from system properties.
         * Returns empty string if no specific ROM skin is detected (pure/generic AOSP).
         */
        fun getRomName(): String {
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

        fun getOsDisplay(): String {
            val rom = getRomName()
            val androidVer = "Android ${android.os.Build.VERSION.RELEASE}"
            return when {
                rom.isNotEmpty() -> "$rom ($androidVer)"
                else -> androidVer
            }
        }

        fun getDeviceModel(): String {
            val marketName = getSystemProperty("ro.product.marketname")
                .ifEmpty { getSystemProperty("ro.product.vendor.marketname") }
                .ifEmpty { getSystemProperty("ro.product.odm.marketname") }
            if (marketName.isNotEmpty()) return marketName

            val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = android.os.Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        }

        private fun getSystemProperty(key: String): String {
            return try {
                val systemPropertiesClass = Class.forName("android.os.SystemProperties")
                val value = HiddenApiBypass.invoke(systemPropertiesClass, null, "get", key)
                value?.toString() ?: ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}
