package curiousnom.dsusideloader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.topjohnwu.superuser.Shell
import curiousnom.dsusideloader.core.SlotPartitionManager
import curiousnom.dsusideloader.util.DualBootNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RebootToPrimaryReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REBOOT_TO_PRIMARY = "curiousnom.dsusideloader.ACTION_REBOOT_TO_PRIMARY"
        private const val TAG = "RebootToPrimaryReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REBOOT_TO_PRIMARY) {
            Log.d(TAG, "Reboot to primary OS triggered from notification action")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SlotPartitionManager.ensureBootctlExtracted(context)
                    val primarySlot = SlotPartitionManager.getPrimarySlotIndex()
                    val switched = SlotPartitionManager.setActiveBootSlot(primarySlot)
                    if (switched) {
                        DualBootNotificationHelper.dismissNotification(context)
                        Log.d(TAG, "Switched active slot to primary slot ($primarySlot)")
                        Shell.cmd(
                            "killall -9 gsi_tool 2>/dev/null || true",
                            "printf 'y\\n' | gsi_tool disable 2>/dev/null || true",
                            "svc power reboot || reboot || setprop sys.powerctl reboot",
                        ).exec()
                    } else {
                        Log.e(TAG, "Cannot reboot to primary OS: slot switch failed (root required)")
                        val openAppIntent = Intent(context, curiousnom.dsusideloader.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(openAppIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error rebooting to primary slot: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
