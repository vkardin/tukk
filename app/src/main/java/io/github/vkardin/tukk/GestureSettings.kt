package io.github.vkardin.tukk

import android.content.Context

internal data class GestureSettings(
    val threshold: Float = 23f,
    val cooldownMs: Int = 900,
    val screenMode: Int = 0,
    val pocketGuard: Boolean = true,
    val vibration: Boolean = true,
    val pauseCharging: Boolean = false,
    val theme: Int = 0,
    val dynamicColor: Boolean = false,
) {
    fun save(context: Context) {
        context.gesturePreferences().edit()
            .putFloat("threshold", threshold)
            .putInt("cooldownMs", cooldownMs)
            .putInt("screenMode", screenMode)
            .putBoolean("pocketGuard", pocketGuard)
            .putBoolean("vibration", vibration)
            .putBoolean("pauseCharging", pauseCharging)
            .putInt("theme", theme)
            .putBoolean("dynamicColor", dynamicColor)
            .apply()
    }

    companion object {
        fun load(context: Context): GestureSettings {
            val prefs = context.gesturePreferences()
            return GestureSettings(
                threshold = prefs.getFloat("threshold", 23f).coerceIn(18f, 32f),
                cooldownMs = prefs.getInt("cooldownMs", 900).coerceIn(500, 3000),
                screenMode = prefs.getInt("screenMode", 0).coerceIn(0, 2),
                pocketGuard = prefs.getBoolean("pocketGuard", true),
                vibration = prefs.getBoolean("vibration", true),
                pauseCharging = prefs.getBoolean("pauseCharging", false),
                theme = prefs.getInt("theme", 0).coerceIn(0, 2),
                dynamicColor = prefs.getBoolean("dynamicColor", false),
            )
        }
    }
}

