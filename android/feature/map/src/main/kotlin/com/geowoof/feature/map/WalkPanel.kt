package com.geowoof.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.WalkStats
import com.geowoof.core.ui.WalkStat
import com.geowoof.core.ui.distanceText
import com.geowoof.core.ui.formatDuration
import com.geowoof.core.ui.paceText
import kotlinx.coroutines.delay
import java.time.Instant

/** What can be done now: start a walk, or follow the one in progress. */
@Composable
internal fun WalkPanel(uiState: MapUiState, actions: MapActions, modifier: Modifier = Modifier) {
    val walk = uiState.walk
    if (walk == null) {
        ExtendedFloatingActionButton(
            onClick = actions.onStart,
            modifier = modifier.padding(GeoWoofTheme.spacing.large),
        ) {
            Icon(painterResource(GeoWoofIcons.DirectionsWalk), contentDescription = null)
            Spacer(Modifier.width(GeoWoofTheme.spacing.small))
            Text(stringResource(R.string.feature_map_start_walk))
        }
        return
    }
    val spacing = GeoWoofTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.medium),
        shape = MaterialTheme.shapes.large,
        tonalElevation = PanelElevation,
        shadowElevation = PanelElevation,
    ) {
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            if (walk.isTracking) {
                LiveVisibilityChip(
                    visibility = uiState.liveVisibility,
                    onClick = actions.onChangeVisibility,
                )
            }
            when (walk.status) {
                ActiveWalkStatus.Preparing -> PreparingContent(actions)
                ActiveWalkStatus.Recording -> RecordingContent(walk, uiState.stats, actions)
                ActiveWalkStatus.Paused -> PausedContent(walk, uiState.stats, actions)
                ActiveWalkStatus.Interrupted -> InterruptedContent(actions)
                ActiveWalkStatus.Finished -> FinishedContent(actions)
            }
        }
    }
}

@Composable
private fun ColumnScope.PreparingContent(actions: MapActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(ProgressSize))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.feature_map_preparing_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.feature_map_preparing_message), style = MaterialTheme.typography.bodyMedium)
        }
    }
    TextButton(onClick = actions.onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.feature_map_cancel))
    }
}

@Composable
private fun ColumnScope.RecordingContent(walk: ActiveWalk, stats: WalkStats?, actions: MapActions) {
    LiveStats(walk, stats)
    Row(horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium)) {
        FilledTonalButton(onClick = actions.onPause, modifier = Modifier.weight(1f)) {
            Icon(painterResource(GeoWoofIcons.Pause), contentDescription = null)
            Text(stringResource(R.string.feature_map_pause))
        }
        Button(onClick = actions.onFinish, modifier = Modifier.weight(1f)) {
            Icon(painterResource(GeoWoofIcons.Stop), contentDescription = null)
            Text(stringResource(R.string.feature_map_finish))
        }
    }
}

@Composable
private fun ColumnScope.PausedContent(walk: ActiveWalk, stats: WalkStats?, actions: MapActions) {
    Text(
        text = stringResource(R.string.feature_map_paused_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    LiveStats(walk, stats)
    ResumeOrFinish(R.string.feature_map_resume, actions)
}

@Composable
private fun ColumnScope.InterruptedContent(actions: MapActions) {
    Text(
        text = stringResource(R.string.feature_map_interrupted_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(stringResource(R.string.feature_map_interrupted_message), style = MaterialTheme.typography.bodyMedium)
    ResumeOrFinish(R.string.feature_map_continue, actions)
}

@Composable
private fun ResumeOrFinish(resumeLabel: Int, actions: MapActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.medium)) {
        FilledTonalButton(onClick = actions.onResume, modifier = Modifier.weight(1f)) {
            Icon(painterResource(GeoWoofIcons.PlayArrow), contentDescription = null)
            Text(stringResource(resumeLabel))
        }
        Button(onClick = actions.onFinish, modifier = Modifier.weight(1f)) {
            Icon(painterResource(GeoWoofIcons.Stop), contentDescription = null)
            Text(stringResource(R.string.feature_map_finish))
        }
    }
}

@Composable
private fun ColumnScope.FinishedContent(actions: MapActions) {
    Text(
        text = stringResource(R.string.feature_map_finished_title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Button(onClick = actions.onReview, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.feature_map_review))
    }
}

@Composable
private fun LiveStats(walk: ActiveWalk, stats: WalkStats?) {
    val elapsed = rememberElapsedSeconds(walk)
    Row(modifier = Modifier.fillMaxWidth()) {
        WalkStat(
            value = distanceText(stats?.distanceM ?: 0),
            label = stringResource(R.string.feature_map_distance),
            modifier = Modifier.weight(1f),
        )
        WalkStat(
            value = formatDuration(elapsed),
            label = stringResource(R.string.feature_map_time),
            modifier = Modifier.weight(1f),
        )
        WalkStat(
            value = paceText(stats?.paceSecondsPerKm),
            label = stringResource(R.string.feature_map_pace),
            modifier = Modifier.weight(1f),
        )
    }
}

/** It runs while recording. While paused, it stays at the last position. */
@Composable
private fun rememberElapsedSeconds(walk: ActiveWalk): Int {
    val stopped = ((walk.lastPoint?.offsetMs ?: 0) / MS_PER_SECOND).toInt()
    val elapsed by produceState(stopped, walk.status, walk.startedAt) {
        value = stopped
        while (walk.status == ActiveWalkStatus.Recording) {
            value = ((Instant.now().toEpochMilli() - walk.startedAt.toEpochMilli()) / MS_PER_SECOND).toInt()
            delay(MS_PER_SECOND)
        }
    }
    return elapsed
}

private val PanelElevation = 3.dp
private val ProgressSize = 32.dp
private const val MS_PER_SECOND = 1000L

@PreviewLightDark
@Composable
private fun WalkPanelPreview() {
    GeoWoofTheme(dynamicColor = false) {
        WalkPanel(
            uiState = MapUiState(
                walk = ActiveWalk(
                    id = "walk",
                    startedAt = Instant.EPOCH,
                    endedAt = null,
                    timeZone = "Europe/Madrid",
                    petIds = listOf("luna"),
                    status = ActiveWalkStatus.Paused,
                    currentSegment = 0,
                    segments = listOf(RouteSegment(listOf(RoutePoint(GeoPoint(40.4, -3.7), 1_000)))),
                ),
                stats = WalkStats(1234, 900, 800, 1.5, 648),
            ),
            actions = MapActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}),
        )
    }
}
