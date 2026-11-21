package io.github.mpridal.jakotata.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokeny skleněného UI (zadání kap. 7). Hodnoty se doladí podle schváleného návrhu UI
 * v `docs/design/`; komponenty berou barvy a rozměry jen odsud.
 */
@Immutable
data class JakoColors(
    val isDark: Boolean,
    /** Pozadí pod sklem, když kamera neběží (a podklad rozmazání). */
    val root: Color,
    /** Tón skla – bílý/černý 12–20 % podle režimu. */
    val glass: Color,
    /** Neprůhledná náhrada skla, když se šetří výkon nebo je omezena průhlednost. */
    val glassSolid: Color,
    /** Vnitřní okraj skla 1 dp, bílý 25 %. */
    val glassBorder: Color,
    /** Tmavší podklad pod textem nad scénou (čitelnost WCAG AA). */
    val scrim: Color,
    val onGlass: Color,
    val onGlassMuted: Color,
    val accentStart: Color,
    val accentEnd: Color,
    /** Červená jen pro běžící záznam. */
    val recording: Color,
    val shadow: Color,
) {
    val accent: Brush get() = Brush.linearGradient(listOf(accentStart, accentEnd))
}

val Coral = Color(0xFFFF7A6B)
val Violet = Color(0xFF8B5CF6)
val RecordRed = Color(0xFFFF3B30)

val LightJakoColors = JakoColors(
    isDark = false,
    root = Color(0xFFF3EEFA),
    glass = Color.White.copy(alpha = 0.18f),
    glassSolid = Color(0xF2FFFFFF),
    glassBorder = Color.White.copy(alpha = 0.25f),
    scrim = Color.Black.copy(alpha = 0.28f),
    onGlass = Color(0xFF1E1830),
    onGlassMuted = Color(0xFF4A4360),
    accentStart = Coral,
    accentEnd = Violet,
    recording = RecordRed,
    shadow = Color(0x33000000),
)

val DarkJakoColors = JakoColors(
    isDark = true,
    root = Color(0xFF1A1530),
    glass = Color.Black.copy(alpha = 0.16f),
    glassSolid = Color(0xF2221C38),
    glassBorder = Color.White.copy(alpha = 0.25f),
    scrim = Color.Black.copy(alpha = 0.40f),
    onGlass = Color(0xFFF6F2FF),
    onGlassMuted = Color(0xFFCBC3E0),
    accentStart = Coral,
    accentEnd = Violet,
    recording = RecordRed,
    shadow = Color(0x66000000),
)

/** Rozměry: zaoblení skla 24–28 dp, dotykové cíle min. 56 dp (děti), rozmazání 20–30 dp. */
object JakoDimens {
    val glassRadius = 26.dp
    val glassBlur = 24.dp
    val touchTarget = 56.dp
    val recordButton = 84.dp
    val gutter = 16.dp
}
