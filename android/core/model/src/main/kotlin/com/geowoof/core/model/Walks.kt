package com.geowoof.core.model

import java.time.Instant

data class GeoPoint(val latitude: Double, val longitude: Double)

/** A position as the device reports it, before any filtering. */
data class LocationFix(val position: GeoPoint, val accuracyM: Float, val timeEpochMs: Long)

/** A kept point of a route. [offsetMs] counts from the start of the walk. */
data class RoutePoint(val position: GeoPoint, val offsetMs: Long)

/** A stretch without pauses: each resume opens a new one, so a pause never draws a straight line (ADR-0006). */
data class RouteSegment(val points: List<RoutePoint>)

/** The limits of the API (docs/api.md). */
object WalkLimits {
    const val MAX_DURATION_MS = 12L * 60 * 60 * 1000
    const val MAX_POINTS = 20_000
    const val MAX_PHOTOS = 10
    const val MAX_PETS = 10
    const val NOTE_MAX_LENGTH = 1000

    /** A segment needs a line, and a walk needs a segment, for the server to accept it. */
    const val MIN_SEGMENT_POINTS = 2
}

/** The states of a walk being recorded (docs/architecture.md, 4.4). */
enum class ActiveWalkStatus {
    /** Waiting for the first precise position: the walk starts with it. */
    Preparing,
    Recording,
    Paused,

    /** The recording stopped by itself, for example because the system killed the app. */
    Interrupted,
    Finished,
}

/** The walk in progress. There is at most one, and it lives in Room to survive the death of the process. */
data class ActiveWalk(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val timeZone: String,
    val petIds: List<String>,
    val status: ActiveWalkStatus,
    val currentSegment: Int,
    val segments: List<RouteSegment>,
) {
    val lastPoint: RoutePoint? get() = segments.lastOrNull { it.points.isNotEmpty() }?.points?.last()

    val pointCount: Int get() = segments.sumOf { it.points.size }

    /** Whether there is enough for the server to accept it. */
    val hasRoute: Boolean get() = segments.any { it.points.size >= WalkLimits.MIN_SEGMENT_POINTS }

    /** Whether the device should be receiving positions. */
    val isTracking: Boolean get() = status == ActiveWalkStatus.Preparing || status == ActiveWalkStatus.Recording
}

enum class WalkVisibility {
    Everyone,
    Friends,
    OnlyMe,
}

/** Walks are written offline and uploaded later (docs/decisions/0009). */
enum class WalkSyncStatus {
    PendingSync,
    Synced,
    Failed,
    PendingDelete,
}

data class WalkStats(
    val distanceM: Int,
    val durationS: Int,
    val movingTimeS: Int,
    val averageSpeedMps: Double,
    val paceSecondsPerKm: Int?,
)

/** A photo of a walk: on the device until it is uploaded, and on the server afterwards. */
data class WalkPhoto(val image: RemoteImage?, val localPath: String?)

data class Walk(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val timeZone: String,
    val petIds: List<String>,
    val visibility: WalkVisibility,
    val note: String?,
    val stats: WalkStats,
    val previewPolylines: List<String>,
    val photos: List<WalkPhoto>,
    val syncStatus: WalkSyncStatus,
    /** Why the server rejected it, when [syncStatus] is [WalkSyncStatus.Failed]. */
    val syncErrorCode: String?,
    val updatedAt: Instant,
)

data class WalkTotals(
    val walkCount: Int,
    val distanceM: Int,
    val durationS: Int,
    val movingTimeS: Int,
    val weeklyStreak: Int,
)

data class EncodedSegment(val polyline: String, val timeOffsetsMs: List<Long>)

/** A finished walk that the user decided to keep. */
data class NewWalk(
    val id: String,
    val startedAt: Instant,
    val endedAt: Instant,
    val timeZone: String,
    val petIds: List<String>,
    val visibility: WalkVisibility,
    val note: String?,
    val stats: WalkStats,
    val segments: List<EncodedSegment>,
    /** The photos picked by the user, still in the system's temporary URIs. */
    val photoUris: List<String>,
    val updatedAt: Instant,
)
