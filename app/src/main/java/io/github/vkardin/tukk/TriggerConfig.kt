package io.github.vkardin.tukk

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

internal enum class TriggerKind(val title: Int, val hint: Int, val symbol: String) {
    SHAKE(R.string.shake, R.string.shake_help, "↔"),
    CHOP(R.string.chop, R.string.chop_help, "⌁"),
    BACK_TAP(R.string.back_tap, R.string.tap_help, "∙∙"),
    FACE_DOWN(R.string.face_down, R.string.face_help, "↷"),
    VOLUME(R.string.volume_keys, R.string.volume_help, "±"),
}

internal enum class ActionKind(val title: Int) {
    TORCH(R.string.action_torch),
    SOS(R.string.action_sos),
    SILENT(R.string.action_silent),
    VIBRATE(R.string.action_vibrate),
    SOUND(R.string.action_sound),
    PLAY_PAUSE(R.string.action_play),
    NEXT(R.string.action_next),
    PREVIOUS(R.string.action_previous),
    LAUNCH_APP(R.string.action_app),
    CAMERA(R.string.action_camera),
    RECORDER(R.string.action_recorder),
    SCREENSHOT(R.string.action_screenshot),
}

internal data class TriggerConfig(
    val kind: TriggerKind,
    val added: Boolean = kind == TriggerKind.SHAKE,
    val enabled: Boolean = kind == TriggerKind.SHAKE,
    val action: ActionKind = if (kind == TriggerKind.FACE_DOWN) ActionKind.VIBRATE else ActionKind.TORCH,
    val threshold: Float = 23f,
    val cooldownMs: Int = 1200,
    val screenMode: Int = 0,
    val pocketGuard: Boolean = true,
    val vibration: Boolean = true,
    val pauseCharging: Boolean = false,
    val taps: Int = 2,
    val tapThreshold: Float = 4f,
    val volumeMode: Int = 0,
    val packageName: String = "",
    val brightness: Int = 1,
) {
    fun save(context: Context) {
        val prefix = "trigger.${kind.name}."
        context.gesturePreferences().edit()
            .putBoolean(prefix + "added", added)
            .putBoolean(prefix + "enabled", enabled)
            .putString(prefix + "action", action.name)
            .putFloat(prefix + "threshold", threshold)
            .putInt(prefix + "cooldown", cooldownMs)
            .putInt(prefix + "screen", screenMode)
            .putBoolean(prefix + "pocket", pocketGuard)
            .putBoolean(prefix + "haptic", vibration)
            .putBoolean(prefix + "charging", pauseCharging)
            .putInt(prefix + "taps", taps)
            .putFloat(prefix + "tapThreshold", tapThreshold)
            .putInt(prefix + "volumeMode", volumeMode)
            .putString(prefix + "package", packageName)
            .putInt(prefix + "brightness", brightness)
            .apply()
    }

    companion object {
        fun load(context: Context, kind: TriggerKind): TriggerConfig {
            val prefs = context.gesturePreferences()
            val prefix = "trigger.${kind.name}."
            val defaults = TriggerConfig(kind)
            val legacy = GestureSettings.load(context)
            return defaults.copy(
                added = prefs.getBoolean(prefix + "added", defaults.added),
                enabled = prefs.getBoolean(prefix + "enabled", defaults.enabled),
                action = ActionKind.entries.firstOrNull {
                    it.name == prefs.getString(prefix + "action", defaults.action.name)
                } ?: defaults.action,
                threshold = prefs.getFloat(prefix + "threshold", legacy.threshold).coerceIn(18f, 32f),
                cooldownMs = prefs.getInt(prefix + "cooldown", legacy.cooldownMs).coerceIn(500, 5000),
                screenMode = prefs.getInt(prefix + "screen", legacy.screenMode).coerceIn(0, 2),
                pocketGuard = prefs.getBoolean(prefix + "pocket", legacy.pocketGuard),
                vibration = prefs.getBoolean(prefix + "haptic", legacy.vibration),
                pauseCharging = prefs.getBoolean(prefix + "charging", legacy.pauseCharging),
                taps = prefs.getInt(prefix + "taps", 2).coerceIn(2, 3),
                tapThreshold = prefs.getFloat(prefix + "tapThreshold", 4f).coerceIn(1.5f, 18f),
                volumeMode = prefs.getInt(prefix + "volumeMode", 0).coerceIn(0, 2),
                packageName = prefs.getString(prefix + "package", "") ?: "",
                brightness = prefs.getInt(prefix + "brightness", 1).coerceAtLeast(1),
            )
        }
    }
}

internal fun TriggerKind.available(context: Context): Boolean {
    val sensors = context.getSystemService(SensorManager::class.java)
    return when (this) {
        TriggerKind.VOLUME -> true
        TriggerKind.CHOP -> sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
            sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        else -> sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
    }
}

internal fun TriggerConfig.isReady(context: Context): Boolean {
    if (!kind.available(context)) return false
    if (kind == TriggerKind.VOLUME && GestureAccessibility.instance == null) return false
    return when (action) {
        ActionKind.TORCH, ActionKind.SOS -> context.packageManager.hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_CAMERA_FLASH,
        )
        ActionKind.SILENT, ActionKind.SOUND, ActionKind.VIBRATE ->
            context.getSystemService(android.app.NotificationManager::class.java).isNotificationPolicyAccessGranted
        ActionKind.SCREENSHOT -> android.os.Build.VERSION.SDK_INT >= 28 && GestureAccessibility.instance != null
        ActionKind.LAUNCH_APP, ActionKind.RECORDER -> packageName.isNotEmpty() &&
            context.packageManager.getLaunchIntentForPackage(packageName) != null
        else -> true
    }
}
