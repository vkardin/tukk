package io.github.vkardin.tukk

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class GestureTile : TileService() {
    override fun onStartListening() {
        updateTile()
    }

    // The Intent overload is used only on OS versions predating the PendingIntent overload.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        if (GestureService.running.value) {
            gesturePreferences().edit().putBoolean(GestureService.ENABLED, false).apply()
            stopService(Intent(this, GestureService::class.java))
            qsTile?.state = Tile.STATE_INACTIVE
            qsTile?.updateTile()
        } else {
            if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                gesturePreferences().edit().putBoolean(GestureService.ENABLED, true).apply()
                if (!GestureService.start(this)) {
                    gesturePreferences().edit().putBoolean(GestureService.ENABLED, false).apply()
                }
                updateTile()
                return
            }
            val intent = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ))
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile() {
        qsTile?.state = if (GestureService.running.value) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile?.label = getString(R.string.app_name)
        qsTile?.updateTile()
    }
}
