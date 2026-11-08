package io.github.vkardin.tukk.core.model

/** A movement or button sequence that can start an action. */
enum class TriggerKind {
    SHAKE,
    CHOP,
    BACK_TAP,
    FACE_DOWN,
    VOLUME,
    ;

    /** True when the trigger is read from motion sensors rather than keys. */
    val usesMotion: Boolean get() = this != VOLUME
}

/** What happens when a trigger fires. */
enum class ActionKind {
    TORCH,
    SOS,
    SILENT,
    VIBRATE,
    SOUND,
    PLAY_PAUSE,
    NEXT,
    PREVIOUS,
    LAUNCH_APP,
    CAMERA,
    RECORDER,
    SCREENSHOT,
    ;

    val needsPackage: Boolean get() = this == LAUNCH_APP || this == RECORDER
    val changesRingerMode: Boolean get() = this == SILENT || this == VIBRATE || this == SOUND
    val needsAccessibility: Boolean get() = this == SCREENSHOT
}

/** When a trigger is allowed to fire relative to the screen state. */
enum class ScreenMode {
    ALWAYS,
    SCREEN_ON,
    SCREEN_OFF,
    ;

    fun allows(interactive: Boolean): Boolean = when (this) {
        ALWAYS -> true
        SCREEN_ON -> interactive
        SCREEN_OFF -> !interactive
    }
}

enum class VolumeSequence { DOUBLE_UP, DOUBLE_DOWN, UP_DOWN }

enum class ThemeMode { DARK, LIGHT, SYSTEM }

data class TriggerConfig(
    val kind: TriggerKind,
    val added: Boolean = kind == TriggerKind.SHAKE,
    val enabled: Boolean = kind == TriggerKind.SHAKE,
    val action: ActionKind = if (kind == TriggerKind.FACE_DOWN) ActionKind.VIBRATE else ActionKind.TORCH,
    /** Shake threshold in m/s²; the chop detector derives its own threshold from it. */
    val threshold: Float = DEFAULT_THRESHOLD,
    val cooldownMs: Int = 1200,
    val screenMode: ScreenMode = ScreenMode.ALWAYS,
    val pocketGuard: Boolean = true,
    val vibration: Boolean = true,
    val hapticStrength: Int = 1,
    val pauseCharging: Boolean = false,
    val pauseCall: Boolean = true,
    val taps: Int = 2,
    val tapThreshold: Float = 4f,
    val volumeSequence: VolumeSequence = VolumeSequence.DOUBLE_UP,
    val packageName: String = "",
    /** Torch strength level; 0 uses the device default. */
    val torchLevel: Int = 0,
) {
    companion object {
        const val DEFAULT_THRESHOLD = 23f
        val THRESHOLD_RANGE = 18f..32f
        val COOLDOWN_RANGE = 500..5000
        val TAP_THRESHOLD_RANGE = 1.5f..18f
    }
}

data class AppSettings(
    val theme: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val serviceEnabled: Boolean = false,
    val onboarded: Boolean = false,
    val localeInitialized: Boolean = false,
)
