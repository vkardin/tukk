package io.github.vkardin.tukk.detection

import kotlin.math.abs
import kotlin.math.sqrt

class FaceDownDetector(private val holdNs: Long = 650_000_000L) : GestureDetector {
    private var armed = false
    private var started: Long? = null

    override fun accept(sample: MotionSample): Boolean {
        if (sample.z > 3f) {
            armed = true
            started = null
            return false
        }
        if (!armed || sample.z > -7.5f || abs(sample.x) > 4.5f || abs(sample.y) > 4.5f) {
            started = null
            return false
        }
        val since = started
        if (since == null) {
            started = sample.timestampNs
            return false
        }
        if (sample.timestampNs - since < holdNs) return false
        armed = false
        started = null
        return true
    }
}

/**
 * Removes the slowly changing gravity component from accelerometer samples
 * with a time-constant based low-pass filter.
 */
class GravityFilter(private val timeConstantNs: Long = 300_000_000L) {
    private var gx = 0f
    private var gy = 0f
    private var gz = 0f
    private var lastNs: Long? = null

    /** Returns linear acceleration, or null while the filter is not yet initialized. */
    fun accept(sample: MotionSample): MotionSample? {
        val last = lastNs
        lastNs = sample.timestampNs
        val dt = if (last == null) -1L else sample.timestampNs - last
        if (last == null || dt !in 1..500_000_000L) {
            gx = sample.x
            gy = sample.y
            gz = sample.z
            return null
        }
        val alpha = dt.toFloat() / (timeConstantNs + dt)
        gx += alpha * (sample.x - gx)
        gy += alpha * (sample.y - gy)
        gz += alpha * (sample.z - gz)
        return MotionSample(sample.timestampNs, sample.x - gx, sample.y - gy, sample.z - gz)
    }
}

/**
 * Two sharp swings along the same horizontal axis within about a second,
 * confirmed by simultaneous wrist rotation from the gyroscope.
 * Each swing produces an acceleration and a braking peak; peaks close together
 * are treated as one swing so a single swing never completes the gesture.
 */
class ChopDetector(private val threshold: Float = 14f) : GestureDetector {
    private val gravity = GravityFilter()
    private var gyroTime = Long.MIN_VALUE
    private var rotation = 0f
    private var high = false
    private var swingStart: Long? = null
    private var swingAxis = 0
    private var lastPeak: Long? = null

    fun rotation(sample: MotionSample) {
        gyroTime = sample.timestampNs
        rotation = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
    }

    override fun accept(sample: MotionSample): Boolean {
        val linear = gravity.accept(sample) ?: return false
        val axis = if (abs(linear.x) >= abs(linear.y)) 0 else 1
        val value = abs(if (axis == 0) linear.x else linear.y)
        if (value < threshold * RESET_RATIO) high = false
        if (high || value < threshold) return false
        high = true
        val now = sample.timestampNs
        if (gyroTime == Long.MIN_VALUE || now - gyroTime !in 0..GYRO_FRESH_NS ||
            rotation < MIN_ROTATION || rotation > MAX_ROTATION
        ) return false

        val previousPeak = lastPeak
        lastPeak = now
        // Braking peak of the same swing.
        if (previousPeak != null && now - previousPeak < SWING_MERGE_NS) return false

        val first = swingStart
        if (first != null && now - first in MIN_GAP_NS..MAX_GAP_NS && axis == swingAxis) {
            swingStart = null
            lastPeak = null
            return true
        }
        swingStart = now
        swingAxis = axis
        return false
    }

    private companion object {
        const val RESET_RATIO = 0.45f
        const val GYRO_FRESH_NS = 150_000_000L
        const val MIN_ROTATION = 1.1f
        const val MAX_ROTATION = 25f
        const val SWING_MERGE_NS = 200_000_000L
        const val MIN_GAP_NS = 220_000_000L
        const val MAX_GAP_NS = 1_000_000_000L
    }
}

/** Extracts brief Z-axis impulses while rejecting broad device motion. */
class TapImpulseDetector(private val threshold: Float = 4f) {
    private var previous: MotionSample? = null
    private var lastImpulse: Long? = null

    fun accept(sample: MotionSample): Float? {
        val before = previous
        previous = sample
        if (before == null || sample.timestampNs - before.timestampNs !in 1..80_000_000L) return null
        val dz = abs(sample.z - before.z)
        val lateral = abs(sample.x - before.x) + abs(sample.y - before.y)
        if (dz < threshold || lateral > dz * 0.85f) return null
        if (lastImpulse?.let { sample.timestampNs - it < 110_000_000L } == true) return null
        lastImpulse = sample.timestampNs
        return dz
    }
}

class BackTapDetector(
    private val taps: Int = 2,
    threshold: Float = 4f,
) : GestureDetector {
    private val impulses = TapImpulseDetector(threshold)
    private var count = 0
    private var lastTap = 0L

    override fun accept(sample: MotionSample): Boolean {
        if (impulses.accept(sample) == null) return false
        if (sample.timestampNs - lastTap > 430_000_000L) count = 0
        lastTap = sample.timestampNs
        count++
        if (count < taps) return false
        count = 0
        return true
    }
}

class VolumeSequenceDetector {
    private var previousKey: Int? = null
    private var previousTime = 0L

    fun accept(key: Int, timestampMs: Long, mode: Int): Boolean {
        val expected = when (mode) {
            0 -> key == 24 && previousKey == 24
            1 -> key == 25 && previousKey == 25
            else -> previousKey != null && previousKey != key
        }
        val match = expected && timestampMs - previousTime in 60..450
        previousKey = if (match) null else key
        previousTime = timestampMs
        return match
    }
}
