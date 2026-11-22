package io.github.mpridal.jakotata.core.face

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Topologie sítě obličeje MediaPipe (468 vrcholů teselace) a z ní odvozená data masky (zadání kap. 2.3).
 * Čistý Kotlin bez MediaPipe, aby šel testovat na JVM; hrany a obrysy dodá [FaceTracker].
 */
object FaceTopology {
    const val MESH_VERTICES = 468

    /** Hřbet nosu mezi očima – střed obličeje. */
    const val NOSE_BRIDGE = 168

    /** Vnitřní obrys rtů – otvor úst (dceřina skutečná pusa prosvítá). */
    val INNER_LIPS = intArrayOf(78, 191, 80, 81, 82, 13, 312, 311, 310, 415, 308, 324, 318, 402, 317, 14, 87, 178, 88, 95)

    /**
     * Body kůže pro barevné sladění: čelo, horní tváře, hřbet nosu. Bez brady a dolních tváří,
     * kde mívá táta vousy.
     */
    val SKIN_SAMPLES = intArrayOf(
        10, 151, 9, 108, 337, 67, 297, 69, 299,
        117, 118, 50, 123, 346, 347, 280, 352,
        6, 197, 195, 5,
    )

    /** Trojúhelníky z hran teselace: každá trojice navzájem spojených vrcholů. */
    fun trianglesFromEdges(edges: IntArray, vertexCount: Int = MESH_VERTICES): IntArray {
        val adj = Array(vertexCount) { HashSet<Int>() }
        var i = 0
        while (i + 1 < edges.size) {
            val a = edges[i]; val b = edges[i + 1]
            if (a in 0 until vertexCount && b in 0 until vertexCount && a != b) {
                adj[a].add(b); adj[b].add(a)
            }
            i += 2
        }
        val out = ArrayList<Int>()
        for (a in 0 until vertexCount) {
            for (b in adj[a]) {
                if (b <= a) continue
                for (c in adj[b]) {
                    if (c <= b) continue
                    if (c in adj[a]) { out.add(a); out.add(b); out.add(c) }
                }
            }
        }
        return out.toIntArray()
    }

    /**
     * Uzavřený obrys z hran (MediaPipe dává obrysy jako množinu hran): seřadí vrcholy do smyčky.
     * Pokud hrany netvoří jednu smyčku, vrátí vrcholy v pořadí výskytu.
     */
    fun loopFromEdges(edges: IntArray): IntArray {
        val next = HashMap<Int, MutableList<Int>>()
        var i = 0
        while (i + 1 < edges.size) {
            next.getOrPut(edges[i]) { mutableListOf() }.add(edges[i + 1])
            next.getOrPut(edges[i + 1]) { mutableListOf() }.add(edges[i])
            i += 2
        }
        if (next.isEmpty()) return IntArray(0)
        val start = edges[0]
        val loop = mutableListOf(start)
        var prev = -1
        var cur = start
        while (true) {
            val n = next[cur]?.firstOrNull { it != prev && (it != start || loop.size > 2) } ?: break
            if (n == start) break
            if (n in loop) break
            loop.add(n); prev = cur; cur = n
        }
        return if (loop.size == next.size) loop.toIntArray() else next.keys.toIntArray()
    }

    /**
     * Průhlednost masky v každém vrcholu podle neutrální čelní fotky táty: 0 na okraji oválu,
     * v obrysu očí a vnitřních rtů, plynulý náběh dovnitř.
     *
     * @param coords souřadnice [x0, y0, z0, …] (stačí 2D, z se ignoruje)
     * @param ovalFeather šířka přechodu na okraji oválu jako podíl šířky obličeje (zadání 8–15 %)
     * @param holeFeather šířka přechodu kolem očí a úst jako podíl šířky obličeje
     */
    fun vertexAlpha(
        coords: FloatArray,
        oval: IntArray,
        holes: List<IntArray>,
        ovalFeather: Float = 0.12f,
        holeFeather: Float = 0.035f,
        vertexCount: Int = MESH_VERTICES,
    ): FloatArray {
        val faceWidth = ovalWidth(coords, oval).coerceAtLeast(1e-6f)
        return FloatArray(vertexCount) { v ->
            val x = coords[v * 3]; val y = coords[v * 3 + 1]
            var a = smoothstep(0f, ovalFeather, distanceToPolygon(x, y, coords, oval) / faceWidth)
            for (hole in holes) {
                if (hole.isEmpty()) continue
                val d = if (v in hole || insidePolygon(x, y, coords, hole)) 0f
                else distanceToPolygon(x, y, coords, hole) / faceWidth
                a = min(a, smoothstep(0f, holeFeather, d))
            }
            a
        }
    }

    fun ovalWidth(coords: FloatArray, oval: IntArray): Float {
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        for (i in oval) { minX = min(minX, coords[i * 3]); maxX = max(maxX, coords[i * 3]) }
        return maxX - minX
    }

    fun distanceToPolygon(x: Float, y: Float, coords: FloatArray, poly: IntArray): Float {
        var best = Float.MAX_VALUE
        for (k in poly.indices) {
            val a = poly[k]; val b = poly[(k + 1) % poly.size]
            best = min(best, segmentDistance(x, y, coords[a * 3], coords[a * 3 + 1], coords[b * 3], coords[b * 3 + 1]))
        }
        return best
    }

    fun insidePolygon(x: Float, y: Float, coords: FloatArray, poly: IntArray): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = coords[poly[i] * 3]; val yi = coords[poly[i] * 3 + 1]
            val xj = coords[poly[j] * 3]; val yj = coords[poly[j] * 3 + 1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 > 0f) (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f) else 0f
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3 - 2 * t)
    }

    /**
     * Úhel odvrácení hlavy od kamery [°] z transformační matice MediaPipe (4×4). Funguje pro
     * column-major i row-major, protože používá jen prvek [10] a normu.
     */
    fun headTiltDegrees(m: FloatArray): Float {
        if (m.size < 16) return 0f
        val s = sqrt(m[8] * m[8] + m[9] * m[9] + m[10] * m[10]).coerceAtLeast(1e-6f)
        val c = (kotlin.math.abs(m[10]) / s).coerceIn(0f, 1f)
        return Math.toDegrees(kotlin.math.acos(c).toDouble()).toFloat()
    }
}
