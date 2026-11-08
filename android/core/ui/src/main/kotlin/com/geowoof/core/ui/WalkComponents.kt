package com.geowoof.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

private val ThumbnailSize = 72.dp
private val MinStatSize = 12.sp
private val MaxStatSize = 22.sp
private const val STATS_PER_ROW = 2

/** A value with its label. TalkBack reads both as one element. */
@Composable
fun WalkStat(value: String, label: String, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = MinStatSize, maxFontSize = MaxStatSize),
            )
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The numbers of a finished walk. */
@Composable
fun WalkStatsGrid(stats: WalkStats, modifier: Modifier = Modifier) {
    StatGrid(
        stats = listOf(
            distanceText(stats.distanceM) to stringResource(R.string.core_ui_stat_distance),
            formatDuration(stats.durationS) to stringResource(R.string.core_ui_stat_duration),
            formatDuration(stats.movingTimeS) to stringResource(R.string.core_ui_stat_moving),
            paceText(stats.paceSecondsPerKm) to stringResource(R.string.core_ui_stat_pace),
        ),
        modifier = modifier,
    )
}

/** The totals of a person or a pet. */
@Composable
fun WalkTotalsGrid(totals: WalkTotals, modifier: Modifier = Modifier) {
    StatGrid(
        stats = listOf(
            totals.walkCount.toString() to stringResource(R.string.core_ui_total_walks),
            distanceText(totals.distanceM) to stringResource(R.string.core_ui_stat_distance),
            formatDuration(totals.durationS) to stringResource(R.string.core_ui_total_time),
            totals.weeklyStreak.toString() to stringResource(R.string.core_ui_total_streak),
        ),
        modifier = modifier,
    )
}

/**
 * Pairs of a value and its label, in equal columns. Four in a single row do not fit in the shared
 * image or on a narrow phone, and the last one would get only what the others leave.
 */
@Composable
private fun StatGrid(stats: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val gap = GeoWoofTheme.spacing.small
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        stats.chunked(STATS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { (value, label) -> WalkStat(value, label, Modifier.weight(1f)) }
            }
        }
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
    val spacing = GeoWoofTheme.spacing
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.extraSmall),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
        ) {
            Box(
                modifier = Modifier
                    .size(ThumbnailSize)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                RouteCanvas(lines = lines, contentDescription = null, modifier = Modifier.size(ThumbnailSize))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(walk.startedAt.formattedIn(walk.timeZone), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = numbers,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                petNames?.takeIf(String::isNotEmpty)?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            WalkSyncIcon(walk.syncStatus)
        }
    }
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
fun WalkPhotoImage(photo: WalkPhoto, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val context = LocalContext.current
    val image = photo.image
    val model = remember(context, photo) {
        when {
            photo.localPath != null -> File(photo.localPath!!)

            // By asset, because the signature of the URL changes each time.
            image?.mediumUrl != null -> ImageRequest.Builder(context)
                .data(image.mediumUrl)
                .memoryCacheKey("${image.assetId}:Medium")
                .diskCacheKey("${image.assetId}:Medium")
                .build()

            else -> null
        }
    }
    val enlarge = stringResource(R.string.core_ui_enlarge_photo)
    AsyncImage(
        model = model,
        contentDescription = stringResource(R.string.core_ui_walk_photo),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = enlarge, onClick = onClick) else Modifier),
        contentScale = ContentScale.Crop,
    )
}

/** The photos of a walk in a row. Touching one enlarges it. */
@Composable
fun WalkPhotosRow(photos: List<WalkPhoto>, photoSize: Dp, modifier: Modifier = Modifier) {
    var openPage by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small)) {
        itemsIndexed(photos) { index, photo ->
            WalkPhotoImage(photo, modifier = Modifier.size(photoSize), onClick = { openPage = index })
        }
    }
    openPage?.let { page ->
        PhotoViewer(
            photos = remember(photos) { photos.map { ViewerPhoto(it.image, it.localPath) } },
            initialPage = page,
            contentDescription = stringResource(R.string.core_ui_walk_photo),
            onDismiss = { openPage = null },
        )
    }
}

/** A picture just picked, before it is saved. */
@Composable
fun PickedPhotoImage(contentUri: String, modifier: Modifier = Modifier) {
    AsyncImage(
        model = contentUri,
        contentDescription = stringResource(R.string.core_ui_walk_photo),
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentScale = ContentScale.Crop,
    )
}

@PreviewLightDark
@Composable
private fun WalkStatsPreview() {
    GeoWoofTheme(dynamicColor = false) {
        WalkStatsGrid(WalkStats(3120, 2530, 2380, 1.31, 763))
    }
}
