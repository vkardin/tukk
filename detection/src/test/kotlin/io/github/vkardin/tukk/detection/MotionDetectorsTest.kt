package io.github.vkardin.tukk.detection

import org.junit.Assert.*
import org.junit.Test

class MotionDetectorsTest {
    private fun sample(ms: Long, x: Float = 0f, y: Float = 0f, z: Float = 9.8f) =
        MotionSample(ms * 1_000_000L, x, y, z)

    @Test fun faceDownRequiresAnActualFlipAndStableHold() {
        val detector = FaceDownDetector()
        assertFalse(detector.accept(sample(0, z = -9.8f)))
        assertFalse(detector.accept(sample(1000, z = -9.8f)))
        assertFalse(detector.accept(sample(1100)))
        assertFalse(detector.accept(sample(1200, z = -9.8f)))
        assertFalse(detector.accept(sample(1400, x = 7f, z = -8f)))
        assertFalse(detector.accept(sample(1500, z = -9.8f)))
        assertFalse(detector.accept(sample(2000, z = -9.8f)))
        assertTrue(detector.accept(sample(2200, z = -9.8f)))
        assertFalse(detector.accept(sample(4000, z = -9.8f)))
        assertFalse(detector.accept(sample(4100)))
        assertFalse(detector.accept(sample(4200, z = -9.8f)))
        assertTrue(detector.accept(sample(4900, z = -9.8f)))
    }

    @Test fun chopRequiresFreshGyroscopeDataAndOpposingMotions() {
        val detector = ChopDetector()
        assertFalse(detector.accept(sample(0, x = 25f)))
        assertFalse(detector.accept(sample(50)))
        detector.rotation(sample(200, x = 2f, z = 0f))
        assertFalse(detector.accept(sample(210, x = 25f)))
        assertFalse(detector.accept(sample(250)))
        detector.rotation(sample(400, x = -2f, z = 0f))
        assertTrue(detector.accept(sample(410, x = -25f)))
    }

    @Test fun chopRejectsWalkingWithoutRotation() {
        val detector = ChopDetector()
        val values = listOf(0f, 25f, 0f, -25f, 0f, 25f, 0f, -25f)
        assertEquals(0, values.mapIndexed { i, x ->
            detector.rotation(sample(i * 200L, z = 0.2f))
            detector.accept(sample(i * 200L + 10, x = x))
        }.count { it })
    }

    @Test fun doubleTapFixtureRecognizesOnlyOnePair() {
        assertEquals(listOf(220_000_000L), readFixture("back-tap.csv").filter(BackTapDetector()::accept).map { it.timestampNs })
    }

    @Test fun tripleTapRequiresThirdImpulseAndRejectsWideGaps() {
        val detector = BackTapDetector(taps = 3)
        val hits = listOf(
            sample(0), sample(20, z = 16f), sample(30),
            sample(200), sample(220, z = 16f), sample(230),
            sample(400), sample(420, z = 16f), sample(430),
            sample(1000), sample(1020, z = 16f), sample(1030),
            sample(1800), sample(1820, z = 16f), sample(1830),
        ).filter(detector::accept)
        assertEquals(listOf(420_000_000L), hits.map { it.timestampNs })
    }

    @Test fun tapRejectsBroadMovementAcrossAxes() {
        val detector = BackTapDetector()
        val samples = listOf(
            sample(0), sample(20, x = 12f, y = 8f, z = 16f),
            sample(30), sample(200), sample(220, x = -12f, y = 9f, z = 16f),
        )
        assertFalse(samples.any(detector::accept))
    }

    @Test fun volumeRequiresMatchingModeAndTiming() {
        val detector = VolumeSequenceDetector()
        assertFalse(detector.accept(24, 1000, 0))
        assertTrue(detector.accept(24, 1200, 0))
        assertFalse(detector.accept(24, 1400, 0))
        assertFalse(detector.accept(24, 2200, 0))
        assertFalse(detector.accept(25, 2300, 0))
        assertFalse(detector.accept(24, 3000, 2))
        assertTrue(detector.accept(25, 3200, 2))
    }

    private fun readFixture(name: String): List<MotionSample> {
        return requireNotNull(javaClass.getResourceAsStream("/recordings/$name")).bufferedReader().useLines { lines ->
            lines.drop(1).filter { it.isNotBlank() }.map { row ->
                val values = row.split(',')
                MotionSample(values[0].toLong(), values[1].toFloat(), values[2].toFloat(), values[3].toFloat())
            }.toList()
        }
    }
}
