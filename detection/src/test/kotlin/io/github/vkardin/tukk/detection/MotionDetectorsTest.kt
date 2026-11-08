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

    /** 100 Hz stream at rest with gravity on [gravityAxis], plus swings starting at [swingsMs]. */
    private fun chopStream(
        detector: ChopDetector,
        swingsMs: List<Long>,
        gravityAxis: Char = 'z',
        rotationRate: Float = 4f,
        durationMs: Long = 2000,
        swingAxis: Char = 'x',
    ): List<Long> {
        val hits = mutableListOf<Long>()
        for (ms in 0..durationMs step 10) {
            val swing = swingsMs.firstOrNull { ms - it in 0..119 }
            // Acceleration peak followed by a braking peak about 80 ms later.
            val linear = when (swing?.let { ms - it }) {
                null -> 0f
                in 0..29 -> 22f
                in 80..109 -> -20f
                else -> 0f
            }
            val gravity = 9.8f
            val x = (if (gravityAxis == 'x') gravity else 0f) + (if (swingAxis == 'x') linear else 0f)
            val y = (if (gravityAxis == 'y') gravity else 0f) + (if (swingAxis == 'y') linear else 0f)
            val z = if (gravityAxis == 'z') gravity else 0f
            detector.rotation(sample(ms, z = if (swing != null) rotationRate else 0.1f).copy(x = 0f))
            if (detector.accept(sample(ms, x = x, y = y, z = z))) hits += ms
        }
        return hits
    }

    @Test fun chopRecognizesTwoSwings() {
        assertEquals(1, chopStream(ChopDetector(), listOf(500, 950)).size)
    }

    @Test fun chopIgnoresSingleSwingWithBrakingPeak() {
        assertEquals(emptyList<Long>(), chopStream(ChopDetector(), listOf(500)))
    }

    @Test fun chopWorksWhenPhoneIsUpright() {
        assertEquals(1, chopStream(ChopDetector(), listOf(500, 950), gravityAxis = 'y', swingAxis = 'x').size)
        assertEquals(1, chopStream(ChopDetector(), listOf(500, 950), gravityAxis = 'y', swingAxis = 'y').size)
    }

    @Test fun chopRejectsSwingsTooFarApart() {
        assertEquals(emptyList<Long>(), chopStream(ChopDetector(), listOf(300, 1600), durationMs = 2500))
    }

    @Test fun chopRequiresWristRotation() {
        assertEquals(emptyList<Long>(), chopStream(ChopDetector(), listOf(500, 950), rotationRate = 0.2f))
    }

    @Test fun chopRejectsWalkingWithoutRotation() {
        val detector = ChopDetector()
        val values = listOf(0f, 25f, 0f, -25f, 0f, 25f, 0f, -25f)
        assertEquals(0, values.mapIndexed { i, x ->
            detector.rotation(sample(i * 200L, z = 0.2f))
            detector.accept(sample(i * 200L + 10, x = x))
        }.count { it })
    }

    @Test fun gravityFilterRemovesStaticGravity() {
        val filter = GravityFilter()
        var last: MotionSample? = null
        for (ms in 0..3000L step 10) last = filter.accept(sample(ms, y = 9.8f, z = 0f))
        assertEquals(0f, requireNotNull(last).y, 0.01f)
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

    @Test fun faceDownIgnoresBriefFlip() {
        val detector = FaceDownDetector()
        assertFalse(detector.accept(sample(0)))
        assertFalse(detector.accept(sample(100, z = -9.8f)))
        assertFalse(detector.accept(sample(400, z = -9.8f)))
        assertFalse(detector.accept(sample(500)))
    }

    @Test fun volumeRejectsSlowPresses() {
        val detector = VolumeSequenceDetector()
        assertFalse(detector.accept(25, 1000, 1))
        assertFalse(detector.accept(25, 1600, 1))
        assertTrue(detector.accept(25, 1800, 1))
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
