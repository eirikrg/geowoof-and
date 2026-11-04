package com.geowoof.core.data.repository

import com.geowoof.core.datastore.SessionStorage
import com.geowoof.core.datastore.StoredSession
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.AuthNetworkDataSource
import com.geowoof.core.network.model.NetworkAccessToken
import com.geowoof.core.network.model.NetworkAuthProviders
import com.geowoof.core.network.model.NetworkSession
import com.geowoof.core.network.model.NetworkUser
import com.geowoof.core.network.model.NetworkUsernameAvailability
import kotlinx.coroutines.flow.MutableStateFlow

internal fun networkUser(username: String? = "ana", emailVerified: Boolean = true, displayName: String = "Ana") =
    NetworkUser(
        id = "0193f0c2-6f1e-7cc4-9a2b-3c1d5e7f9a10",
        email = "ana@geowoof.dev",
        emailVerified = emailVerified,
        username = username,
        displayName = displayName,
        hasPassword = true,
        signInMethods = listOf("Password", "SomethingNew"),
        language = "es",
        createdAt = "2026-11-01T10:00:00Z",
    )

internal fun networkSession(
    accessToken: String = "access-1",
    refreshToken: String = "refresh-1",
    user: NetworkUser = networkUser(),
) = NetworkSession(
    accessToken = accessToken,
    accessTokenExpiresAt = "2026-11-03T10:15:00Z",
    refreshToken = refreshToken,
    refreshTokenExpiresAt = "2026-12-03T10:00:00Z",
    user = user,
)

internal class InMemorySessionStorage(initial: StoredSession? = null) : SessionStorage {
    override val session = MutableStateFlow(initial)

    override suspend fun read(): StoredSession? = session.value

    override suspend fun write(session: StoredSession?) {
        this.session.value = session
    }
}

/** Answers each call with the outcome set by the test and records its relevant arguments. */
internal class FakeAuthNetworkDataSource : AuthNetworkDataSource {
    var sessionResult: Outcome<NetworkSession> = Outcome.Success(networkSession())
    var unitResult: Outcome<Unit> = Outcome.Success(Unit)
    var userResult: Outcome<NetworkUser> = Outcome.Success(networkUser())
    var accessTokenResult: Outcome<NetworkAccessToken> = Outcome.Success(
        NetworkAccessToken("access-2", "2026-11-03T10:30:00Z", networkUser()),
    )
    var availabilityResult: Outcome<NetworkUsernameAvailability> =
        Outcome.Success(NetworkUsernameAvailability("admin", available = false, reason = "Reserved"))
    val calls = mutableListOf<String>()

    override suspend fun providers(): Outcome<NetworkAuthProviders> =
        Outcome.Success(NetworkAuthProviders(google = true))

    override suspend fun register(email: String, password: String, displayName: String, language: String?) =
        sessionResult.also { calls += "register:$email:$displayName:$language" }

    override suspend fun signIn(email: String, password: String) = sessionResult.also { calls += "signIn:$email" }

    override suspend fun signInWithGoogle(idToken: String, language: String?) =
        sessionResult.also { calls += "google:$idToken:$language" }

    override suspend fun signOut(refreshToken: String?) = unitResult.also { calls += "signOut:$refreshToken" }

    override suspend fun resendEmailVerification() = unitResult.also { calls += "resend" }

    override suspend fun verifyEmail(code: String) = accessTokenResult.also { calls += "verify:$code" }

    override suspend fun requestPasswordReset(email: String) = unitResult.also { calls += "forgot:$email" }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        unitResult.also { calls += "reset:$email:$code" }

    override suspend fun changePassword(currentPassword: String?, newPassword: String) =
        sessionResult.also { calls += "changePassword:$currentPassword" }

    override suspend fun deleteAccount(password: String?, googleIdToken: String?) =
        unitResult.also { calls += "delete:$password:$googleIdToken" }

    override suspend fun currentUser() = userResult.also { calls += "me" }

    override suspend fun updateProfile(displayName: String?, language: String?) =
        userResult.also { calls += "updateProfile:$displayName:$language" }

    override suspend fun changeUsername(username: String) = userResult.also { calls += "username:$username" }

    override suspend fun usernameAvailability(username: String) = availabilityResult.also {
        calls +=
            "availability:$username"
    }
}
