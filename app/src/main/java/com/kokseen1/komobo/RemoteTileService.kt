package com.kokseen1.komobo

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class RemoteTileService : TileService() {
    override fun onStartListening() {
        qsTile?.state = Tile.STATE_INACTIVE
        qsTile?.updateTile()
    }

    override fun onClick() {
        ContextCompat.startForegroundService(this, Intent(this, HidRemoteService::class.java).setAction(HidRemoteService.ACTION_START))
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
    }
}
