package com.krafttools.app.tiles

import android.content.Context
import android.content.pm.PackageManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

/**
 * Torch in the notification shade: tap toggles the LED without
 * opening the app. No permission yet -> opens the app at the torch
 * screen instead (the rationale gate lives there, not here).
 */
class TorchTileService : TileService() {

    private fun hasCamera(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    override fun onStartListening() {
        super.onStartListening()
        TorchState.observe(this)
        qsTile?.let { tile ->
            tile.state = if (TorchState.lit) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (!hasCamera() || TorchState.flashId(this) == null) {
            startActivityAndCollapse(
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    putExtra("krafttools.route", "torch")
                },
            )
            return
        }
        val now = !TorchState.lit
        TorchState.setTorch(this, now)
        qsTile?.let { tile ->
            tile.state = if (TorchState.lit) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.updateTile()
        }
    }
}

/**
 * Level in the shade: tiles can't host a live bubble, so this one
 * deep-links straight into the level screen.
 */
class LevelTileService : TileService() {
    override fun onClick() {
        super.onClick()
        startActivityAndCollapse(
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                putExtra("krafttools.route", "level")
            },
        )
    }
}
