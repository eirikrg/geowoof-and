package com.geowoof.core.domain.walks

import com.geowoof.core.common.geo.GeoMath
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import javax.inject.Inject
import kotlin.math.max

/** The last kept point of the current segment, to judge the next position. */
data class TrackAnchor(val position: GeoPoint, val timeEpochMs: Long)

enum class FixRejection {
    /** A segment does not start until the position is good enough. */
    PoorFirstFix,
    PoorAccuracy,

    /** It would mean moving faster than anyone on foot: a jump of the GPS. */
    ImpossibleSpeed,
    OutOfOrder,
}

/** Decides which positions can be part of a route (ADR-0006). */
class FilterLocationUseCase @Inject constructor() {
    /** Why the position cannot be used, or `null` if it can. */
    fun rejection(fix: LocationFix, anchor: TrackAnchor?): FixRejection? = when {
        anchor == null -> FixRejection.PoorFirstFix.takeIf { fix.accuracyM > MAX_FIRST_FIX_ACCURACY_M }
        fix.timeEpochMs <= anchor.timeEpochMs -> FixRejection.OutOfOrder
        fix.accuracyM > MAX_ACCURACY_M -> FixRejection.PoorAccuracy
        impliedSpeedMps(fix, anchor) > MAX_SPEED_MPS -> FixRejection.ImpossibleSpeed
        else -> null
    }

    /** A smaller move is noise: it would add distance while standing still. */
    fun isRealMove(position: GeoPoint, accuracyM: Float, anchor: TrackAnchor): Boolean =
        GeoMath.distanceMeters(anchor.position, position) >= max(MIN_MOVE_M, accuracyM / 2)

    private fun impliedSpeedMps(fix: LocationFix, anchor: TrackAnchor): Double =
        GeoMath.distanceMeters(anchor.position, fix.position) / ((fix.timeEpochMs - anchor.timeEpochMs) / MS_PER_SECOND)

    private companion object {
        const val MAX_FIRST_FIX_ACCURACY_M = 20f
        const val MAX_ACCURACY_M = 25f
        const val MAX_SPEED_MPS = 8.5
        const val MIN_MOVE_M = 3f
        const val MS_PER_SECOND = 1000.0
    }
}
