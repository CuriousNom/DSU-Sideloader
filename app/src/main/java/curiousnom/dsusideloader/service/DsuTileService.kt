package curiousnom.dsusideloader.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import curiousnom.dsusideloader.MainActivity
import curiousnom.dsusideloader.R
import curiousnom.dsusideloader.util.DevicePropUtils

class DsuTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
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
                tile.label = getString(R.string.dsu_tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.dsu_tile_running)
                }
            }
            isGsiInstalled -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.dsu_tile_label)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.dsu_tile_installed)
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
