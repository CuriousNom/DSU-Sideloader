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
                    DualBootNotificationHelper.dismissNotification(context)
                    SlotPartitionManager.ensureBootctlExtracted(context)
                    val primarySlot = SlotPartitionManager.getPrimarySlotIndex()
                    SlotPartitionManager.setActiveBootSlot(primarySlot)
                    Log.d(TAG, "Switched active slot to primary slot ($primarySlot)")
                    Shell.cmd("gsi_tool disable", "svc power reboot || reboot").exec()
                } catch (e: Exception) {
                    Log.e(TAG, "Error rebooting to primary slot: ${e.message}", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
