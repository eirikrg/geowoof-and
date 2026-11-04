package com.geowoof.app

import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestUsers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exposes_the_session_of_the_repository() {
        val auth = FakeAuthRepository(SessionState.SignedOut)

        val viewModel = MainActivityViewModel(auth)
        auth.setSession(SessionState.SignedIn(TestUsers.user()))

        assertEquals(SessionState.SignedIn(TestUsers.user()), viewModel.sessionState.value)
    }

    @Test
    fun refreshes_the_user_once_the_stored_session_is_read() {
        val auth = FakeAuthRepository(SessionState.Loading)
        MainActivityViewModel(auth)
        assertEquals(0, auth.refreshes)

        auth.setSession(SessionState.SignedIn(TestUsers.user()))
        auth.setSession(SessionState.SignedIn(TestUsers.user(displayName = "Ana B.")))

        assertEquals(1, auth.refreshes)
    }

    @Test
    fun does_not_refresh_without_a_session() {
        val auth = FakeAuthRepository(SessionState.SignedOut)

        MainActivityViewModel(auth)

        assertEquals(0, auth.refreshes)
    }
}
