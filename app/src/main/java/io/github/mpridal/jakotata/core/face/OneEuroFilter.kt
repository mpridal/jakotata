package io.github.mpridal.jakotata.core.face

import kotlin.math.PI
import kotlin.math.abs

/**
 * Filtr One Euro (Casiez a kol., 2012): v klidu silně vyhlazuje (bez cukání), při rychlém pohybu
 * propouští změnu (bez zpoždění). Zadání kap. 2.1.
 *
 * @param minCutoff mezní frekvence v klidu [Hz] – menší = hladší v klidu
 * @param beta jak rychle roste mezní frekvence s rychlostí – větší = menší zpoždění v pohybu
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f,
) {
    private var prevX = 0f
    private var prevDx = 0f
    private var prevTimeNs = 0L
    private var initialized = false

    fun reset() { initialized = false }

    fun filter(x: Float, timeNs: Long): Float {
        if (!initialized) {
            initialized = true
            prevX = x; prevDx = 0f; prevTimeNs = timeNs
            return x
        }
        val dt = ((timeNs - prevTimeNs) / 1e9f).coerceAtLeast(1e-4f)
        prevTimeNs = timeNs
        val dx = (x - prevX) / dt
        val edx = lerp(prevDx, dx, alpha(dCutoff, dt))
        prevDx = edx
        val cutoff = minCutoff + beta * abs(edx)
        val ex = lerp(prevX, x, alpha(cutoff, dt))
        prevX = ex
        return ex
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}

/** Filtry One Euro pro všechny souřadnice bodů obličeje (x, y, z pro každý bod). */
class LandmarkSmoother(
    private val minCutoff: Float = 1.2f,
    private val beta: Float = 20f,
) {
    private var filters: Array<OneEuroFilter> = emptyArray()

    fun reset() = filters.forEach { it.reset() }

    /** Vyhladí pole [x0, y0, z0, x1, y1, z1, …] na místě. */
    fun smooth(coords: FloatArray, timeNs: Long) {
        if (filters.size != coords.size) {
            filters = Array(coords.size) { OneEuroFilter(minCutoff, beta) }
        }
        for (i in coords.indices) coords[i] = filters[i].filter(coords[i], timeNs)
    }
}
