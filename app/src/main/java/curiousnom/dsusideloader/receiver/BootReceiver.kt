package curiousnom.dsusideloader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import curiousnom.dsusideloader.core.SlotPartitionManager
import curiousnom.dsusideloader.util.DualBootNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "BOOT_COMPLETED received, checking DSU status...")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SlotPartitionManager.ensureBootctlExtracted(context)
                    if (SlotPartitionManager.isRunningInDsu()) {
                        DualBootNotificationHelper.suppressOfficialDsuNotification()
                        DualBootNotificationHelper.showGuestOsNotification(context)
                    } else {
                        DualBootNotificationHelper.dismissNotification(context)
                        if (SlotPartitionManager.isAbDevice() && SlotPartitionManager.hasSecondaryPhysicalSlot()) {
                            DualBootNotificationHelper.suppressOfficialDsuNotification()
                        }
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
