package curiousnom.dsusideloader.util

import android.content.Context
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

/**
 * DSU Sideloader operation modes
 *
 * ADB (unrooted): Default operation mode when other modes aren't available
 * Features:
 *   - Only prepares selected GSI to be installed using DSU system-app
 *   - Starting DSU installation requires a adb command
 *
 * Shizuku (as adb): When running app with Shizuku permission
 * Features:
 *  - Prepares selected GSI to be installed using DSU system-app
 *  - No adb command is required to start installation
 *  - Track installation progress
 *  - Diagnostics DSU installation
 *
 * Root: When running app with root permissions
 * Features:
 *  - All features presents when using Shizuku mode
 *  - Detects if there is a DSU installed
 *  - Supports reboot into installed DSU without relying on DSU system-app
 *  - Supports built-in DSU installer
 *  - Enhanced DSU installation diagnostics
 *
 * System mode: When running as system-app (eg: when using our Magisk module)
 * Features:
 *  - All features presents when using Shizuku mode
 *  - Custom gsid binary when available
 *  - Fixes for some selinux denials
 *
 *  Root/System mode: When running as system-app with granted root permission
 *  Features:
 *   - All features available in System and Root mode.
 *
 */
enum class OperationMode {
    // ///////////////// priority
    SYSTEM_AND_ROOT, // #1
    SYSTEM, // #2
    ROOT, // #3
    SHIZUKU, // #4
    ADB, // #5
}

class OperationModeUtils {

    companion object {

        fun isSuAvailable(): Boolean {
            val paths = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/data/local/su",
                "/data/adb/ksu/bin/su",
                "/data/adb/ap/bin/su",
                "/data/adb/magisk/su",
            )
            for (path in paths) {
                try {
                    if (java.io.File(path).exists()) return true
                } catch (_: Exception) {}
            }
            val pathEnv = System.getenv("PATH") ?: ""
            for (dir in pathEnv.split(":")) {
                try {
                    if (java.io.File(dir, "su").exists()) return true
                } catch (_: Exception) {}
            }
            return false
        }

        fun getOperationMode(context: Context, checkShizuku: Boolean): OperationMode {
            val hasSu = isSuAvailable()
            if (isDsuPermissionGranted(context)) {
                if (hasSu && Shell.getShell().isRoot) {
                    return OperationMode.SYSTEM_AND_ROOT
                }
                return OperationMode.SYSTEM
            }

            if (hasSu && Shell.getShell().isRoot) {
                return OperationMode.ROOT
            }

            if (checkShizuku && isShizukuPermissionGranted(context)) {
                return OperationMode.SHIZUKU
            }

            return OperationMode.ADB
        }

        fun getOperationModeAsString(operationMode: OperationMode): String {
            return when (operationMode) {
                OperationMode.SYSTEM_AND_ROOT -> "Root/System"
                OperationMode.SYSTEM -> "System"
                OperationMode.ROOT -> "Root"
                OperationMode.ADB -> "ADB"
                OperationMode.SHIZUKU -> "Shizuku"
            }
        }

        private fun isPermissionGranted(context: Context, permission: String): Boolean {
            return context.checkCallingOrSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }

        private fun isDsuPermissionGranted(context: Context): Boolean {
            val dynPermission = "android.permission.INSTALL_DYNAMIC_SYSTEM"
            return isPermissionGranted(context, dynPermission)
        }

        fun isReadLogsPermissionGranted(context: Context): Boolean {
            val readLogsPermission = "android.permission.READ_LOGS"
            return isPermissionGranted(context, readLogsPermission)
        }

        fun isShizukuPermissionGranted(context: Context): Boolean {
            return if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
                isPermissionGranted(context, ShizukuProvider.PERMISSION)
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
