package io.github.vkardin.tukk.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class ShakeDetectorTest {
    @Test
    fun higherThresholdRejectsSmallShakes() {
        val detector = ShakeDetector(threshold = 32f)
        val samples = listOf(
            MotionSample(0L, 26f, 0f, 0f),
            MotionSample(80_000_000L, 0f, 0f, 9.8f),
            MotionSample(180_000_000L, -26f, 0f, 0f),
        )
        assertEquals(0, samples.count(detector::accept))
    }

    @Test
    fun longerCooldownSuppressesSecondGesture() {
        val detector = ShakeDetector(cooldownNs = 2_000_000_000L)
        val fixture = requireNotNull(javaClass.getResourceAsStream("/recordings/shake.csv"))
        val detected = fixture.bufferedReader().useLines { lines ->
            lines.drop(1).map { row ->
                val (timestamp, x, y, z) = row.split(',')
                MotionSample(timestamp.toLong(), x.toFloat(), y.toFloat(), z.toFloat())
            }.filter(detector::accept).map(MotionSample::timestampNs).toList()
        }
        assertEquals(listOf(180_000_000L), detected)
    }

    @Test
    fun recognizesTwoShakesAndSuppressesRepeatedPeaksDuringCooldown() {
        assertEquals(listOf(180_000_000L, 1_220_000_000L), gestures("shake.csv"))
    }

    @Test
    fun ignoresWalkingAndAnIsolatedImpact() {
        assertEquals(emptyList<Long>(), gestures("ordinary-motion.csv"))
    }

    private fun gestures(filename: String): List<Long> {
        val detector = ShakeDetector()
        val fixture = requireNotNull(javaClass.getResourceAsStream("/recordings/$filename"))
        return fixture.bufferedReader().useLines { lines ->
            lines.drop(1).map { row ->
                val (timestamp, x, y, z) = row.split(',')
                MotionSample(timestamp.toLong(), x.toFloat(), y.toFloat(), z.toFloat())
            }.filter(detector::accept).map(MotionSample::timestampNs).toList()
        }
    }
}
