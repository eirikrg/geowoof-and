package com.geowoof.core.data.mapper

import com.geowoof.core.database.ActiveWalkEntity
import com.geowoof.core.database.ActiveWalkPointEntity
import com.geowoof.core.database.ActiveWalkWithPoints
import com.geowoof.core.database.ID_SEPARATOR
import com.geowoof.core.model.ActiveWalk
import com.geowoof.core.model.ActiveWalkStatus
import com.geowoof.core.model.GeoPoint
import com.geowoof.core.model.RoutePoint
import com.geowoof.core.model.RouteSegment
import java.time.Instant

internal fun ActiveWalkWithPoints.toModel() = ActiveWalk(
    id = walk.id,
    startedAt = Instant.ofEpochMilli(walk.startedAtEpochMs),
    endedAt = walk.endedAtEpochMs?.let(Instant::ofEpochMilli),
    timeZone = walk.timeZone,
    petIds = walk.petIds.split(ID_SEPARATOR).filter(String::isNotEmpty),
    status = ActiveWalkStatus.valueOf(walk.status),
    currentSegment = walk.currentSegment,
    segments = points.toSegments(),
)

/** Room does not promise any order in a relation. */
private fun List<ActiveWalkPointEntity>.toSegments(): List<RouteSegment> = sortedBy { it.offsetMs }
    .groupBy { it.segment }
    .toSortedMap()
    .values
    .map { points ->
        RouteSegment(points.map { RoutePoint(GeoPoint(it.latitude, it.longitude), it.offsetMs) })
    }

internal fun newActiveWalkEntity(id: String, startedAtEpochMs: Long, timeZone: String, petIds: List<String>) =
    ActiveWalkEntity(
        id = id,
        startedAtEpochMs = startedAtEpochMs,
        endedAtEpochMs = null,
        timeZone = timeZone,
        petIds = petIds.joinToString(ID_SEPARATOR),
        status = ActiveWalkStatus.Preparing.name,
        currentSegment = 0,
    )
