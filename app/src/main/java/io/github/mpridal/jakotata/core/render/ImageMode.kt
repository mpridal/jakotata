package io.github.mpridal.jakotata.core.render

/** Režimy obrazu (zadání kap. 2.3–2.5). */
enum class ImageMode {
    /** Režim A: tátova textura napnutá na obličej dcery. */
    MASK,
    /** Režim B: tátova fotka přes celou obrazovku, oživená mimikou dcery. */
    PUPPET,
    /** Levá polovina obličeje táta, pravá dcera. */
    HALF,
    /** Rozdělená obrazovka: originál vedle úpravy. */
    COMPARE,
}
