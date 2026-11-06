package com.geowoof.core.ui

import androidx.annotation.RawRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance

private const val DARK_SURFACE = 0.5f

/** Whether what is on screen is dark: the app can be dark while the system is not. */
@Composable
fun isThemeDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE

/** The cartography in the colors of the app, in the JSON that `MapStyleOptions` reads. */
@RawRes
@Composable
fun mapStyleResource(): Int = if (isThemeDark()) R.raw.core_ui_map_style_dark else R.raw.core_ui_map_style_light
