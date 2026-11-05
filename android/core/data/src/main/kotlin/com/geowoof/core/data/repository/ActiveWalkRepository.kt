package com.geowoof.core.data.repository

import com.geowoof.core.common.UuidV7
import com.geowoof.core.data.mapper.newActiveWalkEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.database.ActiveWalkDao
import com.geowoof.core.database.ActiveWalkEntity
import com.geowoof.core.database.ActiveWalkPointEntity
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.WalkLimits
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject

/**
 * The walk being recorded. Everything goes to Room at once, so that the death of the process only
 * loses the time until it comes back (docs/architecture.md, 4.4).
 */
interface ActiveWalkRepository {
    val activeWalk: Flow<ActiveWalk?>

    suspend fun current(): ActiveWalk?

    /** Starts waiting for the first precise position. */
    suspend fun start(petIds: List<String>): ActiveWalk

    /** The first precise position arrived: the walk really starts now. */
    suspend fun beginRecording(startedAtEpochMs: Long)

    suspend fun appendPoint(walkId: String, segment: Int, point: RoutePoint, accuracyM: Float)

    suspend fun pause()

    /** Opens a new segment: what happened since it stopped is not part of the route. */
    suspend fun resume()

    /** The recording stopped by itself. The user decides whether to continue or to finish. */
    suspend fun interrupt()

    suspend fun finish()

    suspend fun discard()
}

internal class DefaultActiveWalkRepository @Inject constructor(
    private val walks: ActiveWalkDao,
    private val clock: Clock,
) : ActiveWalkRepository {
    override val activeWalk: Flow<ActiveWalk?> = walks.observe().map { it?.toModel() }

    override suspend fun current(): ActiveWalk? = walks.current()?.toModel()

    override suspend fun start(petIds: List<String>): ActiveWalk {
        val now = clock.millis()
        val entity = newActiveWalkEntity(UuidV7.generate(now), now, ZoneId.systemDefault().id, petIds)
        walks.clear()
        walks.upsert(entity)
        return checkNotNull(current())
    }

    override suspend fun beginRecording(startedAtEpochMs: Long) = update(ActiveWalkStatus.Preparing) {
        it.copy(startedAtEpochMs = startedAtEpochMs, status = ActiveWalkStatus.Recording.name)
    }

    override suspend fun appendPoint(walkId: String, segment: Int, point: RoutePoint, accuracyM: Float) {
        walks.appendPointIfRecording(
            ActiveWalkPointEntity(
                walkId = walkId,
                segment = segment,
                offsetMs = point.offsetMs,
                latitude = point.position.latitude,
                longitude = point.position.longitude,
                accuracyM = accuracyM,
            ),
        )
    }

    override suspend fun pause() = update(ActiveWalkStatus.Preparing, ActiveWalkStatus.Recording) {
        it.copy(status = ActiveWalkStatus.Paused.name)
    }

    override suspend fun resume() {
        val current = walks.current() ?: return
        if (current.walk.status == ActiveWalkStatus.Paused.name ||
            current.walk.status == ActiveWalkStatus.Interrupted.name
        ) {
            val status = if (current.points.isEmpty()) ActiveWalkStatus.Preparing else ActiveWalkStatus.Recording
            walks.upsert(current.walk.copy(status = status.name, currentSegment = current.walk.currentSegment + 1))
        }
    }

    override suspend fun interrupt() = update(ActiveWalkStatus.Preparing, ActiveWalkStatus.Recording) {
        it.copy(status = ActiveWalkStatus.Interrupted.name)
    }

    override suspend fun finish() {
        val current = walks.current() ?: return
        if (current.walk.status != ActiveWalkStatus.Finished.name) {
            val start = current.walk.startedAtEpochMs

            // The positions carry the clock of the GPS: the route must fit between the start and the end.
            val lastPoint = start + (current.points.maxOfOrNull { it.offsetMs } ?: 0)
            val end = maxOf(clock.millis(), lastPoint).coerceAtMost(start + WalkLimits.MAX_DURATION_MS)
            walks.upsert(current.walk.copy(status = ActiveWalkStatus.Finished.name, endedAtEpochMs = end))
        }
    }

    override suspend fun discard() = walks.clear()

    private suspend fun update(vararg from: ActiveWalkStatus, change: (ActiveWalkEntity) -> ActiveWalkEntity) {
        val current = walks.current()?.walk ?: return
        if (from.any { it.name == current.status }) walks.upsert(change(current))
    }
}
