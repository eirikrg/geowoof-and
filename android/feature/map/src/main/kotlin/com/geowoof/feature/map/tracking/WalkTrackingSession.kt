package com.geowoof.feature.map.tracking

import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.domain.walks.ComputeWalkStatsUseCase
import com.geowoof.core.domain.walks.TrackWalkUseCase
import com.geowoof.core.location.LocationDataSource
import com.geowoof.core.location.LocationPermissionException
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/** What the notification of the service shows. */
data class WalkNotificationState(val status: ActiveWalkStatus, val distanceM: Int, val startedAtEpochMs: Long)

/**
 * What the service does, without anything of Android: Room says what the walk needs, and this
 * follows it. The screens and the notification only change Room (docs/architecture.md, 4.4).
 */
class WalkTrackingSession @Inject constructor(
    private val activeWalks: ActiveWalkRepository,
    private val location: LocationDataSource,
    private val trackWalk: TrackWalkUseCase,
    private val computeStats: ComputeWalkStatsUseCase,
) {
    /** Only while the service is needed: a late update must not bring back the notification after it stops. */
    val notificationStates: Flow<WalkNotificationState> = activeWalks.activeWalk
        .filterNotNull()
        .filter { it.isTracking || it.status == ActiveWalkStatus.Paused }
        .map { walk ->
            WalkNotificationState(
                status = walk.status,
                distanceM = computeStats(walk.segments, durationMs = 0).distanceM,
                startedAtEpochMs = walk.startedAt.toEpochMilli(),
            )
        }
        .distinctUntilChanged()

    /**
     * Records while the walk needs it and returns when it no longer does: it finished, it was
     * discarded or it was interrupted. With [restoring], the system restarted the service after
     * killing the process, and part of the route has been lost.
     */
    suspend fun run(restoring: Boolean) {
        if (restoring) restore()
        activeWalks.activeWalk
            .map { it.toKey() }
            .distinctUntilChanged()
            .transformWhile { key ->
                emit(key)
                key.keepsRunning
            }
            .collectLatest { key -> if (key.isTracking) record() }
    }

    suspend fun isInterrupted(): Boolean = activeWalks.current()?.status == ActiveWalkStatus.Interrupted

    private suspend fun record() {
        val walk = activeWalks.current() ?: return
        try {
            trackWalk(walk)
        } catch (@Suppress("SwallowedException") exception: LocationPermissionException) {
            activeWalks.interrupt()
        }
    }

    /**
     * After a restart, the system may not give positions to a service started from the background.
     * If none arrive, the walk stays interrupted for the user to decide. A new segment starts in
     * any case, so that no straight line joins what was walked meanwhile.
     */
    private suspend fun restore() {
        if (activeWalks.current()?.isTracking != true) return

        activeWalks.interrupt()
        val hasPositions = try {
            withTimeoutOrNull(RESTORE_TIMEOUT) { location.fixes().first() } != null
        } catch (@Suppress("SwallowedException") exception: LocationPermissionException) {
            false
        }
        if (hasPositions) activeWalks.resume()
    }

    private fun ActiveWalk?.toKey(): RunKey = when {
        this == null -> RunKey(id = null, segment = 0, isTracking = false, keepsRunning = false)

        else -> RunKey(
            id = id,
            segment = currentSegment,
            isTracking = isTracking,
            keepsRunning = isTracking || status == ActiveWalkStatus.Paused,
        )
    }

    /** Only a change in this restarts the recording. Going from Preparing to Recording is not one. */
    private data class RunKey(val id: String?, val segment: Int, val isTracking: Boolean, val keepsRunning: Boolean)

    private companion object {
        val RESTORE_TIMEOUT = 60.seconds
    }
}
