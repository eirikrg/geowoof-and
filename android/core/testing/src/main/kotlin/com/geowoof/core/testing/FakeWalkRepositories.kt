package com.geowoof.core.testing

import androidx.paging.PagingData
import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.data.repository.WalkSyncScheduler
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.location.LocationAccess
import com.geowoof.core.location.LocationDataSource
import com.geowoof.core.location.LocationPermissionException
import com.geowoof.core.location.LocationPermissionLevel
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import com.geowoof.core.model.NewWalk
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkTotals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** Positions sent by the test, and the permission it decides. */
class FakeLocationDataSource : LocationDataSource {
    private val positions = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)

    var hasPermission = true
    var lastKnownPosition: GeoPoint? = null
    var subscriptions = 0
        private set

    /** How many are listening now. */
    val listeners: Int get() = positions.subscriptionCount.value

    override fun fixes(): Flow<LocationFix> = flow {
        if (!hasPermission) throw LocationPermissionException()
        subscriptions++
        positions.collect { emit(it) }
    }

    override suspend fun lastKnown(): GeoPoint? = lastKnownPosition

    suspend fun send(fix: LocationFix) = positions.emit(fix)
}

class FakeLocationAccess(
    var level: LocationPermissionLevel = LocationPermissionLevel.Precise,
    var enabled: Boolean = true,
) : LocationAccess {
    override fun permissionLevel() = level

    override fun isLocationEnabled() = enabled
}

/** Behaves like the real one, in memory. */
class FakeActiveWalkRepository(initial: ActiveWalk? = null) : ActiveWalkRepository {
    private val state = MutableStateFlow(initial)

    override val activeWalk: Flow<ActiveWalk?> = state

    val value: ActiveWalk? get() = state.value

    fun set(walk: ActiveWalk?) {
        state.value = walk
    }

    override suspend fun current(): ActiveWalk? = state.value

    override suspend fun start(petIds: List<String>): ActiveWalk {
        val walk = TestWalks.activeWalk(status = ActiveWalkStatus.Preparing, segments = emptyList(), petIds = petIds)
        state.value = walk
        return walk
    }

    override suspend fun beginRecording(startedAtEpochMs: Long) = change {
        it.copy(startedAt = Instant.ofEpochMilli(startedAtEpochMs), status = ActiveWalkStatus.Recording)
    }

    override suspend fun appendPoint(walkId: String, segment: Int, point: RoutePoint, accuracyM: Float) {
        change { walk ->
            if (walk.status != ActiveWalkStatus.Recording) return@change walk
            val segments = walk.segments.toMutableList()
            while (segments.size <= segment) segments += RouteSegment(emptyList())
            segments[segment] = RouteSegment(segments[segment].points + point)
            walk.copy(segments = segments)
        }
    }

    override suspend fun pause() = change {
        if (it.isTracking) it.copy(status = ActiveWalkStatus.Paused) else it
    }

    override suspend fun resume() = change {
        val isStopped = it.status == ActiveWalkStatus.Paused || it.status == ActiveWalkStatus.Interrupted
        if (isStopped) {
            val status = if (it.pointCount == 0) ActiveWalkStatus.Preparing else ActiveWalkStatus.Recording
            it.copy(status = status, currentSegment = it.currentSegment + 1)
        } else {
            it
        }
    }

    override suspend fun interrupt() = change {
        if (it.isTracking) it.copy(status = ActiveWalkStatus.Interrupted) else it
    }

    override suspend fun finish() = change {
        it.copy(status = ActiveWalkStatus.Finished, endedAt = it.startedAt.plusSeconds(FINISHED_AFTER_S))
    }

    override suspend fun discard() {
        state.value = null
    }

    private fun change(transform: (ActiveWalk) -> ActiveWalk) = state.update { walk -> walk?.let(transform) }

    private companion object {
        const val FINISHED_AFTER_S = 600L
    }
}

class FakeWalksRepository(initial: List<Walk> = emptyList()) : WalksRepository {
    private val walks = MutableStateFlow(initial)
    private val routes = MutableStateFlow<Map<String, List<RouteSegment>>>(emptyMap())
    private val totals = MutableStateFlow<Map<String?, WalkTotals>>(emptyMap())

    var saveResult: Outcome<String>? = null
    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var hasUnsynced = false
    val saved = mutableListOf<NewWalk>()
    val retried = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val refreshedWalks = mutableListOf<String>()
    val refreshedTotals = mutableListOf<String?>()

    fun setWalks(value: List<Walk>) {
        walks.value = value
    }

    fun setRoute(walkId: String, segments: List<RouteSegment>) = routes.update { it + (walkId to segments) }

    fun setTotals(petId: String?, value: WalkTotals) = totals.update { it + (petId to value) }

    override fun walks(petId: String?): Flow<PagingData<Walk>> = walks.map { list ->
        PagingData.from(if (petId == null) list else list.filter { petId in it.petIds })
    }

    override fun observeWalk(walkId: String): Flow<Walk?> = walks.map { list -> list.find { it.id == walkId } }

    override fun observeRoute(walkId: String): Flow<List<RouteSegment>?> = routes.map { it[walkId] }

    override suspend fun refreshWalk(walkId: String): Outcome<Unit> {
        refreshedWalks += walkId
        return refreshResult
    }

    override fun observeTotals(petId: String?): Flow<WalkTotals?> = totals.map { it[petId] }

    override suspend fun refreshTotals(petId: String?): Outcome<Unit> {
        refreshedTotals += petId
        return refreshResult
    }

    override suspend fun savePending(walk: NewWalk): Outcome<String> {
        val result = saveResult ?: Outcome.Success(walk.id)
        if (result is Outcome.Success) saved += walk
        return result
    }

    override suspend fun retrySync(walkId: String) {
        retried += walkId
    }

    override suspend fun delete(walkId: String) {
        deleted += walkId
        walks.update { list -> list.filterNot { it.id == walkId } }
    }

    override suspend fun hasUnsyncedWalks(): Boolean = hasUnsynced
}

class FakeWalkSyncScheduler : WalkSyncScheduler {
    val enqueued = mutableListOf<String>()
    var cancelled = 0
        private set

    override fun enqueue(walkId: String) {
        enqueued += walkId
    }

    override fun cancelAll() {
        cancelled++
    }
}
