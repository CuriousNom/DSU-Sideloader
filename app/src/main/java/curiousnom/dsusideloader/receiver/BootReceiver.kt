package curiousnom.dsusideloader.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import curiousnom.dsusideloader.core.SlotPartitionManager
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
                    SlotPartitionManager.autoArmPrimarySlotIfOneShot()
                }
            }
        }
    }
}
