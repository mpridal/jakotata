package io.github.mpridal.jakotata.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalJakoColors = staticCompositionLocalOf { DarkJakoColors }

/** true = šetřit výkon / omezená průhlednost: sklo bez rozmazání (zadání kap. 5 a 7). */
val LocalReduceTransparency = staticCompositionLocalOf { false }

object JakoTheme {
    val colors: JakoColors
        @Composable @ReadOnlyComposable get() = LocalJakoColors.current
}

@Composable
fun JakoTataTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    reduceTransparency: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkJakoColors else LightJakoColors
    val scheme = if (dark) {
        darkColorScheme(primary = Coral, secondary = Violet, background = colors.root, surface = colors.root)
    } else {
        lightColorScheme(primary = Coral, secondary = Violet, background = colors.root, surface = colors.root)
    }
    CompositionLocalProvider(
        LocalJakoColors provides colors,
        LocalReduceTransparency provides reduceTransparency,
    ) {
        MaterialTheme(colorScheme = scheme, typography = JakoTypography, content = content)
    }
}
