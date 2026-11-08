package com.geowoof.core.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.geowoof.core.designsystem.component.HeroBandHeight
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import kotlinx.coroutines.launch

/**
 * How far the start of the page has gone under the top bar. The scroll moves the photo, so if it could stop halfway
 * the photo would stay in the air: when the finger lets go, it finishes going one way or the other.
 */
@Stable
class HeroCollapse internal constructor(private val list: LazyListState, private val rangePx: Float) {
    /** From 0, with the photo in the band, to 1, with the photo in the bar. */
    val progress: Float by derivedStateOf {
        if (list.firstVisibleItemIndex > 0) 1f else (list.firstVisibleItemScrollOffset / rangePx).coerceIn(0f, 1f)
    }

    val isCollapsed: Boolean by derivedStateOf { progress >= HALFWAY }

    val isHalfway: Boolean by derivedStateOf { progress > 0f && progress < 1f }

    /** A list that cannot scroll all the way has everything in sight at the top, so it goes back there. */
    suspend fun settle() {
        if (!isHalfway) return
        val target = if (isCollapsed && list.canScrollForward) rangePx else 0f
        list.animateScrollBy(target - list.firstVisibleItemScrollOffset)
    }

    private companion object {
        const val HALFWAY = 0.5f
    }
}

/** What the list scrolls until the photo arrives: from the edge of the band to the middle of the bar. */
@OptIn(ExperimentalMaterial3Api::class)
val HeroCollapseRange: Dp = HeroBandHeight + TopAppBarDefaults.TopAppBarExpandedHeight / 2

@Composable
fun rememberHeroCollapse(list: LazyListState): HeroCollapse {
    val rangePx = with(LocalDensity.current) { HeroCollapseRange.toPx() }
    val collapse = remember(list, rangePx) { HeroCollapse(list, rangePx) }
    LaunchedEffect(collapse) {
        snapshotFlow { collapse.isHalfway && !list.isScrollInProgress }.collect { isStopped ->
            // Apart, because a finger on the list interrupts it, and this has to keep listening.
            if (isStopped) launch { collapse.settle() }
        }
    }
    return collapse
}

/** Room at the end of the list to reach the bar: a list that nearly fits would leave the photo halfway. */
@Composable
fun heroListPadding(): PaddingValues = PaddingValues(bottom = GeoWoofTheme.spacing.huge + HeroCollapseRange)
