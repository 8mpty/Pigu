package moe.shizuku.manager.qs

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import moe.shizuku.manager.R
import moe.shizuku.manager.ShizukuSettings
import moe.shizuku.manager.security.AccessPause
import moe.shizuku.manager.utils.ShizukuStateMachine

/**
 * The Quick Settings control is deliberately a pause switch, not a stop button.
 *
 * One tap must not kill every client app: pausing keeps grants and blocks starts, so a second
 * tap can resume without re-authorizing anything. Stopping remains an explicit action on the
 * home status card, where its consequences are explained.
 */
class PiguTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()

        if (ShizukuSettings.getAccessPaused()) {
            AccessPause.resume(this)
            Toast.makeText(this, R.string.qs_tile_resumed, Toast.LENGTH_SHORT).show()
        } else {
            AccessPause.pause(this)
            Toast.makeText(this, R.string.qs_tile_paused, Toast.LENGTH_SHORT).show()
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val paused = ShizukuSettings.getAccessPaused()
        val running = ShizukuStateMachine.isRunning()

        tile.state = when {
            paused -> Tile.STATE_INACTIVE
            running -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.label = getString(if (paused) R.string.qs_tile_resume else R.string.qs_tile_pause)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                paused -> getString(R.string.qs_tile_state_paused)
                running -> getString(R.string.qs_tile_state_running)
                else -> getString(R.string.qs_tile_state_stopped)
            }
        }
        tile.icon = Icon.createWithResource(
            this,
            if (paused) R.drawable.ic_outline_play_arrow_24 else R.drawable.ic_pause_24,
        )
        tile.updateTile()
    }
}
