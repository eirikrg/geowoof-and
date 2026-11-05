package com.geowoof.core.model

import java.time.Instant

/** Who can see someone on the live map while they walk (docs/realtime.md). */
enum class LiveVisibility {
    Everyone,
    FriendsOnly,
    Nobody,
}

/**
 * [liveVisibility] is `null` until the person chooses: the app asks before the first walk, and
 * shares nothing without an answer. [hideStartDistanceM] is how far from the start the position
 * stays on the device.
 */
data class UserSettings(
    val liveVisibility: LiveVisibility? = null,
    val hideStartDistanceM: Int = DEFAULT_HIDE_START_M,
) {
    companion object {
        const val DEFAULT_HIDE_START_M = 200
        val HIDE_START_OPTIONS = listOf(0, DEFAULT_HIDE_START_M, 500)
    }
}

data class LivePet(val id: String, val name: String, val avatar: RemoteImage?)

/** Someone walking now, with what the map shows of them. */
data class LiveWalker(
    val userId: String,
    val username: String,
    val displayName: String,
    val avatar: RemoteImage?,
    val pets: List<LivePet>,
    val position: GeoPoint,
    val updatedAt: Instant,
)

/** The part of the map on the screen, in degrees. */
data class MapViewport(val north: Double, val south: Double, val east: Double, val west: Double) {
    fun contains(point: GeoPoint): Boolean = point.latitude in south..north && point.longitude in west..east
}

/** Who is walking in the part of the map on the screen. */
data class LiveNearby(
    val walkers: List<LiveWalker> = emptyList(),
    /** The area needs too many cells: the map has to get closer to show anyone. */
    val isTooWide: Boolean = false,
)
