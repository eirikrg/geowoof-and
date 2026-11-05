package com.geowoof.app

import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestUsers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MainActivityViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val social = FakeSocialRepository()
    private val onboarding = SessionState.SignedIn(TestUsers.user(username = null))
    private val onboarded = SessionState.SignedIn(TestUsers.user(username = "ana"))

    @Test
    fun exposes_the_session_of_the_repository() {
        val auth = FakeAuthRepository(SessionState.SignedOut)

        val viewModel = MainActivityViewModel(auth, social)
        auth.setSession(onboarded)

        assertEquals(onboarded, viewModel.uiState.value.sessionState)
    }

    @Test
    fun refreshes_the_user_once_the_stored_session_is_read() {
        val auth = FakeAuthRepository(SessionState.Loading)
        MainActivityViewModel(auth, social)
        assertEquals(0, auth.refreshes)

        auth.setSession(SessionState.SignedIn(TestUsers.user()))
        auth.setSession(SessionState.SignedIn(TestUsers.user(displayName = "Ana B.")))

        assertEquals(1, auth.refreshes)
    }

    @Test
    fun does_not_refresh_without_a_session() {
        val auth = FakeAuthRepository(SessionState.SignedOut)

        MainActivityViewModel(auth, social)

        assertEquals(0, auth.refreshes)
    }

    @Test
    fun finishing_the_username_step_offers_the_first_pet_until_it_is_done() {
        val auth = FakeAuthRepository(onboarding)
        val viewModel = MainActivityViewModel(auth, social)
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

        val viewModel = MainActivityViewModel(auth, social)
        auth.setSession(SessionState.SignedIn(TestUsers.user(username = "ana", displayName = "Ana B.")))

        assertFalse(viewModel.uiState.value.offerFirstPet)
    }

    @Test
    fun signing_out_during_the_step_cancels_it() {
        val auth = FakeAuthRepository(onboarding)
        val viewModel = MainActivityViewModel(auth, social)
        auth.setSession(onboarded)

        auth.setSession(SessionState.SignedOut)

        assertFalse(viewModel.uiState.value.offerFirstPet)
    }

    @Test
    fun the_pending_requests_reach_the_navigation_bar() {
        val viewModel = MainActivityViewModel(FakeAuthRepository(onboarded), social)

        social.receivedRequests.value = listOf(TestSocial.request("1"), TestSocial.request("2"))

        assertEquals(2, viewModel.uiState.value.pendingRequests)
    }

    @Test
    fun the_requests_refresh_when_a_person_finishes_signing_in_and_each_time_they_come_back() {
        val auth = FakeAuthRepository(SessionState.SignedOut)
        val viewModel = MainActivityViewModel(auth, social)
        assertEquals(emptyList<String>(), social.calls)

        auth.setSession(onboarding)
        assertEquals(emptyList<String>(), social.calls)

        auth.setSession(onboarded)
        assertEquals(listOf("refreshRequests"), social.calls)

        viewModel.onAppVisible()
        assertEquals(listOf("refreshRequests", "refreshRequests"), social.calls)
    }

    @Test
    fun signed_out_coming_back_asks_nothing() {
        val viewModel = MainActivityViewModel(FakeAuthRepository(SessionState.SignedOut), social)

        viewModel.onAppVisible()

        assertEquals(emptyList<String>(), social.calls)
    }
}
