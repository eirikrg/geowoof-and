package com.geowoof.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GeoWoofThemeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun brand_light_scheme_is_used_when_dynamic_color_is_off() {
        assertEquals(LightColorScheme.primary, themedColorScheme(darkTheme = false).primary)
    }

    @Test
    fun brand_dark_scheme_is_used_in_dark_theme() {
        assertEquals(DarkColorScheme.primary, themedColorScheme(darkTheme = true).primary)
    }

    @Test
    fun contrast_preference_selects_the_matching_scheme() {
        assertEquals(LightColorScheme, brandColorScheme(darkTheme = false, ContrastLevel.fromSystemContrast(0f)))
        assertEquals(
            LightMediumContrastColorScheme,
            brandColorScheme(darkTheme = false, ContrastLevel.fromSystemContrast(0.5f)),
        )
        assertEquals(
            DarkHighContrastColorScheme,
            brandColorScheme(darkTheme = true, ContrastLevel.fromSystemContrast(1f)),
        )
        assertEquals(ContrastLevel.Standard, ContrastLevel.fromSystemContrast(-1f))
    }

    @Test
    fun higher_contrast_schemes_differ_from_the_standard_one() {
        assertNotEquals(LightColorScheme.primary, LightHighContrastColorScheme.primary)
        assertNotEquals(DarkColorScheme.onSurfaceVariant, DarkHighContrastColorScheme.onSurfaceVariant)
    }

    @Test
    fun spacing_tokens_are_provided() {
        var spacing: Spacing? = null
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) { spacing = GeoWoofTheme.spacing }
        }

        assertEquals(16.dp, spacing?.large)
    }

    private fun themedColorScheme(darkTheme: Boolean): ColorScheme {
        lateinit var colorScheme: ColorScheme
        composeRule.setContent {
            GeoWoofTheme(darkTheme = darkTheme, dynamicColor = false) { colorScheme = MaterialTheme.colorScheme }
        }
        return colorScheme
    }
}
