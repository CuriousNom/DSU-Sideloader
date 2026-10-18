package curiousnom.dsusideloader.service

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.topjohnwu.superuser.Shell
import curiousnom.dsusideloader.MainActivity
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.core.SlotPartitionManager
import curiousnom.dsusideloader.util.DevicePropUtils
import curiousnom.dsusideloader.util.DualBootNotificationHelper
import curiousnom.dsusideloader.util.OperationModeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DsuTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun {
                handleTileClick()
            }
        } else {
            handleTileClick()
        }
    }

    private fun handleTileClick() {
        when {
            isDsuInUse() -> showRebootToPrimaryPrompt()
            isDsuInstalled() -> showRebootToSecondaryPrompt()
            else -> openMainActivity()
        }
    }

    private fun showRebootToPrimaryPrompt() {
        if (!OperationModeUtils.isSuAvailable()) {
            openMainActivity()
            return
        }
        try {
            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.reboot_to_primary_os)
                .setMessage(R.string.reboot_to_primary_dialog_desc)
                .setPositiveButton(R.string.reboot) { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        SlotPartitionManager.ensureBootctlExtracted(applicationContext)
                        val primarySlot = SlotPartitionManager.getPrimarySlotIndex()
                        SlotPartitionManager.setActiveBootSlot(primarySlot)
                        DualBootNotificationHelper.dismissNotification(applicationContext)
                        Shell.cmd("gsi_tool disable", "svc power reboot || reboot").exec()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .create()
            showDialog(dialog)
        } catch (_: Exception) {
            openMainActivity()
        }
    }

    private fun showRebootToSecondaryPrompt() {
        if (!OperationModeUtils.isSuAvailable()) {
            openMainActivity()
            return
        }
        try {
            val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.boot_to_secondary_os)
                .setMessage(R.string.reboot_to_secondary_dialog_desc)
                .setPositiveButton(R.string.reboot) { _, _ ->
                    CoroutineScope(Dispatchers.IO).launch {
                        val hasPhysicalSlot = SlotPartitionManager.hasSecondaryPhysicalSlot()
                        if (hasPhysicalSlot && SlotPartitionManager.isAbDevice()) {
                            val targetSlot = SlotPartitionManager.getSecondarySlotIndex()
                            SlotPartitionManager.setActiveBootSlot(targetSlot)
                        }
                        Shell.cmd("gsi_tool enable", "svc power reboot || reboot").exec()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .create()
            showDialog(dialog)
        } catch (_: Exception) {
            openMainActivity()
        }
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return

        val isGsiInUse = isDsuInUse()
        val isGsiInstalled = isDsuInstalled()

        when {
            isGsiInUse -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.active_secondary_os)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tap_to_reboot_primary)
                }
            }
            isGsiInstalled -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.secondary_os_ready)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tap_to_boot_secondary)
                }
            }
            else -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.dsu_tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.dsu_tile_inactive)
                }
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_dsu)
        tile.updateTile()
    }

    private fun isDsuInUse(): Boolean {
        if (DevicePropUtils.isDsuRunning()) {
            return true
        }
        if (PrivilegedProvider.isConnected()) {
            return try {
                PrivilegedProvider.getService().isInUse
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    private fun isDsuInstalled(): Boolean {
        if (PrivilegedProvider.isConnected()) {
            return try {
                PrivilegedProvider.getService().isInstalled
            } catch (e: Exception) {
                false
            }
        }
        return false
    }
}
