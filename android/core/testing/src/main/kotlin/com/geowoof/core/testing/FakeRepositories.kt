package com.geowoof.core.testing

import com.geowoof.core.data.repository.AccountRepository
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.model.AuthProviders
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User
import com.geowoof.core.model.UsernameAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Configurable [AuthRepository]: each operation returns the outcome set by the test and records
 * its arguments. Successful sign-ins update [sessionState] like the real repository.
 */
class FakeAuthRepository(initialState: SessionState = SessionState.SignedOut) : AuthRepository {
    private val state = MutableStateFlow(initialState)
    override val sessionState: StateFlow<SessionState> = state

    var providers: Outcome<AuthProviders> = Outcome.Success(AuthProviders(google = false))
    var signInResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var registerResult: Outcome<User> = Outcome.Success(TestUsers.user(username = null, emailVerified = false))
    var googleResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var verifyResult: Outcome<User> = Outcome.Success(TestUsers.user(emailVerified = true))
    var unitResult: Outcome<Unit> = Outcome.Success(Unit)

    val calls = mutableListOf<String>()
    var refreshes = 0
        private set

    fun setSession(sessionState: SessionState) {
        state.value = sessionState
    }

    override suspend fun authProviders() = providers

    override suspend fun register(email: String, password: String, displayName: String) =
        record("register:$email:$displayName", registerResult)

    override suspend fun signIn(email: String, password: String) = record("signIn:$email", signInResult)

    override suspend fun signInWithGoogle(idToken: String) = record("google:$idToken", googleResult)

    override suspend fun signOut() {
        calls += "signOut"
        state.value = SessionState.SignedOut
    }

    override suspend fun resendVerificationEmail() = recordUnit("resend")

    override suspend fun verifyEmail(code: String) = record("verify:$code", verifyResult)

    override suspend fun requestPasswordReset(email: String) = recordUnit("forgot:$email")

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        recordUnit("reset:$email:$code")

    override suspend fun changePassword(currentPassword: String?, newPassword: String) =
        recordUnit("changePassword:$currentPassword")

    override suspend fun deleteAccount(password: String?, googleIdToken: String?): Outcome<Unit> {
        val outcome = recordUnit("delete:$password:$googleIdToken")
        if (outcome is Outcome.Success) {
            state.value = SessionState.SignedOut
        }

        return outcome
    }

    override suspend fun refreshUser(): Outcome<User> {
        refreshes++
        return (state.value as? SessionState.SignedIn)?.let { Outcome.Success(it.user) }
            ?: Outcome.Success(TestUsers.user())
    }

    private fun record(call: String, outcome: Outcome<User>): Outcome<User> {
        calls += call
        if (outcome is Outcome.Success) {
            state.value = SessionState.SignedIn(outcome.value)
        }

        return outcome
    }

    private fun recordUnit(call: String): Outcome<Unit> {
        calls += call
        return unitResult
    }
}

class FakeAccountRepository : AccountRepository {
    var availability: Outcome<UsernameAvailability> = Outcome.Success(
        UsernameAvailability("ana", available = true, reason = null),
    )
    var chooseResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var updateResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var availabilityChecks = 0
        private set
    val chosenUsernames = mutableListOf<String>()
    val displayNames = mutableListOf<String>()

    override suspend fun usernameAvailability(username: String): Outcome<UsernameAvailability> {
        availabilityChecks++
        return availability
    }

    override suspend fun chooseUsername(username: String): Outcome<User> {
        chosenUsernames += username
        return chooseResult
    }

    override suspend fun updateDisplayName(displayName: String): Outcome<User> {
        displayNames += displayName
        return updateResult
    }
}
