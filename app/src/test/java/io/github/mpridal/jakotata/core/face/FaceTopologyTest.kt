package io.github.mpridal.jakotata.core.face

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceTopologyTest {
    // Čtverec 0-1-2-3 s úhlopříčkou 0-2 → 2 trojúhelníky.
    private val squareEdges = intArrayOf(0, 1, 1, 2, 2, 3, 3, 0, 0, 2)

    @Test fun trianglesFromEdgesFindsBothFaces() {
        val t = FaceTopology.trianglesFromEdges(squareEdges, vertexCount = 4)
        assertEquals(6, t.size)
        val tris = t.toList().chunked(3).map { it.sorted() }.toSet()
        assertEquals(setOf(listOf(0, 1, 2), listOf(0, 2, 3)), tris)
    }

    @Test fun loopFromEdgesOrdersPolygon() {
        val loop = FaceTopology.loopFromEdges(intArrayOf(0, 1, 2, 3, 1, 2, 3, 0))
        assertEquals(4, loop.size)
        assertArrayEquals(intArrayOf(0, 1, 2, 3), loop)
    }

    @Test fun alphaIsZeroOnBordersAndOneInside() {
        // Ovál = čtverec 0..3 (šířka 1), díra = malý čtverec 4..7 uprostřed vpravo, vrchol 8 vlevo uvnitř.
        val pts = floatArrayOf(
            0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f,
            0.6f, 0.45f, 0f, 0.7f, 0.45f, 0f, 0.7f, 0.55f, 0f, 0.6f, 0.55f, 0f,
            0.3f, 0.5f, 0f,
        )
        val a = FaceTopology.vertexAlpha(pts, intArrayOf(0, 1, 2, 3), listOf(intArrayOf(4, 5, 6, 7)), vertexCount = 9)
        for (v in 0..7) assertEquals("vertex $v", 0f, a[v], 1e-6f)
        assertEquals(1f, a[8], 1e-6f)
    }

    @Test fun insidePolygonWorks() {
        val pts = floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f)
        assertTrue(FaceTopology.insidePolygon(0.5f, 0.5f, pts, intArrayOf(0, 1, 2, 3)))
        assertTrue(!FaceTopology.insidePolygon(1.5f, 0.5f, pts, intArrayOf(0, 1, 2, 3)))
    }

    @Test fun headTiltFromMatrix() {
        val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        assertEquals(0f, FaceTopology.headTiltDegrees(identity), 1e-3f)
        // Otočení o 45° kolem osy y (column-major).
        val c = 0.70710677f
        val yaw45 = floatArrayOf(c, 0f, -c, 0f, 0f, 1f, 0f, 0f, c, 0f, c, 0f, 0f, 0f, 0f, 1f)
        assertEquals(45f, FaceTopology.headTiltDegrees(yaw45), 0.01f)
    }
}
