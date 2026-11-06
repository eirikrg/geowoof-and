package com.geowoof.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import java.time.Instant

/** What can be done now: start a walk, or follow the one in progress. */
@Composable
internal fun WalkPanel(uiState: MapUiState, actions: MapActions, modifier: Modifier = Modifier) {
    val walk = uiState.walk
    if (walk == null) {
        ExtendedFloatingActionButton(
            onClick = actions.onStart,
            modifier = modifier
                .padding(GeoWoofTheme.spacing.large)
                .heightIn(min = StartHeight),
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(painterResource(GeoWoofIcons.DirectionsWalk), contentDescription = null)
            Spacer(Modifier.width(GeoWoofTheme.spacing.small))
            Text(stringResource(R.string.feature_map_start_walk), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val spacing = GeoWoofTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.medium),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = PanelElevation,
    ) {
        Column(
            modifier = Modifier.padding(spacing.large),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            if (walk.isTracking) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (walk.status == ActiveWalkStatus.Recording) OnAWalk()
                    LiveVisibilityChip(
                        visibility = uiState.liveVisibility,
                        onClick = actions.onChangeVisibility,
                    )
                }
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
        FilledTonalButton(
            onClick = actions.onPause,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonHeight),
        ) {
            Icon(painterResource(GeoWoofIcons.Pause), contentDescription = null)
            Spacer(Modifier.width(GeoWoofTheme.spacing.small))
            Text(stringResource(R.string.feature_map_pause))
        }
        Button(
            onClick = actions.onFinish,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonHeight),
        ) {
            Icon(painterResource(GeoWoofIcons.Stop), contentDescription = null)
            Spacer(Modifier.width(GeoWoofTheme.spacing.small))
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
        FilledTonalButton(
            onClick = actions.onResume,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonHeight),
        ) {
            Icon(painterResource(GeoWoofIcons.PlayArrow), contentDescription = null)
            Spacer(Modifier.width(GeoWoofTheme.spacing.small))
            Text(stringResource(resumeLabel))
        }
        Button(
            onClick = actions.onFinish,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = ButtonHeight),
        ) {
            Icon(painterResource(GeoWoofIcons.Stop), contentDescription = null)
            Spacer(Modifier.width(GeoWoofTheme.spacing.small))
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

private val PanelElevation = 6.dp
private val ProgressSize = 32.dp
private val StartHeight = 64.dp
private val ButtonHeight = 52.dp

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
