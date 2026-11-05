package com.geowoof.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkPhoto
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkTotals
import java.io.File

private val ThumbnailSize = 64.dp
private val ThumbnailShape = RoundedCornerShape(12.dp)

/** A value with its label. TalkBack reads both as one element. */
@Composable
fun WalkStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The numbers of a finished walk. */
@Composable
fun WalkStatsRow(stats: WalkStats, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        WalkStat(distanceText(stats.distanceM), stringResource(R.string.core_ui_stat_distance))
        WalkStat(formatDuration(stats.durationS), stringResource(R.string.core_ui_stat_duration))
        WalkStat(formatDuration(stats.movingTimeS), stringResource(R.string.core_ui_stat_moving))
        WalkStat(paceText(stats.paceSecondsPerKm), stringResource(R.string.core_ui_stat_pace))
    }
}

/** The totals of a person or a pet. */
@Composable
fun WalkTotalsRow(totals: WalkTotals, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        WalkStat(totals.walkCount.toString(), stringResource(R.string.core_ui_total_walks))
        WalkStat(distanceText(totals.distanceM), stringResource(R.string.core_ui_stat_distance))
        WalkStat(formatDuration(totals.durationS), stringResource(R.string.core_ui_total_time))
        WalkStat(totals.weeklyStreak.toString(), stringResource(R.string.core_ui_total_streak))
    }
}

/** A walk in a list: its route, when it happened, and whether it still has to reach the server. */
@Composable
fun WalkCard(walk: Walk, petNames: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val lines = remember(walk.previewPolylines) { decodeRoute(walk.previewPolylines) }
    val numbers = stringResource(
        R.string.core_ui_walk_summary,
        distanceText(walk.stats.distanceM),
        formatDuration(walk.stats.durationS),
    )
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(walk.startedAt.formattedIn(walk.timeZone)) },
        supportingContent = {
            Column {
                Text(numbers)
                petNames?.takeIf(String::isNotEmpty)?.let { Text(it) }
            }
        },
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(ThumbnailSize)
                    .clip(ThumbnailShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                RouteCanvas(lines = lines, contentDescription = null, modifier = Modifier.size(ThumbnailSize))
            }
        },
        trailingContent = { WalkSyncIcon(walk.syncStatus) },
    )
}

@Composable
private fun WalkSyncIcon(status: WalkSyncStatus, modifier: Modifier = Modifier) {
    when (status) {
        WalkSyncStatus.Synced, WalkSyncStatus.PendingDelete -> Unit

        WalkSyncStatus.PendingSync -> Icon(
            painter = painterResource(GeoWoofIcons.CloudUpload),
            contentDescription = stringResource(R.string.core_ui_walk_pending),
            modifier = modifier,
        )

        WalkSyncStatus.Failed -> Icon(
            painter = painterResource(GeoWoofIcons.SyncProblem),
            contentDescription = stringResource(R.string.core_ui_walk_failed),
            modifier = modifier,
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

/** A photo of a walk: the copy on the device until it is uploaded, and then the one on the server. */
@Composable
fun WalkPhotoImage(photo: WalkPhoto, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image = photo.image
    val model = remember(context, photo) {
        when {
            photo.localPath != null -> File(photo.localPath!!)

            // By asset, because the signature of the URL changes each time (docs/decisions/0010).
            image?.mediumUrl != null -> ImageRequest.Builder(context)
                .data(image.mediumUrl)
                .memoryCacheKey("${image.assetId}:Medium")
                .diskCacheKey("${image.assetId}:Medium")
                .build()

            else -> null
        }
    }
    AsyncImage(
        model = model,
        contentDescription = stringResource(R.string.core_ui_walk_photo),
        modifier = modifier
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}

/** A picture just picked, before it is saved. */
@Composable
fun PickedPhotoImage(contentUri: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = contentUri,
        contentDescription = stringResource(R.string.core_ui_walk_photo),
        modifier = modifier
            .clip(ThumbnailShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}

@PreviewLightDark
@Composable
private fun WalkStatsPreview() {
    GeoWoofTheme(dynamicColor = false) {
        WalkStatsRow(WalkStats(3120, 2530, 2380, 1.31, 763))
    }
}
