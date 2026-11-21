package io.github.mpridal.jakotata.core.render

/**
 * Jak se narovnaný obraz kamery vejde na výstup (zadání kap. 2.3): vyplní celou plochu
 * (center crop) a u přední kamery se zrcadlí. Stejné mapování používá obraz i body obličeje.
 *
 * @param scaleX šířka zobrazeného obrazu / šířka výstupu (≥ 1 = přesahuje a ořízne se)
 */
data class DisplayMapping(val scaleX: Float, val scaleY: Float, val mirror: Boolean) {

    /** Normalizovaný bod narovnaného obrazu (y dolů) → normalizovaný bod výstupu (y dolů). */
    fun imageToView(x: Float, y: Float): Pair<Float, Float> {
        val mx = if (mirror) 1f - x else x
        return Pair((mx - 0.5f) * scaleX + 0.5f, (y - 0.5f) * scaleY + 0.5f)
    }

    companion object {
        /**
         * @param bufferWidth rozměry bufferu kamery (před otočením)
         * @param rotationDegrees o kolik stupňů po směru hodin buffer otočit, aby byl rovně
         */
        fun cover(
            bufferWidth: Int, bufferHeight: Int, rotationDegrees: Int,
            viewWidth: Int, viewHeight: Int, mirror: Boolean,
        ): DisplayMapping {
            if (bufferWidth <= 0 || bufferHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
                return DisplayMapping(1f, 1f, mirror)
            }
            val swap = rotationDegrees % 180 != 0
            val imgW = if (swap) bufferHeight else bufferWidth
            val imgH = if (swap) bufferWidth else bufferHeight
            val imgAspect = imgW.toFloat() / imgH
            val viewAspect = viewWidth.toFloat() / viewHeight
            return if (imgAspect > viewAspect) {
                DisplayMapping(imgAspect / viewAspect, 1f, mirror)
            } else {
                DisplayMapping(1f, viewAspect / imgAspect, mirror)
            }
        }
    }
}
