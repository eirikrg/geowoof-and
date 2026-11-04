package com.geowoof.feature.auth.verify

import com.geowoof.core.domain.ValidateVerificationCodeUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.CodeIssue
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestUsers
import com.geowoof.feature.auth.enter
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VerifyEmailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user(username = null, emailVerified = false)))
    private val viewModel by lazy { VerifyEmailViewModel(auth, ValidateVerificationCodeUseCase()) }

    @Test
    fun shows_where_the_code_was_sent() {
        assertEquals("ana@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun an_incomplete_code_is_not_sent() {
        viewModel.code.enter("123")

        viewModel.verify()

        assertEquals(CodeIssue.Incomplete, viewModel.uiState.value.codeIssue)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun a_valid_code_verifies_the_email() {
        viewModel.code.enter("123456")

        viewModel.verify()

        assertEquals(listOf("verify:123456"), auth.calls)
        assertTrue(viewModel.uiState.value.isVerified)
    }

    @Test
    fun a_wrong_code_is_explained() {
        val error = AppError.Api(status = 400, code = "auth.code_invalid")
        auth.verifyResult = Outcome.Failure(error)
        viewModel.code.enter("123456")

        viewModel.verify()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isVerified)
    }

    @Test
    fun an_email_verified_elsewhere_counts_as_verified() {
        auth.verifyResult = Outcome.Failure(AppError.Api(status = 409, code = "auth.email_already_verified"))
        viewModel.code.enter("123456")

        viewModel.verify()

        assertTrue(viewModel.uiState.value.isVerified)
    }

    @Test
    fun after_resending_a_code_the_next_one_waits_a_minute() = runTest {
        viewModel.resendCode()

        assertTrue(viewModel.uiState.value.codeResent)
        assertEquals(60, viewModel.uiState.value.resendAvailableInSeconds)
        viewModel.resendCode()
        assertEquals(1, auth.calls.count { it == "resend" })

        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(30, viewModel.uiState.value.resendAvailableInSeconds)

        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(viewModel.uiState.value.canResend)
    }

    @Test
    fun the_server_cooldown_is_respected() {
        auth.unitResult =
            Outcome.Failure(AppError.Api(status = 429, code = "auth.code_cooldown", retryAfterSeconds = 42))

        viewModel.resendCode()

        assertEquals(42, viewModel.uiState.value.resendAvailableInSeconds)
        assertFalse(viewModel.uiState.value.codeResent)
    }

    @Test
    fun signing_out_ends_the_session() {
        viewModel.signOut()

        assertEquals(SessionState.SignedOut, auth.sessionState.value)
    }
}
