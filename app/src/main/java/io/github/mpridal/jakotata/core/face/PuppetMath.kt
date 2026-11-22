package io.github.mpridal.jakotata.core.face

/**
 * Režim B – Loutka (zadání kap. 2.4): rozdíl aktuálních bodů dcery proti jejímu neutrálnímu výrazu
 * (normalizovaný na velikost obličeje, bez posunu v obraze) se přičte k bodům tátovy čelní fotky.
 */
object PuppetMath {

    /**
     * Body převedené do souřadnic obličeje: střed oválu = 0, šířka oválu = 1, izotropně
     * (x vynásobené poměrem stran snímku). Výstup [x0, y0, x1, y1, …] pro [count] bodů.
     */
    fun normalize(coords: FloatArray, aspect: Float, oval: IntArray, count: Int = FaceTopology.MESH_VERTICES, out: FloatArray = FloatArray(count * 2)): FloatArray {
        var cx = 0f; var cy = 0f
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        for (i in oval) {
            val x = coords[i * 3] * aspect
            cx += x; cy += coords[i * 3 + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
        }
        cx /= oval.size; cy /= oval.size
        val w = (maxX - minX).coerceAtLeast(1e-6f)
        for (i in 0 until count) {
            out[i * 2] = (coords[i * 3] * aspect - cx) / w
            out[i * 2 + 1] = (coords[i * 3 + 1] - cy) / w
        }
        return out
    }

    /**
     * Zdeformované body táty v jeho textuře (čtvercový výřez, poměr 1): dad + (kid − neutral) · šířka táty.
     * Pohyb mimo ±[maxDelta] šířky obličeje se ořízne (chyba detekce nesmí roztrhat obličej).
     */
    fun deform(
        dad: FloatArray, dadOval: IntArray, kidNorm: FloatArray, neutralNorm: FloatArray,
        count: Int = FaceTopology.MESH_VERTICES, maxDelta: Float = 0.25f, out: FloatArray = FloatArray(count * 2),
    ): FloatArray {
        val dadWidth = FaceTopology.ovalWidth(dad, dadOval)
        for (i in 0 until count) {
            val dx = (kidNorm[i * 2] - neutralNorm[i * 2]).coerceIn(-maxDelta, maxDelta)
            val dy = (kidNorm[i * 2 + 1] - neutralNorm[i * 2 + 1]).coerceIn(-maxDelta, maxDelta)
            out[i * 2] = dad[i * 3] + dx * dadWidth
            out[i * 2 + 1] = dad[i * 3 + 1] + dy * dadWidth
        }
        return out
    }
}
