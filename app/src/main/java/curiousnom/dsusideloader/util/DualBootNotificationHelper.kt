package curiousnom.dsusideloader.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.topjohnwu.superuser.Shell
import curiousnom.dsusideloader.MainActivity
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.receiver.RebootToPrimaryReceiver
import curiousnom.dsusideloader.service.PrivilegedProvider

object DualBootNotificationHelper {

    private const val TAG = "DualBootNotification"
    private const val CHANNEL_ID = "dual_boot_status"
    private const val NOTIFICATION_ID = 4001

    fun suppressOfficialDsuNotification() {
        if (!curiousnom.dsusideloader.core.SlotPartitionManager.isRunningInDsu()) {
            Log.d(TAG, "suppressOfficialDsuNotification skipped: not running inside guest OS")
            return
        }
        if (PrivilegedProvider.isConnected()) {
            try {
                PrivilegedProvider.getService().forceStopPackage("com.android.dynsystem")
            } catch (_: Exception) {}
        }
        if (OperationModeUtils.isSuAvailable()) {
            try {
                Shell.cmd(
                    "am force-stop com.android.dynsystem 2>/dev/null",
                    "pm disable-user --user 0 com.android.dynsystem 2>/dev/null || pm disable com.android.dynsystem 2>/dev/null",
                ).submit { result ->
                    Log.d(TAG, "Suppressed official DSU notification in guest OS (exitCode: ${result.code})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to suppress official DSU notification: ${e.message}")
            }
        }
    }

    fun restoreOfficialDsuService() {
        if (OperationModeUtils.isSuAvailable()) {
            try {
                Shell.cmd(
                    "pm enable com.android.dynsystem 2>/dev/null",
                    "pm enable --user 0 com.android.dynsystem 2>/dev/null",
                ).submit { result ->
                    Log.d(TAG, "Restored official DSU service (exitCode: ${result.code})")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore official DSU service: ${e.message}")
            }
        }
    }

    fun showGuestOsNotification(context: Context) {
        Log.d(TAG, "showGuestOsNotification invoked")
        if (OperationModeUtils.isSuAvailable()) {
            try {
                Shell.cmd("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS 2>/dev/null").submit()
            } catch (_: Exception) {}
        }

        // Suppress conflicting official DSU notification in background
        suppressOfficialDsuNotification()

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.dual_boot_manager),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Status of the dual boot secondary OS"
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // Tap notification body: open DSU Sideloader MainActivity
            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
            )

            // Action button: Reboot to Primary OS
            val rebootIntent = Intent(context, RebootToPrimaryReceiver::class.java).apply {
                action = RebootToPrimaryReceiver.ACTION_REBOOT_TO_PRIMARY
            }
            val rebootPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                rebootIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0),
            )

            val romName = DevicePropUtils.getRomName()
            val release = DevicePropUtils.getAndroidRelease()
            val subtext = if (romName.isNotEmpty()) "$romName ($release)" else release

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qs_dsu)
                .setContentTitle(context.getString(R.string.active_secondary_os))
                .setContentText("Running $subtext - ${DevicePropUtils.getActiveSlot()}")
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(contentPendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.reboot_to_primary_os),
                    rebootPendingIntent,
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Posted guest OS dual boot notification")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post guest OS notification: ${e.message}", e)
        }
    }

    fun dismissNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d(TAG, "Dismissed guest OS dual boot notification")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to dismiss notification: ${e.message}")
        }
    }
}
