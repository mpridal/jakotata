package io.github.mpridal.jakotata.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveQualityTest {
    @Test fun dropsOneLevelAfterThreeSecondsBelow24() {
        val q = AdaptiveQuality()
        repeat(5) { q.update(20f, 0.5f) }
        assertEquals(0, q.level)
        assertTrue(q.update(20f, 0.5f))  // 3,0 s
        assertEquals(1, q.level)
        assertFalse(q.shadingTransfer)
        assertTrue(q.glassBlur)
        repeat(6) { q.update(20f, 0.5f) }
        assertEquals(2, q.level)
        assertFalse(q.glassBlur)
        repeat(20) { q.update(10f, 0.5f) }
        assertEquals(2, q.level)  // nejnižší úroveň
    }

    @Test fun shortDipDoesNothingAndRecoveryNeedsTenStableSeconds() {
        val q = AdaptiveQuality()
        q.update(20f, 2f); q.update(30f, 0.5f); q.update(20f, 2f)
        assertEquals(0, q.level)
        q.update(20f, 3f)
        assertEquals(1, q.level)
        repeat(19) { q.update(30f, 0.5f) }
        assertEquals(1, q.level)
        q.update(30f, 0.5f)
        assertEquals(0, q.level)
    }

    @Test fun ignoresZeroFpsWhenCameraIsOff() {
        val q = AdaptiveQuality()
        repeat(20) { q.update(0f, 1f) }
        assertEquals(0, q.level)
    }
}
