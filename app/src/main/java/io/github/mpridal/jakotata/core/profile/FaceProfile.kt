package io.github.mpridal.jakotata.core.profile

import android.graphics.Bitmap
import io.github.mpridal.jakotata.core.face.SkinStats

/**
 * Obličej táty (zadání kap. 2.2). MVP z jedné čelní fotky: [texture] je čtvercový výřez fotky kolem
 * obličeje (1024 × 1024) a [coords] jsou body sítě (468 vrcholů, [x, y, z]) normalizované k výřezu.
 * Maska bere tyto body jako texturové souřadnice – „atlas“ je tedy v prostoru fotky, ne v UV prostoru
 * kanonického modelu (to přijde s bočními fotkami v milníku 7).
 */
class FaceProfile(
    val name: String,
    val texture: Bitmap,
    val coords: FloatArray,
    val skin: SkinStats,
    val createdAtMs: Long,
)
