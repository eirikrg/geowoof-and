package com.geowoof.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import com.geowoof.core.designsystem.R

internal const val DARK_SURFACE = 0.5f

/**
 * The logo with its trail. On a dark surface the trail changes to a light green: the app can be dark
 * while the system is not, so a color resource for the night would not always follow it.
 */
@Composable
fun GeoWoofLogo(contentDescription: String?, modifier: Modifier = Modifier) {
    val isOnDark = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE
    Image(
        painter = painterResource(
            if (isOnDark) R.drawable.core_designsystem_logo_night else R.drawable.core_designsystem_logo,
        ),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

/** Only the head, for small sizes: the trail would be noise there. */
@Composable
fun GeoWoofLogoHead(contentDescription: String?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.core_designsystem_logo_head),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
