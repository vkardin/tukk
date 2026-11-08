package io.github.vkardin.tukk.core.ui

import androidx.annotation.StringRes
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.TriggerKind

@get:StringRes
val TriggerKind.title: Int get() = when (this) {
    TriggerKind.SHAKE -> R.string.shake
    TriggerKind.CHOP -> R.string.chop
    TriggerKind.BACK_TAP -> R.string.back_tap
    TriggerKind.FACE_DOWN -> R.string.face_down
    TriggerKind.VOLUME -> R.string.volume_keys
}

@get:StringRes
val TriggerKind.hint: Int get() = when (this) {
    TriggerKind.SHAKE -> R.string.shake_help
    TriggerKind.CHOP -> R.string.chop_help
    TriggerKind.BACK_TAP -> R.string.tap_help
    TriggerKind.FACE_DOWN -> R.string.face_help
    TriggerKind.VOLUME -> R.string.volume_help
}

val TriggerKind.symbol: String get() = when (this) {
    TriggerKind.SHAKE -> "↔"
    TriggerKind.CHOP -> "⌁"
    TriggerKind.BACK_TAP -> "∙∙"
    TriggerKind.FACE_DOWN -> "↷"
    TriggerKind.VOLUME -> "±"
}

@get:StringRes
val ActionKind.title: Int get() = when (this) {
    ActionKind.TORCH -> R.string.action_torch
    ActionKind.SOS -> R.string.action_sos
    ActionKind.SILENT -> R.string.action_silent
    ActionKind.VIBRATE -> R.string.action_vibrate
    ActionKind.SOUND -> R.string.action_sound
    ActionKind.PLAY_PAUSE -> R.string.action_play
    ActionKind.NEXT -> R.string.action_next
    ActionKind.PREVIOUS -> R.string.action_previous
    ActionKind.LAUNCH_APP -> R.string.action_app
    ActionKind.CAMERA -> R.string.action_camera
    ActionKind.RECORDER -> R.string.action_recorder
    ActionKind.SCREENSHOT -> R.string.action_screenshot
}
