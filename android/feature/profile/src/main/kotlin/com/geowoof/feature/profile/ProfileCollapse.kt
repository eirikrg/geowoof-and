package com.geowoof.feature.profile

import androidx.compose.foundation.gestures.animateScrollBy
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
import kotlinx.coroutines.launch

/**
 * How far the start of the profile has gone under the top bar. The scroll moves the photo, so if it could stop halfway
 * the photo would stay in the air: when the finger lets go, it finishes going one way or the other.
 */
@Stable
internal class ProfileCollapse(private val list: LazyListState, private val rangePx: Float) {
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
internal val ProfileCollapseRange: Dp = HeroBandHeight + TopAppBarDefaults.TopAppBarExpandedHeight / 2

@Composable
internal fun rememberProfileCollapse(list: LazyListState): ProfileCollapse {
    val rangePx = with(LocalDensity.current) { ProfileCollapseRange.toPx() }
    val collapse = remember(list, rangePx) { ProfileCollapse(list, rangePx) }
    LaunchedEffect(collapse) {
        snapshotFlow { collapse.isHalfway && !list.isScrollInProgress }.collect { isStopped ->
            // Apart, because a finger on the list interrupts it, and this has to keep listening.
            if (isStopped) launch { collapse.settle() }
        }
    }
    return collapse
}
