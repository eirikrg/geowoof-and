package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// Wire format of the API. Dates are ISO-8601 UTC strings.

@Serializable
data class NetworkUser(
    val id: String,
    val email: String,
    val emailVerified: Boolean,
    val username: String? = null,
    val displayName: String,
    val hasPassword: Boolean,
    val signInMethods: List<String> = emptyList(),
    val language: String,
    val createdAt: String,
    val isPrivate: Boolean = false,
)

@Serializable
data class NetworkSession(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val isNewUser: Boolean = false,
    val user: NetworkUser,
)

@Serializable
data class NetworkAccessToken(val accessToken: String, val accessTokenExpiresAt: String, val user: NetworkUser)

@Serializable
data class NetworkAuthProviders(val google: Boolean)

@Serializable
data class NetworkUsernameAvailability(val username: String, val available: Boolean, val reason: String? = null)

/** RFC 9457 ProblemDetails with the GeoWoof `code` extension. */
@Serializable
data class NetworkProblem(
    val status: Int? = null,
    val code: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val errors: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class RegisterBody(val email: String, val password: String, val displayName: String, val language: String? = null)

@Serializable
data class LoginBody(val email: String, val password: String)

@Serializable
data class GoogleSignInBody(val idToken: String, val language: String? = null)

@Serializable
data class RefreshBody(val refreshToken: String)

@Serializable
data class LogoutBody(val refreshToken: String? = null, val allDevices: Boolean? = null)

@Serializable
data class VerifyEmailBody(val code: String)

@Serializable
data class EmailBody(val email: String)

@Serializable
data class ResetPasswordBody(val email: String, val code: String, val newPassword: String)

@Serializable
data class ChangePasswordBody(val currentPassword: String? = null, val newPassword: String)

@Serializable
data class UpdateMeBody(val displayName: String? = null, val language: String? = null)

@Serializable
data class UsernameBody(val username: String, val isPrivate: Boolean? = null)

@Serializable
data class PrivacyBody(val isPrivate: Boolean)

@Serializable
data class DeleteAccountBody(val password: String? = null, val googleIdToken: String? = null)
