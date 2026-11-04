package com.geowoof.feature.settings.account

import com.geowoof.core.common.AppConfig
import com.geowoof.core.data.credentials.PasswordCredential
import com.geowoof.core.data.credentials.PasswordSaveRequests
import com.geowoof.core.domain.ValidatePasswordUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PasswordIssue
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestUsers
import com.geowoof.feature.settings.enter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AccountViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val passwordSaves = PasswordSaveRequests()
    private val config =
        AppConfig(apiBaseUrl = "http://localhost/", googleServerClientId = "web-client", logHttpTraffic = false)

    private fun auth(hasPassword: Boolean = true) =
        FakeAuthRepository(SessionState.SignedIn(TestUsers.user(hasPassword = hasPassword)))

    private fun changePassword(auth: FakeAuthRepository) =
        ChangePasswordViewModel(auth, ValidatePasswordUseCase(), passwordSaves)

    @Test
    fun changing_the_password_requires_the_current_one() {
        val auth = auth()
        val viewModel = changePassword(auth)
        viewModel.newPassword.enter("short")

        viewModel.submit()

        assertTrue(viewModel.uiState.value.isCurrentPasswordMissing)
        assertEquals(PasswordIssue.TooShort, viewModel.uiState.value.newPasswordIssue)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun a_changed_password_is_offered_to_the_password_manager() = runTest {
        val auth = auth()
        val viewModel = changePassword(auth)
        viewModel.currentPassword.enter("GeoWoof2026")
        viewModel.newPassword.enter("NewPassword1")

        viewModel.submit()

        assertEquals(listOf("changePassword:GeoWoof2026"), auth.calls)
        assertTrue(viewModel.uiState.value.isDone)
        assertEquals(PasswordCredential("ana@example.com", "NewPassword1"), passwordSaves.pending.first())
    }

    @Test
    fun google_accounts_create_a_password_without_a_current_one() {
        val auth = auth(hasPassword = false)
        val viewModel = changePassword(auth)
        viewModel.newPassword.enter("NewPassword1")

        viewModel.submit()

        assertFalse(viewModel.uiState.value.hasPassword)
        assertEquals(listOf("changePassword:null"), auth.calls)
    }

    @Test
    fun a_wrong_current_password_is_explained() {
        val auth = auth()
        val error = AppError.Api(status = 400, code = "auth.password_incorrect")
        auth.unitResult = Outcome.Failure(error)
        val viewModel = changePassword(auth)
        viewModel.currentPassword.enter("wrong")
        viewModel.newPassword.enter("NewPassword1")

        viewModel.submit()

        assertEquals(error, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isDone)
    }

    @Test
    fun deleting_asks_for_the_password_and_then_for_confirmation() {
        val auth = auth()
        val viewModel = DeleteAccountViewModel(auth, config)

        viewModel.requestDeletion()
        assertTrue(viewModel.uiState.value.isPasswordMissing)
        assertFalse(viewModel.uiState.value.isConfirming)

        viewModel.password.enter("GeoWoof2026")
        viewModel.requestDeletion()
        assertTrue(viewModel.uiState.value.isConfirming)

        viewModel.deleteWithPassword()
        assertEquals(listOf("delete:GeoWoof2026:null"), auth.calls)
        assertEquals(SessionState.SignedOut, auth.sessionState.value)
    }

    @Test
    fun google_accounts_confirm_the_deletion_with_google() {
        val auth = auth(hasPassword = false)
        val viewModel = DeleteAccountViewModel(auth, config)

        viewModel.requestDeletion()
        assertTrue(viewModel.uiState.value.isConfirming)
        assertEquals("web-client", viewModel.uiState.value.googleClientId)

        viewModel.deleteWithGoogle("id-token")
        assertEquals(listOf("delete:null:id-token"), auth.calls)
    }

    @Test
    fun a_failed_deletion_keeps_the_session() {
        val auth = auth()
        auth.unitResult = Outcome.Failure(AppError.Api(status = 400, code = "auth.password_incorrect"))
        val viewModel = DeleteAccountViewModel(auth, config)
        viewModel.password.enter("wrong")

        viewModel.deleteWithPassword()

        assertFalse(viewModel.uiState.value.isDeleting)
        assertTrue(auth.sessionState.value is SessionState.SignedIn)
    }
}
