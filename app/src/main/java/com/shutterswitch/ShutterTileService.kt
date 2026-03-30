package com.shutterswitch

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class ShutterTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = WakeLockService.isServiceRunning.value
        val intent = Intent(this, WakeLockService::class.java)

        if (isRunning) {
            stopService(intent)
        } else {
            if (!Settings.canDrawOverlays(this)) {
                // If permission is missing, launch main activity so user can grant it
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivityAndCollapse(mainIntent)
                return
            }
            ContextCompat.startForegroundService(this, intent)
        }

        // The state will also be updated when WakeLockService starts/stops and 
        // calls requestListeningState, but we optimistically update here as well.
        updateTileState(!isRunning)
    }

    private fun updateTileState(forcedState: Boolean? = null) {
        val tile = qsTile ?: return
        val isRunning = forcedState ?: WakeLockService.isServiceRunning.value
        
        tile.state = if (isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        // Optionally update the label or subtitle based on state
        tile.label = "No Sleep"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            tile.subtitle = if (isRunning) "On" else "Off"
        }
        
        tile.updateTile()
    }
}
