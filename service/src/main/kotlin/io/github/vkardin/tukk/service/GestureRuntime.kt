package io.github.vkardin.tukk.service

import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.detection.BackTapDetector
import io.github.vkardin.tukk.detection.ChopDetector
import io.github.vkardin.tukk.detection.FaceDownDetector
import io.github.vkardin.tukk.detection.GestureDetector
import io.github.vkardin.tukk.detection.MotionSample
import io.github.vkardin.tukk.detection.ShakeDetector

/** One configured trigger: its detector plus cooldown and blocking state. */
class GestureRuntime(val config: TriggerConfig) {
    private var detector = createDetector()
    private var lastActionNs: Long? = null
    private var blocked = false

    private fun createDetector(): GestureDetector = when (config.kind) {
        TriggerKind.SHAKE -> ShakeDetector(config.threshold, config.cooldownMs * 1_000_000L)
        TriggerKind.CHOP -> ChopDetector(config.threshold * CHOP_RATIO)
        TriggerKind.BACK_TAP -> BackTapDetector(config.taps, config.tapThreshold)
        TriggerKind.FACE_DOWN -> FaceDownDetector()
        TriggerKind.VOLUME -> GestureDetector { false }
    }

    fun rotation(sample: MotionSample) {
        (detector as? ChopDetector)?.rotation(sample)
    }

    /** Feeds an accelerometer sample; true when the gesture is complete and out of cooldown. */
    fun accept(sample: MotionSample, blockedNow: Boolean): Boolean {
        if (blocked != blockedNow) {
            // Start fresh so a half-finished gesture does not complete after a block ends.
            detector = createDetector()
            blocked = blockedNow
        }
        if (blocked || !detector.accept(sample)) return false
        return allow(sample.timestampNs)
    }

    fun allow(timeNs: Long): Boolean {
        if (lastActionNs?.let { timeNs - it < config.cooldownMs * 1_000_000L } == true) return false
        lastActionNs = timeNs
        return true
    }

    private companion object {
        /** The chop detector works on linear acceleration, without gravity. */
        const val CHOP_RATIO = 0.6f
    }
}
