package io.github.mpridal.jakotata.core.face

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SkinColorTest {
    @Test fun labOfWhiteAndBlack() {
        val w = SkinColor.rgbToLab(255, 255, 255)
        assertEquals(100f, w[0], 0.1f); assertEquals(0f, w[1], 0.5f); assertEquals(0f, w[2], 0.5f)
        assertEquals(0f, SkinColor.rgbToLab(0, 0, 0)[0], 0.1f)
    }

    @Test fun labOfSkinTone() {
        // Referenční hodnota pro sRGB (224, 172, 105), D65 ≈ Lab(73.79, 11.28, 41.53).
        val lab = SkinColor.rgbToLab(224, 172, 105)
        assertEquals(73.79f, lab[0], 0.2f); assertEquals(11.28f, lab[1], 0.3f); assertEquals(41.53f, lab[2], 0.3f)
    }

    @Test fun statsOfUniformImage() {
        val w = 20; val h = 20
        val px = IntArray(w * h) { 0xFFE0AC69.toInt() }
        val coords = FloatArray(3 * 3).also { it[0] = 0.5f; it[1] = 0.5f; it[3] = 0.3f; it[4] = 0.3f; it[6] = 0.7f; it[7] = 0.7f }
        val s = SkinColor.stats(px, w, h, coords, intArrayOf(0, 1, 2))
        assertNotNull(s)
        assertEquals(73.79f, s!!.meanL, 0.2f)
        assertEquals(0.5f, s.stdL, 1e-6f) // minimum, aby se v shaderu nedělilo nulou
    }
}
