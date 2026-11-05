package com.geowoof.core.model

import java.time.Instant
import java.time.LocalDate

/** An image of the Media module. The signed URLs are `null` while it is being processed. */
data class RemoteImage(val assetId: String, val thumbUrl: String?, val mediumUrl: String?)

/** What an image is uploaded for: the API only lets it be used for that (docs/api.md). */
enum class MediaPurpose {
    UserAvatar,
    PetAvatar,
    WalkPhoto,
    PostImage,
}

/** The public face of an account. It never includes the email. */
data class Profile(
    val id: String,
    val username: String?,
    val displayName: String,
    val bio: String?,
    val city: String?,
    val avatar: RemoteImage?,
    val createdAt: Instant,
    val isPrivate: Boolean = false,
)

/** The editable fields of the profile. Saving replaces all of them: `null` clears a field. */
data class ProfileDraft(val displayName: String, val bio: String?, val city: String?, val avatarAssetId: String?)

enum class PetSpecies {
    Dog,
    Cat,
    Other,
}

enum class PetSex {
    Male,
    Female,
    Unknown,
}

enum class PetSize {
    Small,
    Medium,
    Large,
    Giant,
}

data class Pet(
    val id: String,
    val ownerId: String,
    val name: String,
    val species: PetSpecies,
    val speciesOther: String?,
    val breed: String?,
    val birthDate: LocalDate?,
    val sex: PetSex,
    val size: PetSize?,
    val color: String?,
    val neutered: Boolean,
    /** Only the owner receives it. */
    val microchip: String?,
    val avatar: RemoteImage?,
    val bio: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** The editable fields of a pet. Saving replaces all of them: `null` clears a field. */
data class PetDraft(
    val name: String,
    val species: PetSpecies,
    val speciesOther: String?,
    val breed: String?,
    val birthDate: LocalDate?,
    val sex: PetSex,
    val size: PetSize?,
    val color: String?,
    val neutered: Boolean,
    val microchip: String?,
    val avatarAssetId: String?,
    val bio: String?,
)

/** One weight measurement. There is at most one per day. */
data class WeightEntry(val id: String, val petId: String, val measuredOn: LocalDate, val weightKg: Double)
