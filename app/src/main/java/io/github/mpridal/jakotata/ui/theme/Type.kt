package io.github.mpridal.jakotata.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.mpridal.jakotata.R

/** Nunito (text) a Baloo 2 (nadpisy), OFL – stejně jako Deskovky (zadání kap. 7). */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Nunito = FontFamily(
    variable(R.font.nunito, 400),
    variable(R.font.nunito, 600),
    variable(R.font.nunito, 700),
    variable(R.font.nunito, 800),
)

val Baloo2 = FontFamily(
    variable(R.font.baloo2, 600),
    variable(R.font.baloo2, 700),
    variable(R.font.baloo2, 800),
)

private val base = Typography()

val JakoTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Baloo2, fontWeight = FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Baloo2, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = Nunito),
    bodyMedium = base.bodyMedium.copy(fontFamily = Nunito),
    labelLarge = base.labelLarge.copy(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
    labelMedium = base.labelMedium.copy(fontFamily = Nunito, fontWeight = FontWeight.Bold),
)
