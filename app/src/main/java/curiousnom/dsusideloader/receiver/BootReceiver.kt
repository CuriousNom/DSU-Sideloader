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

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "BOOT_COMPLETED received, checking DSU status...")
            CoroutineScope(Dispatchers.IO).launch {
                SlotPartitionManager.ensureBootctlExtracted(context)
                if (SlotPartitionManager.isRunningInDsu()) {
                    Shell.cmd("pm disable-user --user 0 com.android.dynsystem 2>/dev/null || pm disable com.android.dynsystem 2>/dev/null").exec()
                    DualBootNotificationHelper.showGuestOsNotification(context)
                } else {
                    DualBootNotificationHelper.dismissNotification(context)
                }
            }
        }
    }
}
