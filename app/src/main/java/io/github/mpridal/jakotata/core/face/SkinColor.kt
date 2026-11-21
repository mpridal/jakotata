package io.github.mpridal.jakotata.core.face

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Barva kůže v prostoru CIE Lab (D65): průměr a směrodatná odchylka L, a, b a průměrný jas.
 * Přenáší se v shaderu masky z tátovy fotky na obličej dcery (zadání kap. 2.3 bod 4).
 */
data class SkinStats(
    val meanL: Float, val meanA: Float, val meanB: Float,
    val stdL: Float, val stdA: Float, val stdB: Float,
    /** Průměrný jas (luma 0–1) pro přenos stínování. */
    val luma: Float,
) {
    fun toArray() = floatArrayOf(meanL, meanA, meanB, stdL, stdA, stdB, luma)

    /** Klouzavý průměr pro vyhlazení v čase. */
    fun blend(next: SkinStats, t: Float) = SkinStats(
        meanL + (next.meanL - meanL) * t, meanA + (next.meanA - meanA) * t, meanB + (next.meanB - meanB) * t,
        stdL + (next.stdL - stdL) * t, stdA + (next.stdA - stdA) * t, stdB + (next.stdB - stdB) * t,
        luma + (next.luma - luma) * t,
    )

    companion object {
        fun fromArray(a: FloatArray) = SkinStats(a[0], a[1], a[2], a[3], a[4], a[5], a[6])
    }
}

object SkinColor {

    /** sRGB 0–255 → Lab. */
    fun rgbToLab(r: Int, g: Int, b: Int): FloatArray {
        fun lin(c: Int): Float {
            val v = c / 255f
            return if (v <= 0.04045f) v / 12.92f else ((v + 0.055f) / 1.055f).pow(2.4f)
        }
        val rl = lin(r); val gl = lin(g); val bl = lin(b)
        val x = (0.4124f * rl + 0.3576f * gl + 0.1805f * bl) / 0.95047f
        val y = (0.2126f * rl + 0.7152f * gl + 0.0722f * bl)
        val z = (0.0193f * rl + 0.1192f * gl + 0.9505f * bl) / 1.08883f
        fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
        val fx = f(x); val fy = f(y); val fz = f(z)
        return floatArrayOf(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    /**
     * Statistika kůže z pixelů ARGB kolem zadaných bodů (okolí 5×5).
     *
     * @param pixels pixely obrázku ARGB po řádcích
     * @param coords body obličeje [x0, y0, z0, …] normalizované k obrázku
     */
    fun stats(pixels: IntArray, width: Int, height: Int, coords: FloatArray, sampleIdx: IntArray): SkinStats? {
        var n = 0
        var sL = 0.0; var sA = 0.0; var sB = 0.0
        var qL = 0.0; var qA = 0.0; var qB = 0.0
        var sLuma = 0.0
        for (idx in sampleIdx) {
            if (idx * 3 + 1 >= coords.size) continue
            val cx = (coords[idx * 3] * width).toInt()
            val cy = (coords[idx * 3 + 1] * height).toInt()
            for (dy in -2..2) for (dx in -2..2) {
                val x = cx + dx; val y = cy + dy
                if (x !in 0 until width || y !in 0 until height) continue
                val p = pixels[y * width + x]
                val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                val lab = rgbToLab(r, g, b)
                sL += lab[0]; sA += lab[1]; sB += lab[2]
                qL += lab[0] * lab[0]; qA += lab[1] * lab[1]; qB += lab[2] * lab[2]
                sLuma += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                n++
            }
        }
        if (n < 10) return null
        fun std(s: Double, q: Double) = sqrt((q / n - (s / n) * (s / n)).coerceAtLeast(0.0)).toFloat().coerceAtLeast(0.5f)
        return SkinStats(
            (sL / n).toFloat(), (sA / n).toFloat(), (sB / n).toFloat(),
            std(sL, qL), std(sA, qA), std(sB, qB),
            (sLuma / n).toFloat(),
        )
    }
}
