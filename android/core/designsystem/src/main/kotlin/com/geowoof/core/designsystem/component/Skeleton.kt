package com.geowoof.core.designsystem.component

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.R
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/** A piece of what is on its way: it pulses softly while it arrives. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.small) {
    val colors = MaterialTheme.colorScheme
    val color by rememberInfiniteTransition(label = "skeleton").animateColor(
        initialValue = colors.surfaceContainer,
        targetValue = colors.surfaceContainerHighest,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeleton color",
    )
    Box(modifier = modifier.background(color, shape))
}

/**
 * The shape of the list that is on its way, in place of a wheel, so that nothing jumps when it comes.
 * TalkBack hears a single "Loading".
 */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, rows: Int = 6, row: @Composable () -> Unit = { SkeletonRow() }) {
    val loading = stringResource(R.string.core_designsystem_loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .clearAndSetSemantics { contentDescription = loading },
    ) {
        repeat(rows) { row() }
    }
}

/** A person or a conversation on its way. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.large, vertical = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.large),
    ) {
        SkeletonBox(Modifier.size(AvatarSize), CircleShape)
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            SkeletonBox(Modifier.fillMaxWidth(LONG_LINE).height(LineHeight))
            SkeletonBox(Modifier.fillMaxWidth(SHORT_LINE).height(LineHeight))
        }
    }
}

/** A post on its way. */
@Composable
fun SkeletonPost(modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            SkeletonBox(Modifier.size(AvatarSize), CircleShape)
            SkeletonBox(Modifier.fillMaxWidth(SHORT_LINE).height(LineHeight))
        }
        SkeletonBox(
            Modifier
                .fillMaxWidth()
                .aspectRatio(PHOTO_RATIO),
            MaterialTheme.shapes.large,
        )
        SkeletonBox(Modifier.fillMaxWidth(LONG_LINE).height(LineHeight))
    }
}

private val AvatarSize = 48.dp
private val LineHeight = 14.dp
private const val LONG_LINE = 0.7f
private const val SHORT_LINE = 0.45f
private const val PHOTO_RATIO = 1.25f
private const val PULSE_MILLIS = 800

@PreviewLightDark
@Composable
private fun SkeletonPreview() {
    GeoWoofTheme(dynamicColor = false) {
        Surface { SkeletonList() }
    }
}
