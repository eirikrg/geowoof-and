package com.geowoof.feature.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.WalkStats
import com.geowoof.core.ui.distanceText
import com.geowoof.core.ui.formatDuration
import com.geowoof.core.ui.paceText
import kotlinx.coroutines.delay
import java.time.Instant

/** That it is recording, with a dot that breathes so that it is clear that it has not stopped. */
@Composable
internal fun RecordingDot(modifier: Modifier = Modifier) {
    val alpha by rememberInfiniteTransition(label = "recording").animateFloat(
        initialValue = 1f,
        targetValue = DOT_MIN_ALPHA,
        animationSpec = infiniteRepeatable(tween(DOT_MILLIS), RepeatMode.Reverse),
        label = "recording dot",
    )
    val color = MaterialTheme.colorScheme.tertiary
    Box(
        modifier = modifier
            .size(DotSize)
            .graphicsLayer { this.alpha = alpha }
            .background(color, CircleShape),
    )
}

@Composable
internal fun OnAWalk(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GeoWoofTheme.spacing.small),
    ) {
        RecordingDot()
        Text(stringResource(R.string.feature_map_on_a_walk), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
internal fun LiveStats(walk: ActiveWalk, stats: WalkStats?) {
    val elapsed = rememberElapsedSeconds(walk)
    // Large, to read it at a glance outdoors. Nunito has digits of the same width, so the time does not dance.
    Row(modifier = Modifier.fillMaxWidth()) {
        LiveStat(
            value = formatDuration(elapsed),
            label = stringResource(R.string.feature_map_time),
            modifier = Modifier.weight(1f),
        )
        LiveStat(
            value = distanceText(stats?.distanceM ?: 0),
            label = stringResource(R.string.feature_map_distance),
            modifier = Modifier.weight(1f),
        )
        LiveStat(
            value = paceText(stats?.paceSecondsPerKm),
            label = stringResource(R.string.feature_map_pace),
            modifier = Modifier.weight(1f),
        )
    }
}

/** A value with its label. TalkBack reads both as one element. */
@Composable
private fun LiveStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = value, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** It runs while recording. While paused, it stays at the last position. */
@Composable
internal fun rememberElapsedSeconds(walk: ActiveWalk): Int {
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

private val DotSize = 10.dp
private const val DOT_MILLIS = 900
private const val DOT_MIN_ALPHA = 0.25f
private const val MS_PER_SECOND = 1000L
