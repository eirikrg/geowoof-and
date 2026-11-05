package com.geowoof.core.data.mapper

import com.geowoof.core.datastore.StoredSession
import com.geowoof.core.datastore.StoredUser
import com.geowoof.core.model.AuthProviders
import com.geowoof.core.model.SignInMethod
import com.geowoof.core.model.User
import com.geowoof.core.model.UsernameAvailability
import com.geowoof.core.model.UsernameUnavailableReason
import com.geowoof.core.network.model.NetworkAuthProviders
import com.geowoof.core.network.model.NetworkSession
import com.geowoof.core.network.model.NetworkUser
import com.geowoof.core.network.model.NetworkUsernameAvailability
import java.time.Instant

internal fun NetworkSession.toStored() = StoredSession(
    accessToken = accessToken,
    accessTokenExpiresAtEpochMs = Instant.parse(accessTokenExpiresAt).toEpochMilli(),
    refreshToken = refreshToken,
    refreshTokenExpiresAtEpochMs = Instant.parse(refreshTokenExpiresAt).toEpochMilli(),
    user = user.toStored(),
)

internal fun NetworkUser.toStored() = StoredUser(
    id = id,
    email = email,
    emailVerified = emailVerified,
    username = username,
    displayName = displayName,
    hasPassword = hasPassword,
    signInMethods = signInMethods,
    language = language,
    createdAtEpochMs = Instant.parse(createdAt).toEpochMilli(),
    isPrivate = isPrivate,
)

internal fun StoredUser.toModel() = User(
    id = id,
    email = email,
    emailVerified = emailVerified,
    username = username,
    displayName = displayName,
    hasPassword = hasPassword,
    signInMethods = signInMethods.mapNotNull { method -> SignInMethod.entries.find { it.name == method } }.toSet(),
    language = language,
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    isPrivate = isPrivate,
)

internal fun NetworkAuthProviders.toModel() = AuthProviders(google = google)

internal fun NetworkUsernameAvailability.toModel() = UsernameAvailability(
    username = username,
    available = available,
    reason = reason?.let { value -> UsernameUnavailableReason.entries.find { it.name == value } },
)
