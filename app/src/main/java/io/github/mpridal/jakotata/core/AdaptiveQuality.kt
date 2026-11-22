package io.github.mpridal.jakotata.core

/**
 * Adaptivní kvalita (zadání kap. 5): když fps klesne pod 24 na 3 s, sníží se úroveň –
 * 1 = bez přenosu stínování, 2 = sklo bez rozmazání (poloprůhledný tón). Zpět o úroveň výš až po
 * 10 s stabilně nad 28 fps (hystereze, aby se kvalita nepřepínala sem a tam).
 */
class AdaptiveQuality(
    private val minFps: Float = 24f,
    private val recoverFps: Float = 28f,
    private val downAfterSec: Float = 3f,
    private val upAfterSec: Float = 10f,
    val maxLevel: Int = 2,
) {
    var level = 0
        private set
    private var lowSec = 0f
    private var highSec = 0f

    /** Jeden vzorek fps za [dtSec] sekund; vrací true, když se úroveň změnila. */
    fun update(fps: Float, dtSec: Float): Boolean {
        if (fps <= 0f) return false  // kamera neběží
        if (fps < minFps) { lowSec += dtSec; highSec = 0f } else if (fps >= recoverFps) { highSec += dtSec; lowSec = 0f } else { lowSec = 0f; highSec = 0f }
        if (lowSec >= downAfterSec && level < maxLevel) { level++; lowSec = 0f; return true }
        if (highSec >= upAfterSec && level > 0) { level--; highSec = 0f; return true }
        return false
    }

    val shadingTransfer: Boolean get() = level < 1
    val glassBlur: Boolean get() = level < 2
}
