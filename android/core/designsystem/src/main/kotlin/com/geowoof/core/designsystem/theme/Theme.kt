package com.geowoof.core.designsystem.theme

import android.app.UiModeManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * GeoWoof Material 3 theme.
 *
 * It uses the colors of the brand, generated from the icon, honouring the system contrast setting
 * (Android 14+). With [dynamicColor], on Android 12+, it takes them from the wallpaper instead: it is
 * something the person can choose in Settings, not the default, because it would hide the brand.
 */
@Composable
fun GeoWoofTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        brandColorScheme(darkTheme, systemContrastLevel())
    }

    CompositionLocalProvider(LocalSpacing provides Spacing()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = GeoWoofTypography,
            shapes = GeoWoofShapes,
            content = content,
        )
    }
}

/** Design tokens that Material 3 does not provide. */
object GeoWoofTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current
}

/** System contrast preference, mapped to the three generated contrast levels. */
internal enum class ContrastLevel {
    Standard,
    Medium,
    High,
    ;

    companion object {
        fun fromSystemContrast(contrast: Float): ContrastLevel = when {
            contrast >= HIGH_CONTRAST_THRESHOLD -> High
            contrast >= MEDIUM_CONTRAST_THRESHOLD -> Medium
            else -> Standard
        }

        private const val MEDIUM_CONTRAST_THRESHOLD = 0.33f
        private const val HIGH_CONTRAST_THRESHOLD = 0.66f
    }
}

internal fun brandColorScheme(darkTheme: Boolean, contrastLevel: ContrastLevel): ColorScheme = when (contrastLevel) {
    ContrastLevel.Standard -> if (darkTheme) DarkColorScheme else LightColorScheme
    ContrastLevel.Medium -> if (darkTheme) DarkMediumContrastColorScheme else LightMediumContrastColorScheme
    ContrastLevel.High -> if (darkTheme) DarkHighContrastColorScheme else LightHighContrastColorScheme
}

@Composable
@ReadOnlyComposable
private fun systemContrastLevel(): ContrastLevel {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return ContrastLevel.Standard
    val uiModeManager = LocalContext.current.getSystemService(UiModeManager::class.java)
    return ContrastLevel.fromSystemContrast(uiModeManager.contrast)
}
