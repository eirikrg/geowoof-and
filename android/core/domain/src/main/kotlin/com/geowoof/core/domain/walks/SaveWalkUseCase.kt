package com.geowoof.core.domain.walks

import com.geowoof.core.common.geo.PolylineCodec
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.EncodedSegment
import com.geowoof.core.model.NewWalk
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.WalkLimits
import com.geowoof.core.model.WalkVisibility
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** Keeps a finished walk: it goes to the queue of uploads, which works without a connection. */
class SaveWalkUseCase @Inject constructor(
    private val walks: WalksRepository,
    private val computeStats: ComputeWalkStatsUseCase,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        walk: ActiveWalk,
        visibility: WalkVisibility,
        note: String?,
        photoUris: List<String>,
    ): Outcome<String> {
        val endedAt = checkNotNull(walk.endedAt) { "Only a finished walk can be saved." }
        val duration = Duration.between(walk.startedAt, endedAt).toMillis()
        return walks.savePending(
            NewWalk(
                id = walk.id,
                startedAt = walk.startedAt,
                endedAt = endedAt,
                timeZone = walk.timeZone,
                petIds = walk.petIds,
                visibility = visibility,
                note = note?.trim()?.ifEmpty { null },
                stats = computeStats(walk.segments, duration),
                segments = walk.segments.filter {
                    it.points.size >= WalkLimits.MIN_SEGMENT_POINTS
                }.map { it.encoded() },
                photoUris = photoUris,
                updatedAt = clock.instant(),
            ),
        )
    }

    private fun RouteSegment.encoded() = EncodedSegment(
        polyline = PolylineCodec.encode(points.map { it.position }),
        timeOffsetsMs = points.map { it.offsetMs },
    )
}
