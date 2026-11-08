package com.geowoof.core.domain.walks

import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.LocationFix
import javax.inject.Inject

/** The estimate of where the person is. */
data class SmoothedPosition(val position: GeoPoint, val varianceM2: Double, val timeEpochMs: Long)

/**
 * Light smoothing with a one-dimensional Kalman filter per axis. The less precise a
 * position is, and the less time has passed, the less it moves the estimate.
 */
class SmoothRouteUseCase @Inject constructor() {
    operator fun invoke(previous: SmoothedPosition?, fix: LocationFix): SmoothedPosition {
        val measurementVariance = (fix.accuracyM * fix.accuracyM).toDouble()
        if (previous == null) return SmoothedPosition(fix.position, measurementVariance, fix.timeEpochMs)

        val elapsedS = (fix.timeEpochMs - previous.timeEpochMs) / MS_PER_SECOND
        val predictedVariance = previous.varianceM2 + elapsedS * PROCESS_VARIANCE_M2_PER_S
        val gain = predictedVariance / (predictedVariance + measurementVariance)
        val from = previous.position
        return SmoothedPosition(
            position = GeoPoint(
                latitude = from.latitude + gain * (fix.position.latitude - from.latitude),
                longitude = from.longitude + gain * (fix.position.longitude - from.longitude),
            ),
            varianceM2 = (1 - gain) * predictedVariance,
            timeEpochMs = fix.timeEpochMs,
        )
    }

    private companion object {
        const val MS_PER_SECOND = 1000.0

        /** The square of a brisk walk, 2 m/s: how much the position can change in a second. */
        const val PROCESS_VARIANCE_M2_PER_S = 4.0
    }
}
