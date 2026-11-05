package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// Wire format of the API (docs/api.md, phase 2). Plain dates are ISO-8601 strings without time.

@Serializable
data class NetworkImage(val assetId: String, val thumbUrl: String? = null, val mediumUrl: String? = null)

@Serializable
data class NetworkProfile(
    val id: String,
    val username: String? = null,
    val displayName: String,
    val bio: String? = null,
    val city: String? = null,
    val avatar: NetworkImage? = null,
    val createdAt: String,
    val isPrivate: Boolean = false,
)

@Serializable
data class ProfileBody(val displayName: String, val bio: String?, val city: String?, val avatarAssetId: String?)

@Serializable
data class NetworkPet(
    val id: String,
    val ownerId: String,
    val name: String,
    val species: String,
    val speciesOther: String? = null,
    val breed: String? = null,
    val birthDate: String? = null,
    val sex: String,
    val size: String? = null,
    val color: String? = null,
    val neutered: Boolean,
    val microchip: String? = null,
    val avatar: NetworkImage? = null,
    val bio: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class PetBody(
    val name: String,
    val species: String,
    val speciesOther: String?,
    val breed: String?,
    val birthDate: String?,
    val sex: String,
    val size: String?,
    val color: String?,
    val neutered: Boolean,
    val microchip: String?,
    val avatarAssetId: String?,
    val bio: String?,
)

@Serializable
data class NetworkWeightEntry(val id: String, val measuredOn: String, val weightKg: Double)

@Serializable
data class WeightBody(val measuredOn: String, val weightKg: Double)

/** A page of a list (docs/api.md, section 3). */
@Serializable
data class NetworkPage<T>(val items: List<T>, val nextCursor: String? = null)

@Serializable
data class UploadRequestBody(val contentType: String, val sizeBytes: Long, val purpose: String)

@Serializable
data class NetworkMediaUpload(
    val assetId: String,
    val uploadUrl: String,
    val uploadHeaders: Map<String, String> = emptyMap(),
    val expiresAt: String,
)

@Serializable
data class NetworkMediaAsset(
    val id: String,
    val status: String,
    val thumbUrl: String? = null,
    val mediumUrl: String? = null,
    val rejectionCode: String? = null,
)
