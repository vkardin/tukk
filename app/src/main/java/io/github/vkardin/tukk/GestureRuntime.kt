package io.github.vkardin.tukk

import io.github.vkardin.tukk.detection.*

internal class GestureRuntime(val config: TriggerConfig) {
    private var detector = createDetector()
    private var lastActionNs: Long? = null
    private var blocked = false

    private fun createDetector(): GestureDetector = when (config.kind) {
        TriggerKind.SHAKE -> ShakeDetector(config.threshold, config.cooldownMs * 1_000_000L)
        TriggerKind.CHOP -> ChopDetector(config.threshold * 0.78f)
        TriggerKind.BACK_TAP -> BackTapDetector(config.taps, config.tapThreshold)
        TriggerKind.FACE_DOWN -> FaceDownDetector()
        TriggerKind.VOLUME -> GestureDetector { false }
    }

    fun rotation(sample: MotionSample) {
        (detector as? ChopDetector)?.rotation(sample)
    }

    fun accept(sample: MotionSample, blockedNow: Boolean): Boolean {
        if (blocked != blockedNow) {
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
}
