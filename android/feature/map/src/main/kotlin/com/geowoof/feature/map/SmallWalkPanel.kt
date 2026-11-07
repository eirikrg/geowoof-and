package com.geowoof.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.WalkStats
import com.geowoof.core.ui.distanceText
import com.geowoof.core.ui.formatDuration

/**
 * The walk while the person looks around the map: a capsule with the time and the distance, and what can be done at
 * hand. Touching the capsule brings back the whole panel.
 */
@Composable
internal fun SmallWalkPanel(walk: ActiveWalk, stats: WalkStats?, actions: MapActions, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    val isRecording = walk.status == ActiveWalkStatus.Recording
    Row(
        modifier = modifier
            .clickable(
                onClickLabel = stringResource(R.string.feature_map_show_whole_panel),
                onClick = actions.onBackToWalk,
            )
            .padding(start = spacing.extraLarge, end = spacing.small, top = spacing.small, bottom = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isRecording) {
            RecordingDot()
            Spacer(Modifier.width(spacing.medium))
        }
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = formatDuration(rememberElapsedSeconds(walk)),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            Text(
                text = if (isRecording) {
                    distanceText(stats?.distanceM ?: 0)
                } else {
                    stringResource(R.string.feature_map_paused_title)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(spacing.large))
        FilledTonalIconButton(
            onClick = if (isRecording) actions.onPause else actions.onResume,
            modifier = Modifier.size(ButtonSize),
        ) {
            Icon(
                painter = painterResource(if (isRecording) GeoWoofIcons.Pause else GeoWoofIcons.PlayArrow),
                contentDescription = stringResource(
                    if (isRecording) R.string.feature_map_pause else R.string.feature_map_resume,
                ),
            )
        }
        Spacer(Modifier.width(spacing.small))
        FilledIconButton(onClick = actions.onFinish, modifier = Modifier.size(ButtonSize)) {
            Icon(
                painter = painterResource(GeoWoofIcons.Stop),
                contentDescription = stringResource(R.string.feature_map_finish),
            )
        }
    }
}

private val ButtonSize = 48.dp
