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

// Hodnoty ze schváleného návrhu UI (docs/design/, 21. 11. 2026).
val Coral = Color(0xFFFF6B57)
val Violet = Color(0xFF8B5CF6)
val RecordRed = Color(0xFFFF3B30)
val Ink = Color(0xFF1B1724)

val LightJakoColors = JakoColors(
    isDark = false,
    root = Color(0xFFF3EEFA),
    glass = Color.White.copy(alpha = 0.72f),
    glassSolid = Color(0xF2FFFFFF),
    glassBorder = Color.White.copy(alpha = 0.90f),
    scrim = Color.Black.copy(alpha = 0.28f),
    onGlass = Ink,
    onGlassMuted = Color(0xFF4A4458),
    accentStart = Coral,
    accentEnd = Coral,
    recording = RecordRed,
    shadow = Color(0x38000000),
)

val DarkJakoColors = JakoColors(
    isDark = true,
    root = Color(0xFF2B2333),
    glass = Color(0xFF16121E).copy(alpha = 0.42f),
    glassSolid = Color(0xF216121E),
    glassBorder = Color.White.copy(alpha = 0.18f),
    scrim = Color.Black.copy(alpha = 0.40f),
    onGlass = Color.White,
    onGlassMuted = Color.White.copy(alpha = 0.80f),
    accentStart = Coral,
    accentEnd = Coral,
    recording = RecordRed,
    shadow = Color(0x38000000),
)

/**
 * Rozměry z návrhu: pilulky 52 dp (zaoblení 26), spodní panel zaoblení 32, rozmazání 22 dp,
 * tlačítko nahrávání 84 dp. Dotykové cíle min. 52–56 dp (děti).
 */
object JakoDimens {
    val glassRadius = 26.dp
    val panelRadius = 32.dp
    val glassBlur = 22.dp
    val pill = 52.dp
    val touchTarget = 56.dp
    val recordButton = 84.dp
    val gutter = 16.dp
}
