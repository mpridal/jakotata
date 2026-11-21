package io.github.mpridal.jakotata.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.mpridal.jakotata.ui.theme.JakoDimens
import io.github.mpridal.jakotata.ui.theme.JakoTheme
import io.github.mpridal.jakotata.ui.theme.LocalReduceTransparency

/** Haze stav pozadí (živý obraz kamery), který skleněné panely rozmazávají. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Skleněný povrch (zadání kap. 7): rozmazání pozadí + tón + 1 dp světlý okraj + jemný stín.
 * Bez zdroje rozmazání nebo při šetření výkonu poloprůhledný tón.
 */
@Composable
fun Modifier.glass(
    shape: Shape = RoundedCornerShape(JakoDimens.glassRadius),
    elevation: Dp = 6.dp,
): Modifier {
    val c = JakoTheme.colors
    val hazeState = LocalHazeState.current
    val reduce = LocalReduceTransparency.current
    var m = this
    if (elevation > 0.dp) m = m.shadow(elevation, shape, ambientColor = c.shadow, spotColor = c.shadow)
    m = m.clip(shape)
    m = if (hazeState != null && !reduce) {
        val style = remember(c) {
            HazeBlurStyle {
                blurRadius(JakoDimens.glassBlur)
                backgroundColor(c.root)
                noiseFactor(0.08f)
                colorEffects(listOf(HazeColorEffect.tint(c.glass)))
            }
        }
        val input = remember(hazeState) { HazeInput.Sources(hazeState) }
        m.hazeBlur(input = input, style = style)
    } else {
        m.background(c.glassSolid)
    }
    return m.border(1.dp, c.glassBorder, shape)
}

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(JakoDimens.glassRadius),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.glass(shape), content = content)
}
