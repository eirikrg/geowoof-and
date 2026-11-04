package com.geowoof.feature.auth.welcome

import com.geowoof.core.model.AppError
import com.geowoof.core.model.AuthProviders
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.feature.auth.googleClientId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WelcomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository().apply { providers = Outcome.Success(AuthProviders(google = true)) }

    @Test
    fun hides_google_when_the_build_has_no_client_id() {
        assertNull(WelcomeViewModel(auth, googleClientId(auth, clientId = null)).uiState.value.googleClientId)
    }

    @Test
    fun offers_google_when_it_is_configured() {
        assertEquals(
            "web-client",
            WelcomeViewModel(auth, googleClientId(auth, "web-client")).uiState.value.googleClientId,
        )
    }

    @Test
    fun signing_in_with_google_starts_the_session() {
        val viewModel = WelcomeViewModel(auth, googleClientId(auth, "web-client"))

        viewModel.signInWithGoogle("id-token")

        assertEquals(listOf("google:id-token"), auth.calls)
        assertTrue(auth.sessionState.value is SessionState.SignedIn)
    }

    @Test
    fun a_rejected_google_token_shows_the_error() {
        val error = AppError.Api(status = 401, code = "auth.google_token_invalid")
        auth.googleResult = Outcome.Failure(error)
        val viewModel = WelcomeViewModel(auth, googleClientId(auth, "web-client"))

        viewModel.signInWithGoogle("id-token")

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSigningInWithGoogle)
    }

    @Test
    fun a_device_without_a_google_account_is_explained() {
        val viewModel = WelcomeViewModel(auth, googleClientId(auth, "web-client"))

        viewModel.onGoogleUnavailable()

        assertEquals(AppError.CredentialUnavailable, viewModel.uiState.value.error)
    }
}
