package com.geowoof.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.geowoof.core.designsystem.component.HeroBand
import com.geowoof.core.designsystem.component.HeroBandHeight
import com.geowoof.core.designsystem.component.HeroPhotoFrame
import com.geowoof.core.designsystem.component.heroTopBarColors
import com.geowoof.core.designsystem.theme.GeoWoofTheme

/*
 * The start of the page of someone or of a pet, the same everywhere: a [HeroBand] with the photo halfway out of it.
 * As the page goes up, the photo flies from the band to the top bar and gets smaller, and the name appears next to it.
 *
 * A page puts together four pieces: [rememberHeroCollapse] with the state of its LazyColumn, [HeroTopBar] as the top
 * bar of its Scaffold, a [HeroBand] without photo as the first item of the list (the photo is the one of the bar,
 * which flies over the page) with [heroListPadding] as the padding of the list, and [HeroRefreshBox] around it.
 */

/**
 * The top bar of a page with a [HeroBand]. At the start it says [title]. As the page goes up, [photo], of [photoSize]
 * in the band, flies to the bar, and [collapsedTitle] with [collapsedSubtitle] appears next to it. Without
 * [collapsedTitle], as while the page is loading, there is nothing to fly.
 */
@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroTopBar(
    collapse: HeroCollapse,
    title: String,
    photoSize: Dp,
    modifier: Modifier = Modifier,
    collapsedTitle: String? = null,
    collapsedSubtitle: String? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    photo: @Composable () -> Unit = {},
) {
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    var landing by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = modifier.onGloballyPositioned { barOrigin = it.positionInRoot() }) {
        TopAppBar(
            colors = heroTopBarColors(),
            title = {
                HeroBarTitle(title, collapsedTitle, collapsedSubtitle, collapse, onPlaceLanding = { landing = it })
            },
            navigationIcon = navigationIcon,
            actions = actions,
        )
        if (collapsedTitle != null) {
            FlyingPhoto(collapse, photoSize, landing = { landing - barOrigin }, photo = photo)
        }
    }
}

/** The indicator of pulling to refresh comes out from below the photo, which goes over the list and would hide it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    photoSize: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = HeroBandHeight + photoSize / 2),
            )
        },
    ) { content() }
}

/** The two titles are on top of each other, and each one is only for TalkBack while it can be seen. */
@Composable
private fun HeroBarTitle(
    title: String,
    collapsedTitle: String?,
    collapsedSubtitle: String?,
    collapse: HeroCollapse,
    onPlaceLanding: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .graphicsLayer {
                    val leaving = (collapse.progress / TITLE_LEAVES_AT).coerceAtMost(1f)
                    alpha = 1f - leaving
                    translationY = -TitleTravel.toPx() * leaving
                }
                .then(if (collapse.isCollapsed) Modifier.clearAndSetSemantics {} else Modifier),
        )
        if (collapsedTitle != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier = Modifier
                        .size(LandedPhotoSize)
                        .onGloballyPositioned { onPlaceLanding(it.boundsInRoot().center) },
                )
                CollapsedTitle(
                    title = collapsedTitle,
                    subtitle = collapsedSubtitle,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .graphicsLayer {
                            val arriving = ((collapse.progress - TITLE_ARRIVES_FROM) / (1f - TITLE_ARRIVES_FROM))
                                .coerceIn(0f, 1f)
                            alpha = arriving
                            translationY = TitleTravel.toPx() * (1f - arriving)
                        }
                        .then(if (collapse.isCollapsed) Modifier else Modifier.clearAndSetSemantics {}),
                )
            }
        }
    }
}

/** With a very large font there is no room for two lines in the bar, and the subtitle is left out. */
@Composable
private fun CollapsedTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val fitsTwoLines = LocalDensity.current.fontScale <= LARGE_FONT_SCALE
    Column(
        modifier = modifier.padding(start = GeoWoofTheme.spacing.medium),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null && fitsTwoLines) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The photo of the page. It is over the bar and the page so that it can go from one to the other. It goes up with
 * the scroll, as if it were part of the page, while it gets smaller and goes to its place in the bar. It takes no room
 * in the bar, and it does not stop the touches on what is under it.
 */
@Composable
private fun BoxScope.FlyingPhoto(
    collapse: HeroCollapse,
    photoSize: Dp,
    landing: () -> Offset,
    photo: @Composable () -> Unit,
) {
    var bar by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .matchParentSize()
            .onSizeChanged { bar = it }
            .wrapContentSize(Alignment.TopStart, unbounded = true),
    ) {
        HeroPhotoFrame(
            modifier = Modifier.graphicsLayer {
                val progress = collapse.progress
                val startX = bar.width / 2f
                val startY = bar.height + HeroBandHeight.toPx()
                val end = landing()
                translationX = lerp(startX, end.x, FastOutSlowInEasing.transform(progress)) - size.width / 2
                translationY = lerp(startY, end.y, progress) - size.height / 2
                val scale = lerp(1f, LandedPhotoSize.toPx() / size.width, progress)
                scaleX = scale
                scaleY = scale
            },
        ) {
            Box(modifier = Modifier.size(photoSize)) { photo() }
        }
    }
}

private val LandedPhotoSize = 40.dp
private val TitleTravel = 12.dp
private const val TITLE_LEAVES_AT = 0.35f
private const val TITLE_ARRIVES_FROM = 0.55f
private const val LARGE_FONT_SCALE = 1.3f
