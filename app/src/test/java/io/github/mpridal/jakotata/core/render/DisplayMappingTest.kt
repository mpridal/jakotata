package io.github.mpridal.jakotata.core.render

import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayMappingTest {
    @Test fun portraitPhoneCropsSidesOfRotated720p() {
        // Buffer 1280×720 otočený o 90° = 720×1280 (9:16) na displeji 1080×2400 (9:20) → ořez po stranách.
        val m = DisplayMapping.cover(1280, 720, 90, 1080, 2400, mirror = false)
        assertEquals(1f, m.scaleY, 1e-6f)
        assertEquals((720f / 1280f) / (1080f / 2400f), m.scaleX, 1e-5f)
    }

    @Test fun landscapeTabletCropsSidesOfWiderImage() {
        // 1280×720 (16:9) bez otočení na 1280×800 (16:10): obraz je širší → ořez po stranách.
        val m = DisplayMapping.cover(1280, 720, 0, 1280, 800, mirror = false)
        assertEquals(1f, m.scaleY, 1e-6f)
        assertEquals((1280f / 720f) / (1280f / 800f), m.scaleX, 1e-5f)
    }

    @Test fun tallerImageCropsTopAndBottom() {
        // 4:3 obraz na 16:9 výstupu → ořez nahoře a dole.
        val m = DisplayMapping.cover(640, 480, 0, 1600, 900, mirror = false)
        assertEquals(1f, m.scaleX, 1e-6f)
        assertEquals((1600f / 900f) / (640f / 480f), m.scaleY, 1e-5f)
    }

    @Test fun centerStaysCenterAndMirrorFlipsX() {
        val m = DisplayMapping.cover(1280, 720, 270, 1080, 2400, mirror = true)
        assertEquals(Pair(0.5f, 0.5f), m.imageToView(0.5f, 0.5f))
        val (lx, _) = m.imageToView(0.4f, 0.5f)
        val (rx, _) = m.imageToView(0.6f, 0.5f)
        assertEquals(1f - lx, rx, 1e-6f)
        assert(lx > 0.5f) { "mirrored left point should land right of center" }
    }

    @Test fun analysis4by3IsWiderThanPortrait16by9Preview() {
        // Na výšku: detekce 480×640 (3:4) je širší než náhled 720×1280 (9:16) → x se roztáhne.
        val (fx, fy) = DisplayMapping.analysisToPreview(480f / 640f, 720f / 1280f)
        assertEquals((480f / 640f) / (720f / 1280f), fx, 1e-5f)
        assertEquals(1f, fy, 1e-6f)
    }

    @Test fun sameAspectNeedsNoCorrection() {
        assertEquals(Pair(1f, 1f), DisplayMapping.analysisToPreview(0.5625f, 0.5625f))
    }
}
