package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// Wire format of the walks (docs/api.md, phase 3).

@Serializable
data class NetworkWalkSegment(val polyline: String, val timeOffsetsMs: List<Long>)

@Serializable
data class NetworkWalkClientStats(val distanceM: Int, val movingTimeS: Int)

@Serializable
data class WalkBody(
    val startedAt: String,
    val endedAt: String,
    val timeZone: String,
    val petIds: List<String>,
    val visibility: String,
    val note: String?,
    val photoAssetIds: List<String>,
    val segments: List<NetworkWalkSegment>,
    val clientStats: NetworkWalkClientStats,
    val updatedAt: String,
)

/** Lists do not include [segments]. */
@Serializable
data class NetworkWalk(
    val id: String,
    val ownerId: String = "",
    val startedAt: String,
    val endedAt: String,
    val timeZone: String,
    val petIds: List<String>,
    val visibility: String,
    val note: String? = null,
    val distanceM: Int,
    val durationS: Int,
    val movingTimeS: Int,
    val averageSpeedMps: Double,
    val paceSecondsPerKm: Int? = null,
    val previewPolylines: List<String> = emptyList(),
    val photos: List<NetworkImage> = emptyList(),
    val updatedAt: String,
    val segments: List<NetworkWalkSegment>? = null,
)

@Serializable
data class NetworkWalkStats(
    val walkCount: Int,
    val distanceM: Int,
    val durationS: Int,
    val movingTimeS: Int,
    val weeklyStreak: Int,
)
