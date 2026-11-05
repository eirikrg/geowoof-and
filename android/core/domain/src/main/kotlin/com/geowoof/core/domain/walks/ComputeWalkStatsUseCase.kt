package com.geowoof.core.domain.walks

import com.geowoof.core.common.geo.GeoMath
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.WalkStats
import javax.inject.Inject
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * The numbers shown while recording and until the server answers. It is the same algorithm as the
 * server, which has the last word (ADR-0006, statistics version 1).
 */
class ComputeWalkStatsUseCase @Inject constructor() {
    operator fun invoke(segments: List<RouteSegment>, durationMs: Long): WalkStats {
        var distanceM = 0.0
        var movingMs = 0L

        // Only inside each segment: the time and the distance of a pause are not part of the walk.
        for (segment in segments) {
            segment.points.zipWithNext { from, to ->
                val stepM = GeoMath.distanceMeters(from.position, to.position)
                val elapsedMs = to.offsetMs - from.offsetMs
                val speedMps = stepM / (elapsedMs / MS_PER_SECOND)
                if (speedMps <= IMPOSSIBLE_SPEED_MPS) {
                    distanceM += stepM
                    if (speedMps >= MOVING_SPEED_MPS) movingMs += elapsedMs
                }
            }
        }

        val movingS = movingMs / MS_PER_SECOND
        val hasMoved = distanceM > 0 && movingS > 0
        return WalkStats(
            distanceM = distanceM.roundToInt(),
            durationS = (durationMs / MS_PER_SECOND).roundToInt(),
            movingTimeS = movingS.roundToInt(),
            averageSpeedMps = if (hasMoved) round(distanceM / movingS * SPEED_PRECISION) / SPEED_PRECISION else 0.0,
            paceSecondsPerKm = if (hasMoved) (movingS / (distanceM / METERS_PER_KM)).roundToInt() else null,
        )
    }

    private companion object {
        const val IMPOSSIBLE_SPEED_MPS = 8.5
        const val MOVING_SPEED_MPS = 0.5
        const val MS_PER_SECOND = 1000.0
        const val METERS_PER_KM = 1000.0
        const val SPEED_PRECISION = 100.0
    }
}
