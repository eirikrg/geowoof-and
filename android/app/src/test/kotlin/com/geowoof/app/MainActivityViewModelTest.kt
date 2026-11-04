package com.geowoof.app

import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestUsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val onboarding = SessionState.SignedIn(TestUsers.user(username = null))
    private val onboarded = SessionState.SignedIn(TestUsers.user(username = "ana"))

    @Test
    fun exposes_the_session_of_the_repository() {
        val auth = FakeAuthRepository(SessionState.SignedOut)

        val viewModel = MainActivityViewModel(auth)
        auth.setSession(onboarded)

        assertEquals(onboarded, viewModel.uiState.value.sessionState)
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

    @Test
    fun finishing_the_username_step_offers_the_first_pet_until_it_is_done() {
        val auth = FakeAuthRepository(onboarding)
        val viewModel = MainActivityViewModel(auth)
        assertFalse(viewModel.uiState.value.offerFirstPet)

        auth.setSession(onboarded)
        assertTrue(viewModel.uiState.value.offerFirstPet)

        // Other session changes, such as refreshed tokens, do not end the step.
        auth.setSession(SessionState.SignedIn(TestUsers.user(username = "ana", displayName = "Ana B.")))
        assertTrue(viewModel.uiState.value.offerFirstPet)

        viewModel.onFirstPetStepDone()
        assertFalse(viewModel.uiState.value.offerFirstPet)
    }

    @Test
    fun an_account_that_was_already_set_up_is_not_asked_for_a_pet() {
        val auth = FakeAuthRepository(onboarded)

        val viewModel = MainActivityViewModel(auth)
        auth.setSession(SessionState.SignedIn(TestUsers.user(username = "ana", displayName = "Ana B.")))

        assertFalse(viewModel.uiState.value.offerFirstPet)
    }

    @Test
    fun signing_out_during_the_step_cancels_it() {
        val auth = FakeAuthRepository(onboarding)
        val viewModel = MainActivityViewModel(auth)
        auth.setSession(onboarded)

        auth.setSession(SessionState.SignedOut)

        assertFalse(viewModel.uiState.value.offerFirstPet)
    }
}
