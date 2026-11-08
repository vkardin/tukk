package io.github.vkardin.tukk.service

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import io.github.vkardin.tukk.core.data.TukkRepository
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.detection.VolumeSequenceDetector
import javax.inject.Inject

/**
 * Optional accessibility service: watches volume key presses for the volume
 * gesture and performs the screenshot and app-launch actions. It never reads
 * window content and never consumes key events, so volume keeps working.
 */
@AndroidEntryPoint
class GestureAccessibility : AccessibilityService() {
    @Inject lateinit var connection: AccessibilityConnection
    @Inject lateinit var status: GestureStatus
    @Inject lateinit var repository: TukkRepository

    private var sequence = VolumeSequenceDetector()

    override fun onServiceConnected() {
        connection.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        sequence = VolumeSequenceDetector()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val config = repository.triggers.value.first { it.kind == TriggerKind.VOLUME }
        if (!status.running.value || status.previewActive || !config.added || !config.enabled) {
            sequence = VolumeSequenceDetector()
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            sequence.accept(event.keyCode, event.eventTime, config.volumeSequence.ordinal)
        ) {
            status.volumeSequence()
        }
        return false
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        connection.detach(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        connection.detach(this)
        super.onDestroy()
    }
}
