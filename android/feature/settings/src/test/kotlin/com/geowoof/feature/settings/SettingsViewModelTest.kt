package com.geowoof.feature.settings

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAccountRepository
import com.geowoof.core.testing.FakeActiveWalkRepository
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestUsers
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = TestUsers.user()
    private val auth = FakeAuthRepository(SessionState.SignedIn(user))
    private val walks = FakeWalksRepository()
    private val activeWalks = FakeActiveWalkRepository()
    private val accounts = FakeAccountRepository()
    private val viewModel by lazy { SettingsViewModel(auth, walks, activeWalks, accounts) }

    @Test
    fun shows_the_signed_in_account() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        assertEquals(user, viewModel.uiState.value.user)
    }

    @Test
    fun signing_out_ends_the_session_once() = runTest {
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.signOut()
        viewModel.signOut()

        assertEquals(SessionState.SignedOut, auth.sessionState.value)
        assertEquals(listOf("signOut"), auth.calls)
    }

    @Test
    fun signing_out_warns_if_there_are_walks_not_uploaded() = runTest {
        walks.hasUnsynced = true
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signOut()

        assertEquals(true, viewModel.uiState.value.isConfirmingSignOut)
        assertEquals(emptyList<String>(), auth.calls)

        viewModel.dismissSignOut()
        assertEquals(false, viewModel.uiState.value.isConfirmingSignOut)
        assertEquals(emptyList<String>(), auth.calls)

        viewModel.signOut()
        viewModel.confirmSignOut()
        assertEquals(listOf("signOut"), auth.calls)
    }

    @Test
    fun signing_out_warns_during_a_walk() = runTest {
        activeWalks.set(TestWalks.activeWalk())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signOut()

        assertEquals(true, viewModel.uiState.value.isConfirmingSignOut)
    }

    @Test
    fun the_privacy_change_goes_to_the_server() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onPrivateChange(true)

        assertEquals(listOf(true), accounts.chosenPrivacy)
        assertFalse(viewModel.uiState.value.isChangingPrivacy)
    }

    @Test
    fun a_privacy_change_that_fails_is_shown() = runTest {
        accounts.setPrivateResult = Outcome.Failure(AppError.Network)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.onPrivateChange(true)

        assertEquals(AppError.Network, viewModel.uiState.value.error)
        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.error)
    }
}
