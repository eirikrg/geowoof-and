package com.geowoof.feature.auth.signin

import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.data.credentials.PasswordCredential
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.feature.auth.enter
import com.geowoof.feature.auth.googleClientId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val passwordSaves = PasswordSaveRequests()

    private fun viewModel(arguments: Map<String, Any?> = emptyMap()) =
        SignInViewModel(SavedStateHandle(arguments), auth, ValidateEmailUseCase(), passwordSaves, googleClientId(auth))

    @Test
    fun the_route_prefills_the_email_and_the_reset_notice() {
        val viewModel = viewModel(mapOf("email" to "ana@geowoof.dev", "passwordWasReset" to true))

        assertEquals("ana@geowoof.dev", viewModel.email.text.toString())
        assertTrue(viewModel.uiState.value.passwordWasReset)
    }

    @Test
    fun invalid_fields_are_reported_without_calling_the_api() {
        val viewModel = viewModel()
        viewModel.email.enter("ana@")

        viewModel.submit()

        assertEquals(EmailIssue.Invalid, viewModel.uiState.value.emailIssue)
        assertTrue(viewModel.uiState.value.isPasswordMissing)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun a_reported_problem_disappears_once_the_field_is_fixed() {
        val viewModel = viewModel()
        viewModel.submit()

        viewModel.email.enter("ana@geowoof.dev")
        viewModel.password.enter("x")

        assertNull(viewModel.uiState.value.emailIssue)
        assertFalse(viewModel.uiState.value.isPasswordMissing)
    }

    @Test
    fun signing_in_starts_the_session_and_offers_to_save_the_password() = runTest {
        val viewModel = viewModel()
        viewModel.email.enter("ana@geowoof.dev")
        viewModel.password.enter("GeoWoof2026")

        viewModel.submit()

        assertEquals(listOf("signIn:ana@geowoof.dev"), auth.calls)
        assertTrue(auth.sessionState.value is SessionState.SignedIn)
        assertEquals(PasswordCredential("ana@geowoof.dev", "GeoWoof2026"), passwordSaves.pending.first())
    }

    @Test
    fun wrong_credentials_show_the_error_and_allow_another_attempt() {
        val error = AppError.Api(status = 401, code = "auth.invalid_credentials")
        auth.signInResult = Outcome.Failure(error)
        val viewModel = viewModel()
        viewModel.email.enter("ana@geowoof.dev")
        viewModel.password.enter("wrong-password1")

        viewModel.submit()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(SessionState.SignedOut, auth.sessionState.value)
    }

    @Test
    fun google_sign_in_uses_the_token_from_credential_manager() {
        val viewModel = viewModel()

        viewModel.signInWithGoogle("id-token")

        assertEquals(listOf("google:id-token"), auth.calls)
    }
}
