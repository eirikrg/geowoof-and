package com.geowoof.feature.auth.signup

import com.geowoof.core.data.credentials.PasswordCredential
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.domain.ValidateDisplayNameUseCase
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PasswordIssue
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.feature.auth.enter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignUpViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val passwordSaves = PasswordSaveRequests()
    private val viewModel by lazy {
        SignUpViewModel(
            auth,
            ValidateDisplayNameUseCase(),
            ValidateEmailUseCase(),
            ValidatePasswordUseCase(),
            passwordSaves,
        )
    }

    @Test
    fun every_invalid_field_is_reported_at_once() {
        viewModel.email.enter("ana")
        viewModel.password.enter("password")

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(DisplayNameIssue.Empty, state.displayNameIssue)
        assertEquals(EmailIssue.Invalid, state.emailIssue)
        assertEquals(PasswordIssue.MissingDigit, state.passwordIssue)
        assertFalse(state.isSubmitting)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun fixing_a_field_clears_its_problem() {
        viewModel.password.enter("password")
        viewModel.submit()

        viewModel.password.enter("password1")

        assertNull(viewModel.uiState.value.passwordIssue)
    }

    @Test
    fun creating_the_account_starts_the_session_and_offers_to_save_the_password() = runTest {
        viewModel.displayName.enter("Ana")
        viewModel.email.enter("ana@geowoof.dev")
        viewModel.password.enter("GeoWoof2026")

        viewModel.submit()

        assertEquals(listOf("register:ana@geowoof.dev:Ana"), auth.calls)
        assertTrue(auth.sessionState.value is SessionState.SignedIn)
        assertEquals(PasswordCredential("ana@geowoof.dev", "GeoWoof2026"), passwordSaves.pending.first())
    }

    @Test
    fun an_email_in_use_is_explained() {
        val error = AppError.Api(status = 409, code = "auth.email_taken")
        auth.registerResult = Outcome.Failure(error)
        viewModel.displayName.enter("Ana")
        viewModel.email.enter("ana@geowoof.dev")
        viewModel.password.enter("GeoWoof2026")

        viewModel.submit()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}
