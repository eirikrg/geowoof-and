package com.geowoof.core.domain.walks

import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.location.LocationDataSource
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.WalkLimits
import javax.inject.Inject

/**
 * Turns the positions of the device into the route of a walk: filter, smooth and write to Room at
 * once. It does nothing with maps, so tests and any screen can run without them (docs/architecture.md, 4.4).
 */
class TrackWalkUseCase @Inject constructor(
    private val location: LocationDataSource,
    private val activeWalks: ActiveWalkRepository,
    private val filter: FilterLocationUseCase,
    private val smooth: SmoothRouteUseCase,
) {
    /** Records the current segment of [walk] until it is cancelled. */
    suspend operator fun invoke(walk: ActiveWalk) {
        val tracker = WalkPointTracker(walk, filter, smooth)
        location.fixes().collect { fix ->
            when (val step = tracker.process(fix)) {
                TrackStep.Ignored -> Unit

                TrackStep.LimitReached -> activeWalks.pause()

                is TrackStep.Keep -> {
                    if (step.startsWalk) activeWalks.beginRecording(fix.timeEpochMs)
                    activeWalks.appendPoint(walk.id, walk.currentSegment, step.point, fix.accuracyM)
                }
            }
        }
    }
}

internal sealed interface TrackStep {
    data object Ignored : TrackStep

    /** The walk cannot grow any more, because the server would reject it. */
    data object LimitReached : TrackStep

    data class Keep(val point: RoutePoint, val startsWalk: Boolean) : TrackStep
}

/** The decisions of one recording run, which is always one segment. It has no side effects. */
internal class WalkPointTracker(
    walk: ActiveWalk,
    private val filter: FilterLocationUseCase,
    private val smooth: SmoothRouteUseCase,
) {
    private var startEpochMs: Long? = walk.startedAt.toEpochMilli().takeUnless {
        walk.status ==
            ActiveWalkStatus.Preparing
    }
    private var lastOffsetMs = walk.lastPoint?.offsetMs ?: -1
    private var pointCount = walk.pointCount
    private var anchor: TrackAnchor? = null
    private var smoothed: SmoothedPosition? = null

    fun process(fix: LocationFix): TrackStep {
        val start = startEpochMs
        val hasReachedLimit = pointCount >= WalkLimits.MAX_POINTS ||
            (start != null && fix.timeEpochMs - start > WalkLimits.MAX_DURATION_MS)
        return when {
            hasReachedLimit -> TrackStep.LimitReached
            filter.rejection(fix, anchor) != null -> TrackStep.Ignored
            start != null && fix.timeEpochMs - start <= lastOffsetMs -> TrackStep.Ignored
            else -> keepIfItMoved(fix)
        }
    }

    private fun keepIfItMoved(fix: LocationFix): TrackStep {
        val next = smooth(smoothed, fix)
        smoothed = next
        val current = anchor
        return if (current != null && !filter.isRealMove(next.position, fix.accuracyM, current)) {
            TrackStep.Ignored
        } else {
            val startsWalk = startEpochMs == null
            val start = startEpochMs ?: fix.timeEpochMs
            startEpochMs = start
            anchor = TrackAnchor(next.position, fix.timeEpochMs)
            lastOffsetMs = fix.timeEpochMs - start
            pointCount++
            TrackStep.Keep(RoutePoint(next.position, lastOffsetMs), startsWalk)
        }
    }
}
