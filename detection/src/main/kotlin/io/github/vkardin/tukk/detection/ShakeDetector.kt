package io.github.vkardin.tukk.detection

import kotlin.math.sqrt

data class MotionSample(val timestampNs: Long, val x: Float, val y: Float, val z: Float)

fun interface GestureDetector {
    fun accept(sample: MotionSample): Boolean
}

class ShakeDetector(
    private val threshold: Float = 23f,
    private val cooldownNs: Long = 900_000_000L,
) : GestureDetector {
    private var aboveThreshold = false
    private var firstPeakNs: Long? = null
    private var lastPeakNs: Long? = null
    private var lastGestureNs: Long? = null

    override fun accept(sample: MotionSample): Boolean {
        val magnitude = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
        if (magnitude < RESET_THRESHOLD) {
            aboveThreshold = false
        }
        if (aboveThreshold || magnitude < threshold) return false
        aboveThreshold = true

        val now = sample.timestampNs
        if (lastGestureNs?.let { now - it < cooldownNs } == true) return false
        if (lastPeakNs?.let { now - it < MIN_PEAK_SPACING_NS } == true) return false
        lastPeakNs = now

        val first = firstPeakNs
        if (first == null || now - first > WINDOW_NS) {
            firstPeakNs = now
            return false
        }

        firstPeakNs = null
        lastGestureNs = now
        return true
    }

    private companion object {
        const val RESET_THRESHOLD = 15f
        const val MIN_PEAK_SPACING_NS = 90_000_000L
        const val WINDOW_NS = 550_000_000L
    }
}
