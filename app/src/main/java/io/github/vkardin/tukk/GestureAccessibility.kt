package io.github.vkardin.tukk

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import io.github.vkardin.tukk.detection.VolumeSequenceDetector

class GestureAccessibility : AccessibilityService() {
    private var sequence = VolumeSequenceDetector()

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() {
        sequence = VolumeSequenceDetector()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        if (!GestureService.running.value || GestureService.previewActive) {
            sequence = VolumeSequenceDetector()
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false
        val config = TriggerConfig.load(this, TriggerKind.VOLUME)
        if (!config.added || !config.enabled) {
            sequence = VolumeSequenceDetector()
            return false
        }
        if (sequence.accept(event.keyCode, event.eventTime, config.volumeMode)) {
            GestureService.volumeGesture()
        }
        // Preserve regular volume controls, including their key-up events.
        return false
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        internal var instance: GestureAccessibility? = null
            private set
    }
}
