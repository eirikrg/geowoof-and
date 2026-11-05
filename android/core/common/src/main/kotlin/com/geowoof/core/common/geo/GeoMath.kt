package com.geowoof.core.common.geo

import com.geowoof.core.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** Haversine distance: precise enough for the short steps of a walk. It matches the server (ADR-0006). */
    fun distanceMeters(from: GeoPoint, to: GeoPoint): Double {
        val latFrom = Math.toRadians(from.latitude)
        val latTo = Math.toRadians(to.latitude)
        val halfDeltaLat = (latTo - latFrom) / 2
        val halfDeltaLon = Math.toRadians(to.longitude - from.longitude) / 2
        val a = sin(halfDeltaLat) * sin(halfDeltaLat) +
            cos(latFrom) * cos(latTo) * sin(halfDeltaLon) * sin(halfDeltaLon)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }
}
