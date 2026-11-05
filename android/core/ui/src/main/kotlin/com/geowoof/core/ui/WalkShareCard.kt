package com.geowoof.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.WalkStats

/**
 * The image that is shared. It shows the [mapSnapshot] when there is one, and otherwise a drawing
 * of the route, which needs no Maps key. A map shows where the walk happened: the user sees this
 * card before sharing it, and nothing leaves the device until then.
 */
@Composable
fun WalkShareCard(
    lines: List<List<GeoPoint>>,
    mapSnapshot: ImageBitmap?,
    title: String,
    subtitle: String?,
    stats: WalkStats,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(MAP_ASPECT_RATIO)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (mapSnapshot != null) {
                Image(
                    bitmap = mapSnapshot,
                    contentDescription = stringResource(R.string.core_ui_walk_route),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                RouteCanvas(
                    lines = lines,
                    contentDescription = stringResource(R.string.core_ui_walk_route),
                    modifier = Modifier.fillMaxSize(),
                    padding = spacing.extraLarge,
                )
            }
        }
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.takeIf(String::isNotEmpty)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WalkStatsGrid(stats)
            Text(
                text = stringResource(R.string.core_ui_share_brand),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val MAP_ASPECT_RATIO = 1.4f

@PreviewLightDark
@Composable
private fun WalkShareCardPreview() {
    GeoWoofTheme(dynamicColor = false) {
        WalkShareCard(
            lines = listOf(listOf(GeoPoint(40.41, -3.68), GeoPoint(40.42, -3.67), GeoPoint(40.415, -3.66))),
            mapSnapshot = null,
            title = "2 nov 2026, 9:00",
            subtitle = "Luna",
            stats = WalkStats(3120, 2530, 2380, 1.31, 763),
        )
    }
}
