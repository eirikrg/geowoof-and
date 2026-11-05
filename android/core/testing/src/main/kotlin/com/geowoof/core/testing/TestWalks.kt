package com.geowoof.core.testing

import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkPhoto
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.model.WalkVisibility
import java.time.Instant

object TestWalks {
    val START: Instant = Instant.parse("2026-11-02T08:00:00Z")

    private const val STEP_DEGREES = 0.0001
    private const val LONGITUDE = -3.7
    private const val DEFAULT_POINTS = 3

    /** Going north, one step of about 11 m every [stepMs]. */
    fun segment(points: Int, firstOffsetMs: Long = 0, stepMs: Long = 8000, latitude: Double = 40.4) = RouteSegment(
        List(points) { index ->
            RoutePoint(GeoPoint(latitude + index * STEP_DEGREES, LONGITUDE), firstOffsetMs + index * stepMs)
        },
    )

    /** Every field has a value: each test names only what matters to it. */
    @Suppress("LongParameterList")
    fun activeWalk(
        id: String = "walk-1",
        status: ActiveWalkStatus = ActiveWalkStatus.Recording,
        segments: List<RouteSegment> = listOf(segment(DEFAULT_POINTS)),
        startedAt: Instant = START,
        endedAt: Instant? = null,
        petIds: List<String> = listOf("luna"),
        currentSegment: Int = segments.lastIndex.coerceAtLeast(0),
    ) = ActiveWalk(
        id = id,
        startedAt = startedAt,
        endedAt = endedAt,
        timeZone = "Europe/Madrid",
        petIds = petIds,
        status = status,
        currentSegment = currentSegment,
        segments = segments,
    )

    fun stats(distanceM: Int = 3120, durationS: Int = 2530, movingTimeS: Int = 2380) = WalkStats(
        distanceM = distanceM,
        durationS = durationS,
        movingTimeS = movingTimeS,
        averageSpeedMps = 1.31,
        paceSecondsPerKm = 763,
    )

    /** Every field has a value: each test names only what matters to it. */
    @Suppress("LongParameterList")
    fun walk(
        id: String = "walk-1",
        petIds: List<String> = listOf("luna"),
        syncStatus: WalkSyncStatus = WalkSyncStatus.Synced,
        syncErrorCode: String? = null,
        note: String? = "Por el Retiro",
        photos: List<WalkPhoto> = emptyList(),
        stats: WalkStats = stats(),
    ) = Walk(
        id = id,
        startedAt = START,
        endedAt = START.plusSeconds(stats.durationS.toLong()),
        timeZone = "Europe/Madrid",
        petIds = petIds,
        visibility = WalkVisibility.Friends,
        note = note,
        stats = stats,
        previewPolylines = listOf("_p~iF~ps|U_ulLnnqC"),
        photos = photos,
        syncStatus = syncStatus,
        syncErrorCode = syncErrorCode,
        updatedAt = START,
    )

    fun totals(walkCount: Int = 42, weeklyStreak: Int = 5) = WalkTotals(
        walkCount = walkCount,
        distanceM = 128_400,
        durationS = 110_300,
        movingTimeS = 98_200,
        weeklyStreak = weeklyStreak,
    )
}
