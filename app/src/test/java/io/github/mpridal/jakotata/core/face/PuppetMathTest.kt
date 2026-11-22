package io.github.mpridal.jakotata.core.face

import org.junit.Assert.assertEquals
import org.junit.Test

class PuppetMathTest {
    // 4 body: ovál = 0..3 (čtverec), bod 4 = „pusa“ uprostřed.
    private val oval = intArrayOf(0, 1, 2, 3)

    private fun pts(vararg xy: Float) = FloatArray(xy.size / 2 * 3).also { for (i in 0 until xy.size / 2) { it[i * 3] = xy[i * 2]; it[i * 3 + 1] = xy[i * 2 + 1] } }

    @Test fun normalizationRemovesPositionAndScale() {
        val a = pts(0.2f, 0.2f, 0.4f, 0.2f, 0.4f, 0.4f, 0.2f, 0.4f, 0.3f, 0.35f)
        val b = pts(0.5f, 0.5f, 0.9f, 0.5f, 0.9f, 0.9f, 0.5f, 0.9f, 0.7f, 0.8f) // posunuté a 2× větší
        val na = PuppetMath.normalize(a, 1f, oval, 5)
        val nb = PuppetMath.normalize(b, 1f, oval, 5)
        for (i in na.indices) assertEquals(na[i], nb[i], 1e-5f)
    }

    @Test fun neutralFaceLeavesDadUnchangedAndMouthMoves() {
        val dad = pts(0.3f, 0.3f, 0.7f, 0.3f, 0.7f, 0.7f, 0.3f, 0.7f, 0.5f, 0.6f)
        val neutral = PuppetMath.normalize(pts(0.2f, 0.2f, 0.4f, 0.2f, 0.4f, 0.4f, 0.2f, 0.4f, 0.3f, 0.35f), 1f, oval, 5)
        val same = PuppetMath.deform(dad, oval, neutral, neutral, 5)
        assertEquals(0.5f, same[8], 1e-6f); assertEquals(0.6f, same[9], 1e-6f)
        // Dcera otevře pusu: bod 4 o 0,02 níž při šířce obličeje 0,2 = 10 % šířky.
        val open = PuppetMath.normalize(pts(0.2f, 0.2f, 0.4f, 0.2f, 0.4f, 0.4f, 0.2f, 0.4f, 0.3f, 0.37f), 1f, oval, 5)
        val moved = PuppetMath.deform(dad, oval, open, neutral, 5)
        assertEquals(0.6f + 0.1f * 0.4f * 0.5f / 0.5f * 1f, moved[9], 0.005f) // ≈ 0,64 (táta má šířku 0,4)
    }
}
