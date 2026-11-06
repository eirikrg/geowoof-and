package com.geowoof.app

import com.geowoof.core.model.SessionState
import com.geowoof.core.notifications.PushRegistrar
import com.geowoof.core.testing.FakeAppearanceRepository
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeChatRepository
import com.geowoof.core.testing.FakeNotificationsRepository
import com.geowoof.core.testing.FakePushTokenProvider
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestChat
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestUsers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
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
    private val chat = FakeChatRepository()
    private val notifications = FakeNotificationsRepository()
    private val tokens = FakePushTokenProvider(token = "fid-1")

    private fun create(auth: FakeAuthRepository) = MainActivityViewModel(
        auth,
        social,
        chat,
        notifications,
        PushRegistrar(auth, notifications, tokens, CoroutineScope(mainDispatcherRule.testDispatcher)),
        FakeAppearanceRepository(),
    )

    @Test
    fun it_asks_for_notifications_only_once_even_if_the_app_starts_again() {
        val viewModel = create(FakeAuthRepository(SessionState.SignedOut))
        assertTrue(viewModel.uiState.value.asksForNotifications)

        viewModel.onAskedForNotifications()

        assertFalse(viewModel.uiState.value.asksForNotifications)
        assertFalse(create(FakeAuthRepository(SessionState.SignedOut)).uiState.value.asksForNotifications)
    }

    @Test
    fun a_tapped_notification_waits_until_it_has_been_opened() {
        val viewModel = create(FakeAuthRepository(SessionState.Loading))

        viewModel.onLink("geowoof://notifications")
        assertEquals("geowoof://notifications", viewModel.uiState.value.pendingLink)

        viewModel.onLinkOpened()
        assertEquals(null, viewModel.uiState.value.pendingLink)
    }

    @Test
    fun exposes_the_session_of_the_repository() {
        val auth = FakeAuthRepository(SessionState.SignedOut)

        val viewModel = create(auth)
        auth.setSession(onboarded)

        assertEquals(onboarded, viewModel.uiState.value.sessionState)
    }

    @Test
    fun refreshes_the_user_once_the_stored_session_is_read() {
        val auth = FakeAuthRepository(SessionState.Loading)
        create(auth)
        assertEquals(0, auth.refreshes)

        auth.setSession(SessionState.SignedIn(TestUsers.user()))
        auth.setSession(SessionState.SignedIn(TestUsers.user(displayName = "Ana B.")))

        assertEquals(1, auth.refreshes)
    }

    @Test
    fun does_not_refresh_without_a_session() {
        val auth = FakeAuthRepository(SessionState.SignedOut)

        create(auth)

        assertEquals(0, auth.refreshes)
    }

    @Test
    fun finishing_the_username_step_offers_the_first_pet_until_it_is_done() {
        val auth = FakeAuthRepository(onboarding)
        val viewModel = create(auth)
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

        val viewModel = create(auth)
        auth.setSession(SessionState.SignedIn(TestUsers.user(username = "ana", displayName = "Ana B.")))

        assertFalse(viewModel.uiState.value.offerFirstPet)
    }

    @Test
    fun signing_out_during_the_step_cancels_it() {
        val auth = FakeAuthRepository(onboarding)
        val viewModel = create(auth)
        auth.setSession(onboarded)

        auth.setSession(SessionState.SignedOut)

        assertFalse(viewModel.uiState.value.offerFirstPet)
    }

    @Test
    fun the_pending_requests_reach_the_navigation_bar() {
        val viewModel = create(FakeAuthRepository(onboarded))

        social.receivedRequests.value = listOf(TestSocial.request("1"), TestSocial.request("2"))

        assertEquals(2, viewModel.uiState.value.pendingRequests)
    }

    @Test
    fun the_requests_refresh_when_a_person_finishes_signing_in_and_each_time_they_come_back() {
        val auth = FakeAuthRepository(SessionState.SignedOut)
        val viewModel = create(auth)
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
        val viewModel = create(FakeAuthRepository(SessionState.SignedOut))

        viewModel.onAppVisible()

        assertEquals(emptyList<String>(), social.calls)
    }

    @Test
    fun the_badges_follow_what_is_unread() {
        val viewModel = create(FakeAuthRepository(onboarded))

        chat.conversationsState.value = listOf(TestChat.conversation(unreadCount = 3))
        notifications.unread.value = 2

        assertEquals(3, viewModel.uiState.value.unreadMessages)
        assertEquals(2, viewModel.uiState.value.unreadNotifications)
    }

    @Test
    fun someone_who_finishes_signing_in_registers_the_device_and_reads_what_is_waiting() {
        val auth = FakeAuthRepository(onboarding)
        create(auth)
        assertEquals(0, tokens.registrations)

        auth.setSession(onboarded)

        assertEquals(1, tokens.registrations)
        assertEquals(listOf("refresh"), chat.calls)
        assertEquals(1, notifications.refreshes)
    }

    @Test
    fun coming_back_brings_the_badges_up_to_date() {
        val viewModel = create(FakeAuthRepository(onboarded))
        chat.calls.clear()

        viewModel.onAppVisible()

        assertEquals(listOf("refresh"), chat.calls)
        assertEquals(2, notifications.refreshes)
    }

    @Test
    fun the_chat_only_listens_while_there_is_someone_signed_in() = runTest(mainDispatcherRule.testDispatcher) {
        val auth = FakeAuthRepository(SessionState.SignedOut)
        val viewModel = create(auth)
        backgroundScope.launch { viewModel.listenToChat() }
        assertFalse(chat.isListening)

        auth.setSession(onboarded)
        assertTrue(chat.isListening)

        auth.setSession(SessionState.SignedOut)
        assertFalse(chat.isListening)
    }
}
