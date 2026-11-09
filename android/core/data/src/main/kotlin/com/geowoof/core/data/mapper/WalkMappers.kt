package com.geowoof.core.data.mapper

import com.geowoof.core.database.LINE_SEPARATOR
import com.geowoof.core.database.WalkBundle
import com.geowoof.core.database.WalkEntity
import com.geowoof.core.database.WalkPetEntity
import com.geowoof.core.database.WalkPhotoEntity
import com.geowoof.core.database.WalkTotalsEntity
import com.geowoof.core.database.WalkWithChildren
import com.geowoof.core.model.EncodedSegment
import com.geowoof.core.model.NewWalk
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkPhoto
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.model.toEnumOr
import com.geowoof.core.network.model.NetworkWalk
import com.geowoof.core.network.model.NetworkWalkClientStats
import com.geowoof.core.network.model.NetworkWalkSegment
import com.geowoof.core.network.model.NetworkWalkStats
import com.geowoof.core.network.model.WalkBody
import java.time.Instant

internal fun WalkWithChildren.toModel() = Walk(
    id = walk.id,
    startedAt = Instant.ofEpochMilli(walk.startedAtEpochMs),
    endedAt = Instant.ofEpochMilli(walk.endedAtEpochMs),
    timeZone = walk.timeZone,
    petIds = pets.map { it.petId },
    visibility = walk.visibility.toEnumOr(WalkVisibility.OnlyMe),
    note = walk.note,
    stats = WalkStats(
        distanceM = walk.distanceM,
        durationS = walk.durationS,
        movingTimeS = walk.movingTimeS,
        averageSpeedMps = walk.averageSpeedMps,
        paceSecondsPerKm = walk.paceSecondsPerKm,
    ),
    previewPolylines = walk.previewPolylines.split(LINE_SEPARATOR).filter(String::isNotEmpty),
    photos = photos.sortedBy { it.position }.map { it.toModel() },
    syncStatus = WalkSyncStatus.valueOf(walk.syncStatus),
    syncErrorCode = walk.syncErrorCode,
    updatedAt = Instant.ofEpochMilli(walk.updatedAtEpochMs),
)

private fun WalkPhotoEntity.toModel() = WalkPhoto(
    image = assetId?.takeIf { localPath == null }?.let { RemoteImage(it, thumbUrl, mediumUrl) },
    localPath = localPath,
)

internal fun NetworkWalk.toBundle() = WalkBundle(
    walk = WalkEntity(
        id = id,
        startedAtEpochMs = Instant.parse(startedAt).toEpochMilli(),
        endedAtEpochMs = Instant.parse(endedAt).toEpochMilli(),
        timeZone = timeZone,
        visibility = visibility,
        note = note,
        distanceM = distanceM,
        durationS = durationS,
        movingTimeS = movingTimeS,
        averageSpeedMps = averageSpeedMps,
        paceSecondsPerKm = paceSecondsPerKm,
        previewPolylines = previewPolylines.joinToString(LINE_SEPARATOR),
        updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
        syncStatus = WalkSyncStatus.Synced.name,
        syncErrorCode = null,
    ),
    pets = petIds.map { WalkPetEntity(id, it) },
    photos = photos.mapIndexed { position, image ->
        WalkPhotoEntity(id, position, image.assetId, image.thumbUrl, image.mediumUrl, localPath = null)
    },
    segmentsJson = segments?.toTrackJson(),
)

internal fun NewWalk.toBundle(photoPaths: List<String>) = WalkBundle(
    walk = WalkEntity(
        id = id,
        startedAtEpochMs = startedAt.toEpochMilli(),
        endedAtEpochMs = endedAt.toEpochMilli(),
        timeZone = timeZone,
        visibility = visibility.name,
        note = note,
        distanceM = stats.distanceM,
        durationS = stats.durationS,
        movingTimeS = stats.movingTimeS,
        averageSpeedMps = stats.averageSpeedMps,
        paceSecondsPerKm = stats.paceSecondsPerKm,
        // Until the server answers with simplified ones, the thumbnails use the whole route.
        previewPolylines = segments.joinToString(LINE_SEPARATOR) { it.polyline },
        updatedAtEpochMs = updatedAt.toEpochMilli(),
        syncStatus = WalkSyncStatus.PendingSync.name,
        syncErrorCode = null,
    ),
    pets = petIds.map { WalkPetEntity(id, it) },
    photos = photoPaths.mapIndexed { position, path ->
        WalkPhotoEntity(id, position, assetId = null, thumbUrl = null, mediumUrl = null, localPath = path)
    },
    segmentsJson = segments.map(EncodedSegment::toNetwork).toTrackJson(),
)

private fun EncodedSegment.toNetwork() = NetworkWalkSegment(polyline, timeOffsetsMs)

internal fun WalkWithChildren.toBody(segmentsJson: String, photoAssetIds: List<String>) = WalkBody(
    startedAt = Instant.ofEpochMilli(walk.startedAtEpochMs).toString(),
    endedAt = Instant.ofEpochMilli(walk.endedAtEpochMs).toString(),
    timeZone = walk.timeZone,
    petIds = pets.map { it.petId },
    visibility = walk.visibility,
    note = walk.note,
    photoAssetIds = photoAssetIds,
    segments = segmentsJson.toNetworkSegments(),
    clientStats = NetworkWalkClientStats(walk.distanceM, walk.movingTimeS),
    updatedAt = Instant.ofEpochMilli(walk.updatedAtEpochMs).toString(),
)

internal fun NetworkWalkStats.toEntity(listKey: String) =
    WalkTotalsEntity(listKey, walkCount, distanceM, durationS, movingTimeS, weeklyStreak)

internal fun WalkTotalsEntity.toModel() = WalkTotals(walkCount, distanceM, durationS, movingTimeS, weeklyStreak)
