package com.geowoof.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geowoof.core.designsystem.R

/** Nunito, a single variable file: its rounded ends are those of the logo. */
@OptIn(ExperimentalTextApi::class)
private fun nunito(weight: FontWeight) = Font(
    resId = R.font.core_designsystem_nunito,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

internal val Nunito = FontFamily(
    nunito(FontWeight.Normal),
    nunito(FontWeight.Medium),
    nunito(FontWeight.SemiBold),
    nunito(FontWeight.Bold),
    nunito(FontWeight.ExtraBold),
)

private val MaterialScale = Typography()

private fun TextStyle.branded(weight: FontWeight) = copy(fontFamily = Nunito, fontWeight = weight, letterSpacing = 0.sp)

/**
 * The Material 3 type scale in Nunito. Titles are heavier to give the screens a clear hierarchy, and the
 * smaller sizes go up a little because Nunito looks smaller than Roboto at the same size.
 */
internal val GeoWoofTypography = Typography(
    displayLarge = MaterialScale.displayLarge.branded(FontWeight.ExtraBold),
    displayMedium = MaterialScale.displayMedium.branded(FontWeight.ExtraBold),
    displaySmall = MaterialScale.displaySmall.branded(FontWeight.ExtraBold),
    headlineLarge = MaterialScale.headlineLarge.branded(FontWeight.ExtraBold),
    headlineMedium = MaterialScale.headlineMedium.branded(FontWeight.ExtraBold),
    headlineSmall = MaterialScale.headlineSmall.branded(FontWeight.ExtraBold),
    titleLarge = MaterialScale.titleLarge.branded(FontWeight.ExtraBold),
    titleMedium = MaterialScale.titleMedium.branded(FontWeight.Bold).copy(fontSize = 17.sp, lineHeight = 24.sp),
    titleSmall = MaterialScale.titleSmall.branded(FontWeight.Bold).copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = MaterialScale.bodyLarge.branded(FontWeight.Medium).copy(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = MaterialScale.bodyMedium.branded(FontWeight.Medium).copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = MaterialScale.bodySmall.branded(FontWeight.Medium).copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = MaterialScale.labelLarge.branded(FontWeight.Bold).copy(fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = MaterialScale.labelMedium.branded(FontWeight.Bold).copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = MaterialScale.labelSmall.branded(FontWeight.Bold).copy(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Rounder than the Material 3 scale, like the logo. */
internal val GeoWoofShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
