package com.geowoof.feature.settings

import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestUsers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val user = TestUsers.user()
    private val auth = FakeAuthRepository(SessionState.SignedIn(user))
    private val viewModel by lazy { SettingsViewModel(auth) }

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
}
