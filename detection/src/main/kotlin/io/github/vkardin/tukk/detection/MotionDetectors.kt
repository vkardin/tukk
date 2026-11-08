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

class ChopDetector(private val threshold: Float = 18f) : GestureDetector {
    private var gyroTime = Long.MIN_VALUE
    private var rotation = 0f
    private var firstTime: Long? = null
    private var firstAxis = 0
    private var firstDirection = 0f
    private var high = false

    fun rotation(sample: MotionSample) {
        gyroTime = sample.timestampNs
        rotation = sqrt(sample.x * sample.x + sample.y * sample.y + sample.z * sample.z)
    }

    override fun accept(sample: MotionSample): Boolean {
        val axis = if (abs(sample.x) >= abs(sample.y)) 0 else 1
        val value = if (axis == 0) sample.x else sample.y
        if (abs(value) < threshold * 0.55f) high = false
        if (high || abs(value) < threshold) return false
        high = true
        if (gyroTime == Long.MIN_VALUE || sample.timestampNs - gyroTime !in 0..150_000_000L ||
            rotation < 1.1f || rotation > 18f
        ) return false

        val first = firstTime
        val gap = if (first == null) Long.MAX_VALUE else sample.timestampNs - first
        if (first != null && gap in 110_000_000L..850_000_000L &&
            firstAxis == axis && firstDirection * value < 0
        ) {
            firstTime = null
            return true
        }
        firstTime = sample.timestampNs
        firstAxis = axis
        firstDirection = value
        return false
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
