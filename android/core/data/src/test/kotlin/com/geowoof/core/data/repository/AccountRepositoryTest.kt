package com.geowoof.core.data.repository

import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.UsernameAvailability
import com.geowoof.core.model.UsernameUnavailableReason
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountRepositoryTest {
    private val network = FakeAuthNetworkDataSource()
    private val storage = InMemorySessionStorage()

    private suspend fun TestScope.signedIn(): Pair<DefaultAccountRepository, SessionManager> {
        val sessions = SessionManager(storage, NoUserData, backgroundScope)
        runCurrent()
        sessions.start(networkSession(user = networkUser(username = null)))
        runCurrent()
        return DefaultAccountRepository(network, sessions) to sessions
    }

    private fun SessionManager.user() = (sessionState.value as SessionState.SignedIn).user

    @Test
    fun availability_maps_the_reason() = runTest {
        val (accounts, _) = signedIn()

        assertEquals(
            Outcome.Success(
                UsernameAvailability("admin", available = false, reason = UsernameUnavailableReason.Reserved),
            ),
            accounts.usernameAvailability("admin"),
        )
    }

    @Test
    fun choosing_a_username_ends_the_onboarding() = runTest {
        val (accounts, sessions) = signedIn()
        assertEquals(true, sessions.user().needsOnboarding)
        network.userResult = Outcome.Success(networkUser(username = "ana.paws"))

        accounts.chooseUsername("ana.paws")
        runCurrent()

        assertEquals(false, sessions.user().needsOnboarding)
        assertEquals("ana.paws", storage.session.value?.user?.username)
    }

    @Test
    fun the_privacy_chosen_with_the_username_reaches_the_server_and_the_session() = runTest {
        val (accounts, sessions) = signedIn()
        network.userResult = Outcome.Success(networkUser(username = "ana.paws").copy(isPrivate = true))

        accounts.chooseUsername("ana.paws", isPrivate = true)
        runCurrent()

        assertEquals(true, network.calls.contains("username:ana.paws:true"))
        assertEquals(true, sessions.user().isPrivate)
    }

    @Test
    fun the_profile_can_change_who_sees_it_and_the_session_keeps_the_answer() = runTest {
        val (accounts, sessions) = signedIn()
        network.userResult = Outcome.Success(networkUser().copy(isPrivate = true))

        accounts.setPrivate(true)
        runCurrent()

        assertEquals(true, network.calls.contains("privacy:true"))
        assertEquals(true, sessions.user().isPrivate)
        assertEquals(true, storage.session.value?.user?.isPrivate)
    }

    @Test
    fun a_change_of_privacy_that_fails_keeps_what_there_was() = runTest {
        val (accounts, sessions) = signedIn()
        network.userResult = Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Failure(AppError.Network), accounts.setPrivate(true))
        runCurrent()

        assertEquals(false, sessions.user().isPrivate)
    }

    @Test
    fun a_taken_username_keeps_the_onboarding() = runTest {
        val (accounts, sessions) = signedIn()
        val error = AppError.Api(status = 409, code = "identity.username_taken")
        network.userResult = Outcome.Failure(error)

        assertEquals(Outcome.Failure(error), accounts.chooseUsername("ana"))
        runCurrent()

        assertEquals(true, sessions.user().needsOnboarding)
    }

    @Test
    fun the_display_name_is_trimmed_and_cached() = runTest {
        val (accounts, sessions) = signedIn()
        network.userResult = Outcome.Success(networkUser(username = null, displayName = "Ana B."))

        accounts.updateDisplayName("  Ana B.  ")
        runCurrent()

        assertEquals("updateProfile:Ana B.:null", network.calls.last())
        assertEquals("Ana B.", sessions.user().displayName)
    }
}
