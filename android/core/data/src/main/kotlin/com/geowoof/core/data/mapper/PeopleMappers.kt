package com.geowoof.core.data.mapper

import com.geowoof.core.model.Person
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.Relationship
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.UserWalk
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkPhoto
import com.geowoof.core.model.WalkStats
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.model.WalkVisibility
import com.geowoof.core.network.model.NetworkCounts
import com.geowoof.core.network.model.NetworkPerson
import com.geowoof.core.network.model.NetworkPetSearchResult
import com.geowoof.core.network.model.NetworkRelationship
import com.geowoof.core.network.model.NetworkWalk
import com.geowoof.core.network.model.NetworkWalkStats
import java.time.Instant

// What comes from the server about other people and is not kept on the device.

/** A value from a newer API that this version does not know counts as no relationship. */
internal fun NetworkRelationship.toModel() = Relationship(
    status = RelationshipStatus.entries.find { it.name == status } ?: RelationshipStatus.None,
    followsYou = followsYou,
    requestId = requestId,
    incomingRequestId = incomingRequestId,
)

internal fun NetworkCounts.toModel() = ProfileCounts(followers, following, friends)

internal fun NetworkPerson.toModel() = Person(user.toModel(), Instant.parse(since))

internal fun NetworkWalkStats.toModel() = WalkTotals(walkCount, distanceM, durationS, movingTimeS, weeklyStreak)

internal fun NetworkPetSearchResult.toModel() = PetSearchResult(
    id = id,
    name = name,
    species = PetSpecies.entries.find { it.name == species } ?: PetSpecies.Other,
    speciesOther = speciesOther,
    breed = breed,
    avatar = avatar?.toModel(),
    owner = owner.toModel(),
)

internal fun NetworkWalk.toModel() = Walk(
    id = id,
    startedAt = Instant.parse(startedAt),
    endedAt = Instant.parse(endedAt),
    timeZone = timeZone,
    petIds = petIds,
    visibility = WalkVisibility.entries.find { it.name == visibility } ?: WalkVisibility.OnlyMe,
    note = note,
    stats = WalkStats(distanceM, durationS, movingTimeS, averageSpeedMps, paceSecondsPerKm),
    previewPolylines = previewPolylines,
    photos = photos.map { WalkPhoto(image = it.toModel(), localPath = null) },
    syncStatus = WalkSyncStatus.Synced,
    syncErrorCode = null,
    updatedAt = Instant.parse(updatedAt),
)

internal fun NetworkWalk.toUserWalk() = UserWalk(toModel(), ownerId, segments.orEmpty().toRouteSegments())
