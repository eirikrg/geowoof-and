package com.geowoof.feature.auth.password

import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.domain.ValidateEmailUseCase
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.domain.ValidateVerificationCodeUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.CodeIssue
import com.geowoof.core.model.EmailIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PasswordIssue
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.feature.auth.enter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PasswordRecoveryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()

    private fun forgot(email: String = "") =
        ForgotPasswordViewModel(SavedStateHandle(mapOf("email" to email)), auth, ValidateEmailUseCase())

    private fun reset() = ResetPasswordViewModel(
        SavedStateHandle(mapOf("email" to "ana@geowoof.dev")),
        auth,
        ValidateVerificationCodeUseCase(),
        ValidatePasswordUseCase(),
    )

    @Test
    fun requesting_a_code_moves_on_with_the_normalised_email() {
        val viewModel = forgot(email = " ana@geowoof.dev ")

        viewModel.submit()

        assertEquals(listOf("forgot:ana@geowoof.dev"), auth.calls)
        assertEquals("ana@geowoof.dev", viewModel.uiState.value.codeSentTo)
        viewModel.onCodeSentHandled()
        assertNull(viewModel.uiState.value.codeSentTo)
    }

    @Test
    fun an_invalid_email_does_not_request_a_code() {
        val viewModel = forgot()
        viewModel.email.enter("ana")

        viewModel.submit()

        assertEquals(EmailIssue.Invalid, viewModel.uiState.value.emailIssue)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun resetting_checks_the_code_and_the_new_password_first() {
        val viewModel = reset()
        viewModel.code.enter("123")
        viewModel.newPassword.enter("password")

        viewModel.submit()

        assertEquals(CodeIssue.Incomplete, viewModel.uiState.value.codeIssue)
        assertEquals(PasswordIssue.MissingDigit, viewModel.uiState.value.passwordIssue)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun a_valid_code_changes_the_password() {
        val viewModel = reset()
        viewModel.code.enter("123456")
        viewModel.newPassword.enter("NewPassword1")

        viewModel.submit()

        assertEquals(listOf("reset:ana@geowoof.dev:123456"), auth.calls)
        assertTrue(viewModel.uiState.value.isDone)
    }

    @Test
    fun an_expired_code_is_explained() {
        val error = AppError.Api(status = 400, code = "auth.code_expired")
        auth.unitResult = Outcome.Failure(error)
        val viewModel = reset()
        viewModel.code.enter("123456")
        viewModel.newPassword.enter("NewPassword1")

        viewModel.submit()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDone)
    }

    @Test
    fun a_new_code_can_be_requested_from_the_reset_form() {
        val viewModel = reset()

        viewModel.resendCode()

        assertEquals(listOf("forgot:ana@geowoof.dev"), auth.calls)
        assertTrue(viewModel.uiState.value.codeResent)
    }
}
