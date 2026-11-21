package io.github.mpridal.jakotata.core.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class OneEuroFilterTest {
    private val frameNs = 33_333_333L

    @Test fun firstSampleIsPassedThrough() {
        assertEquals(0.42f, OneEuroFilter().filter(0.42f, 0L), 0f)
    }

    @Test fun reducesJitterAtRest() {
        val f = OneEuroFilter(minCutoff = 1f, beta = 0.007f)
        val rnd = Random(1)
        var rawDev = 0f
        var maxDev = 0f
        for (i in 0 until 300) {
            val noisy = 0.5f + (rnd.nextFloat() - 0.5f) * 0.01f
            val out = f.filter(noisy, i * frameNs)
            if (i > 30) {
                rawDev = maxOf(rawDev, abs(noisy - 0.5f))
                maxDev = maxOf(maxDev, abs(out - 0.5f))
            }
        }
        assertTrue("filtered jitter $maxDev should be under 2/3 of raw $rawDev", maxDev < rawDev * 0.67f)
    }

    @Test fun followsFastMovementWithLittleLag() {
        val f = OneEuroFilter(minCutoff = 1f, beta = 20f)
        var out = 0f
        // Pohyb 0 → 0.3 za 10 snímků (rychlé otočení hlavy).
        for (i in 0..10) out = f.filter(i * 0.03f, i * frameNs)
        assertTrue("lag ${0.3f - out} too big", 0.3f - out < 0.06f)
    }
}
