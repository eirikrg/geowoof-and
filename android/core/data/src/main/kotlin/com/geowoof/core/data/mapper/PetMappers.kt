package com.geowoof.core.data.mapper

import com.geowoof.core.database.PetEntity
import com.geowoof.core.database.PetWeightEntity
import com.geowoof.core.database.ProfileEntity
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetDraft
import com.geowoof.core.model.PetSex
import com.geowoof.core.model.PetSize
import com.geowoof.core.model.PetSpecies
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileDraft
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.WeightEntry
import com.geowoof.core.model.toEnumOr
import com.geowoof.core.model.toEnumOrNull
import com.geowoof.core.network.model.NetworkPet
import com.geowoof.core.network.model.NetworkProfile
import com.geowoof.core.network.model.NetworkWeightEntry
import com.geowoof.core.network.model.PetBody
import com.geowoof.core.network.model.ProfileBody
import com.geowoof.core.network.model.WeightBody
import java.time.Instant
import java.time.LocalDate

internal fun NetworkProfile.toEntity() = ProfileEntity(
    id = id,
    username = username,
    displayName = displayName,
    bio = bio,
    city = city,
    avatarAssetId = avatar?.assetId,
    avatarThumbUrl = avatar?.thumbUrl,
    avatarMediumUrl = avatar?.mediumUrl,
    createdAtEpochMs = Instant.parse(createdAt).toEpochMilli(),
    isPrivate = isPrivate,
)

internal fun ProfileEntity.toModel() = Profile(
    id = id,
    username = username,
    displayName = displayName,
    bio = bio,
    city = city,
    avatar = avatarAssetId?.let { RemoteImage(it, avatarThumbUrl, avatarMediumUrl) },
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    isPrivate = isPrivate,
)

internal fun ProfileDraft.toBody() = ProfileBody(displayName.trim(), bio.cleaned(), city.cleaned(), avatarAssetId)

internal fun NetworkPet.toEntity() = PetEntity(
    id = id,
    ownerId = ownerId,
    name = name,
    species = species,
    speciesOther = speciesOther,
    breed = breed,
    birthDateEpochDay = birthDate?.let { LocalDate.parse(it).toEpochDay() },
    sex = sex,
    size = size,
    color = color,
    neutered = neutered,
    microchip = microchip,
    avatarAssetId = avatar?.assetId,
    avatarThumbUrl = avatar?.thumbUrl,
    avatarMediumUrl = avatar?.mediumUrl,
    bio = bio,
    createdAtEpochMs = Instant.parse(createdAt).toEpochMilli(),
    updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
)

/** Values from a newer API that this version does not know fall back to a neutral one. */
internal fun PetEntity.toModel() = Pet(
    id = id,
    ownerId = ownerId,
    name = name,
    species = species.toEnumOr(PetSpecies.Other),
    speciesOther = speciesOther,
    breed = breed,
    birthDate = birthDateEpochDay?.let(LocalDate::ofEpochDay),
    sex = sex.toEnumOr(PetSex.Unknown),
    size = size.toEnumOrNull<PetSize>(),
    color = color,
    neutered = neutered,
    microchip = microchip,
    avatar = avatarAssetId?.let { RemoteImage(it, avatarThumbUrl, avatarMediumUrl) },
    bio = bio,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)

internal fun PetDraft.toBody() = PetBody(
    name = name.trim(),
    species = species.name,
    speciesOther = if (species == PetSpecies.Other) speciesOther.cleaned() else null,
    breed = breed.cleaned(),
    birthDate = birthDate?.toString(),
    sex = sex.name,
    size = size?.name,
    color = color.cleaned(),
    neutered = neutered,
    microchip = microchip.cleaned(),
    avatarAssetId = avatarAssetId,
    bio = bio.cleaned(),
)

internal fun NetworkWeightEntry.toEntity(petId: String) =
    PetWeightEntity(id, petId, LocalDate.parse(measuredOn).toEpochDay(), weightKg)

internal fun PetWeightEntity.toModel() = WeightEntry(id, petId, LocalDate.ofEpochDay(measuredOnEpochDay), weightKg)

internal fun weightBody(measuredOn: LocalDate, weightKg: Double) = WeightBody(measuredOn.toString(), weightKg)

private fun String?.cleaned(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
