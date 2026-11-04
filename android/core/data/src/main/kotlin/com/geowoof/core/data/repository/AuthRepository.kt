package com.geowoof.core.data.repository

import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.mapper.toStored
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.model.AuthProviders
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User
import com.geowoof.core.model.map
import com.geowoof.core.network.AuthNetworkDataSource
import com.geowoof.core.network.model.NetworkSession
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/** Session and credentials of the user (docs/api.md, phase 1). */
interface AuthRepository {
    /** Survives process death: restored from the encrypted session file. */
    val sessionState: StateFlow<SessionState>

    suspend fun authProviders(): Outcome<AuthProviders>

    suspend fun register(email: String, password: String, displayName: String): Outcome<User>

    suspend fun signIn(email: String, password: String): Outcome<User>

    suspend fun signInWithGoogle(idToken: String): Outcome<User>

    /** Always signs out locally, even when the server cannot be reached. */
    suspend fun signOut()

    suspend fun resendVerificationEmail(): Outcome<Unit>

    suspend fun verifyEmail(code: String): Outcome<User>

    suspend fun requestPasswordReset(email: String): Outcome<Unit>

    suspend fun resetPassword(email: String, code: String, newPassword: String): Outcome<Unit>

    suspend fun changePassword(currentPassword: String?, newPassword: String): Outcome<Unit>

    suspend fun deleteAccount(password: String?, googleIdToken: String?): Outcome<Unit>

    /** Refreshes the cached user from the server. */
    suspend fun refreshUser(): Outcome<User>
}

internal class DefaultAuthRepository @Inject constructor(
    private val network: AuthNetworkDataSource,
    private val sessions: SessionManager,
) : AuthRepository {
    override val sessionState: StateFlow<SessionState> = sessions.sessionState

    override suspend fun authProviders() = network.providers().map { it.toModel() }

    override suspend fun register(email: String, password: String, displayName: String) =
        startSession(network.register(email.trim(), password, displayName.trim(), deviceLanguage()))

    override suspend fun signIn(email: String, password: String) = startSession(network.signIn(email.trim(), password))

    override suspend fun signInWithGoogle(idToken: String) =
        startSession(network.signInWithGoogle(idToken, deviceLanguage()))

    override suspend fun signOut() {
        network.signOut(sessions.refreshToken())
        sessions.clear()
    }

    override suspend fun resendVerificationEmail() = network.resendEmailVerification()

    override suspend fun verifyEmail(code: String): Outcome<User> =
        when (val outcome = network.verifyEmail(code.trim())) {
            is Outcome.Success -> {
                val user = outcome.value.user.toStored()
                sessions.updateAccessToken(
                    outcome.value.accessToken,
                    Instant.parse(outcome.value.accessTokenExpiresAt).toEpochMilli(),
                    user,
                )
                Outcome.Success(user.toModel())
            }

            is Outcome.Failure -> outcome
        }

    override suspend fun requestPasswordReset(email: String) = network.requestPasswordReset(email.trim())

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        network.resetPassword(email.trim(), code.trim(), newPassword)

    override suspend fun changePassword(currentPassword: String?, newPassword: String) =
        startSession(network.changePassword(currentPassword, newPassword)).map { }

    override suspend fun deleteAccount(password: String?, googleIdToken: String?): Outcome<Unit> {
        val outcome = network.deleteAccount(password, googleIdToken)
        if (outcome is Outcome.Success) {
            sessions.clear()
        }

        return outcome
    }

    override suspend fun refreshUser(): Outcome<User> = when (val outcome = network.currentUser()) {
        is Outcome.Success -> {
            val user = outcome.value.toStored()
            sessions.updateUser(user)
            Outcome.Success(user.toModel())
        }

        is Outcome.Failure -> outcome
    }

    private suspend fun startSession(outcome: Outcome<NetworkSession>): Outcome<User> = when (outcome) {
        is Outcome.Success -> {
            sessions.start(outcome.value)
            Outcome.Success(outcome.value.user.toStored().toModel())
        }

        is Outcome.Failure -> outcome
    }

    /** Language of emails sent by the backend: Spanish or English. */
    private fun deviceLanguage(): String = if (Locale.getDefault().language == "en") "en" else "es"
}
