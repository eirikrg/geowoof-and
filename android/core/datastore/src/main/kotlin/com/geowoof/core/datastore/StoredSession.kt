package com.geowoof.core.datastore

import kotlinx.serialization.Serializable

/** The persisted session: tokens and a cached copy of the user (local source of truth). */
@Serializable
data class StoredSession(
    val accessToken: String,
    val accessTokenExpiresAtEpochMs: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtEpochMs: Long,
    val user: StoredUser,
)

@Serializable
data class StoredUser(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val username: String? = null,
    val displayName: String,
    val hasPassword: Boolean,
    val signInMethods: List<String> = emptyList(),
    val language: String,
    val createdAtEpochMs: Long,
)

/** File content: a single optional session. */
@Serializable
internal data class SessionFile(val session: StoredSession? = null)
