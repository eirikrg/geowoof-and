package com.geowoof.core.data.repository

import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.SignInMethod
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AuthRepositoryTest {
    private val network = FakeAuthNetworkDataSource()
    private val storage = InMemorySessionStorage()
    private val originalLocale = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(originalLocale)

    private fun TestScope.repository(): DefaultAuthRepository {
        val repository = DefaultAuthRepository(network, SessionManager(storage, backgroundScope))
        runCurrent()
        return repository
    }

    private fun DefaultAuthRepository.signedInUser() = (sessionState.value as SessionState.SignedIn).user

    @Test
    fun signing_in_stores_the_session_and_exposes_the_user() = runTest {
        val repository = repository()
        assertEquals(SessionState.SignedOut, repository.sessionState.value)

        val outcome = repository.signIn(" ana@geowoof.dev ", "GeoWoof2026")
        runCurrent()

        assertEquals(listOf("signIn:ana@geowoof.dev"), network.calls)
        assertEquals("ana@geowoof.dev", (outcome as Outcome.Success).value.email)
        assertEquals("ana@geowoof.dev", repository.signedInUser().email)
        assertEquals("refresh-1", storage.session.value?.refreshToken)
    }

    @Test
    fun unknown_sign_in_methods_from_a_newer_api_are_ignored() = runTest {
        val repository = repository()

        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()

        assertEquals(setOf(SignInMethod.Password), repository.signedInUser().signInMethods)
    }

    @Test
    fun a_rejected_sign_in_leaves_no_session() = runTest {
        val error = AppError.Api(status = 401, code = "auth.invalid_credentials")
        network.sessionResult = Outcome.Failure(error)
        val repository = repository()

        val outcome = repository.signIn("ana@geowoof.dev", "wrong")
        runCurrent()

        assertEquals(Outcome.Failure(error), outcome)
        assertEquals(SessionState.SignedOut, repository.sessionState.value)
        assertNull(storage.session.value)
    }

    @Test
    fun a_stored_session_survives_a_restart() = runTest {
        repository().signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()

        val restarted = repository()

        assertEquals("ana@geowoof.dev", restarted.signedInUser().email)
    }

    @Test
    fun registering_normalises_the_input_and_sends_the_device_language() = runTest {
        Locale.setDefault(Locale.ENGLISH)
        val repository = repository()

        repository.register(" ana@geowoof.dev ", "GeoWoof2026", " Ana ")

        assertEquals(listOf("register:ana@geowoof.dev:Ana:en"), network.calls)
    }

    @Test
    fun other_device_languages_get_emails_in_spanish() = runTest {
        Locale.setDefault(Locale.FRENCH)
        val repository = repository()

        repository.signInWithGoogle("id-token")

        assertEquals(listOf("google:id-token:es"), network.calls)
    }

    @Test
    fun signing_out_revokes_the_refresh_token_and_clears_the_session() = runTest {
        val repository = repository()
        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()

        repository.signOut()
        runCurrent()

        assertEquals("signOut:refresh-1", network.calls.last())
        assertEquals(SessionState.SignedOut, repository.sessionState.value)
        assertNull(storage.session.value)
    }

    @Test
    fun signing_out_works_without_a_connection() = runTest {
        val repository = repository()
        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()
        network.unitResult = Outcome.Failure(AppError.Network)

        repository.signOut()
        runCurrent()

        assertEquals(SessionState.SignedOut, repository.sessionState.value)
    }

    @Test
    fun verifying_the_email_replaces_the_access_token_and_keeps_the_refresh_token() = runTest {
        val repository = repository()
        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()

        repository.verifyEmail(" 123456 ")
        runCurrent()

        assertEquals("verify:123456", network.calls.last())
        assertEquals("access-2", storage.session.value?.accessToken)
        assertEquals("refresh-1", storage.session.value?.refreshToken)
    }

    @Test
    fun changing_the_password_replaces_the_whole_session() = runTest {
        val repository = repository()
        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()
        network.sessionResult = Outcome.Success(networkSession(accessToken = "access-3", refreshToken = "refresh-3"))

        val outcome = repository.changePassword("GeoWoof2026", "NewPassword1")
        runCurrent()

        assertEquals(Outcome.Success(Unit), outcome)
        assertEquals("refresh-3", storage.session.value?.refreshToken)
        assertTrue(repository.sessionState.value is SessionState.SignedIn)
    }

    @Test
    fun deleting_the_account_ends_the_session_only_when_the_api_accepts() = runTest {
        val repository = repository()
        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()

        network.unitResult = Outcome.Failure(AppError.Api(status = 400, code = "auth.password_incorrect"))
        repository.deleteAccount("wrong", null)
        runCurrent()
        assertTrue(repository.sessionState.value is SessionState.SignedIn)

        network.unitResult = Outcome.Success(Unit)
        repository.deleteAccount("GeoWoof2026", null)
        runCurrent()
        assertEquals(SessionState.SignedOut, repository.sessionState.value)
    }

    @Test
    fun refreshing_the_user_updates_the_cached_profile() = runTest {
        val repository = repository()
        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()
        network.userResult = Outcome.Success(networkUser(displayName = "Ana B."))

        repository.refreshUser()
        runCurrent()

        assertEquals("Ana B.", repository.signedInUser().displayName)
    }

    @Test
    fun a_failed_refresh_keeps_the_cached_user() = runTest {
        val repository = repository()
        repository.signIn("ana@geowoof.dev", "GeoWoof2026")
        runCurrent()
        network.userResult = Outcome.Failure(AppError.Network)

        repository.refreshUser()
        runCurrent()

        assertEquals("Ana", repository.signedInUser().displayName)
    }
}
