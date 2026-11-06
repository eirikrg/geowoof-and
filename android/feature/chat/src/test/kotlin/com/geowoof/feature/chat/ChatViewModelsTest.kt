package com.geowoof.feature.chat

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MessageStatus
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeChatRepository
import com.geowoof.core.testing.FakeOpenConversations
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestChat
import com.geowoof.core.testing.TestUsers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Types into a field and publishes the change, as a frame would in the app. */
private fun TextFieldState.enter(text: String) {
    setTextAndPlaceCursorAtEnd(text)
    Snapshot.sendApplyNotifications()
}

private class SteppingClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}

class ConversationsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chat = FakeChatRepository()
    private val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user()))

    private fun TestScope.created(): ConversationsViewModel {
        val viewModel = ConversationsViewModel(chat, auth)
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun it_waits_for_the_first_answer_only_while_there_is_nothing_to_show() = runTest {
        val viewModel = created()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onResume()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(listOf("refresh"), chat.calls)
    }

    @Test
    fun what_is_kept_shows_straight_away() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation())

        val viewModel = created()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.conversations.size)
    }

    @Test
    fun a_refresh_that_fails_is_told_once() = runTest {
        chat.refreshResult = Outcome.Failure(AppError.Network)
        val viewModel = created()

        viewModel.onResume()
        assertEquals(AppError.Network, viewModel.uiState.value.error)

        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.error)
    }
}

class NewConversationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun it_offers_the_friends_of_this_person() {
        val people = FakePeopleRepository()
        val user = TestUsers.user()

        NewConversationViewModel(people, FakeAuthRepository(SessionState.SignedIn(user)))

        assertEquals(listOf("friends:${user.id}"), people.reads)
    }
}

class ConversationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val chat = FakeChatRepository()
    private val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user()))
    private val open = FakeOpenConversations()
    private val clock = SteppingClock(TestChat.SENT_AT)
    private val id = TestChat.CONVERSATION_ID

    private fun TestScope.created(conversationId: String? = id, userId: String? = null): ConversationViewModel {
        val viewModel = ConversationViewModel(
            SavedStateHandle(mapOf("conversationId" to conversationId, "userId" to userId)),
            chat,
            auth,
            open,
            clock,
        )
        backgroundScope.launch(mainDispatcherRule.testDispatcher) { viewModel.uiState.collect() }
        return viewModel
    }

    @Test
    fun opening_it_tells_that_it_is_on_the_screen_catches_up_and_marks_it_as_read() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation())
        val viewModel = created()

        viewModel.onResume()

        assertEquals(id, open.current)
        assertEquals(listOf("catchUp:$id", "markRead:$id"), chat.calls)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun leaving_it_lets_notifications_through_again() = runTest {
        val viewModel = created()
        viewModel.onResume()

        viewModel.onPause()

        assertNull(open.current)
    }

    @Test
    fun reached_through_a_person_it_finds_the_conversation_first() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation())
        val viewModel = created(conversationId = null, userId = TestChat.BEA)

        viewModel.onResume()

        assertEquals(listOf("open:${TestChat.BEA}", "catchUp:$id", "markRead:$id"), chat.calls)
        assertEquals(id, open.current)
        assertEquals(TestChat.bea(), viewModel.uiState.value.otherUser)
    }

    @Test
    fun someone_who_is_no_longer_a_friend_has_no_conversation_to_open() = runTest {
        val rejected = AppError.Api(status = 403, code = "chat.not_friends")
        chat.openResult = Outcome.Failure(rejected)

        val viewModel = created(conversationId = null, userId = TestChat.BEA)

        assertTrue(viewModel.uiState.value.isGone)
        assertEquals(rejected, viewModel.uiState.value.error)
    }

    @Test
    fun a_conversation_that_no_longer_exists_says_so() = runTest {
        chat.catchUpResult = Outcome.Failure(AppError.Api(status = 404, code = "chat.conversation_not_found"))
        val viewModel = created()

        viewModel.onResume()

        assertTrue(viewModel.uiState.value.isGone)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun without_a_connection_it_also_tells_why() = runTest {
        chat.catchUpResult = Outcome.Failure(AppError.Network)
        val viewModel = created()

        viewModel.onResume()

        assertEquals(AppError.Network, viewModel.uiState.value.error)
    }

    @Test
    fun what_is_kept_can_be_read_without_a_connection() = runTest {
        chat.catchUpResult = Outcome.Failure(AppError.Network)
        chat.conversationsState.value = listOf(TestChat.conversation())
        chat.messagesState.value = listOf(TestChat.message())
        val viewModel = created()

        viewModel.onResume()

        assertFalse(viewModel.uiState.value.isGone)
        assertEquals(1, viewModel.uiState.value.messages.size)
    }

    @Test
    fun what_arrives_while_it_is_on_the_screen_is_read() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation())
        val viewModel = created()
        viewModel.onResume()
        chat.calls.clear()

        chat.messagesState.value = listOf(TestChat.message(id = "m2", clientMessageId = "k2"))

        assertEquals(listOf("markRead:$id"), chat.calls)
    }

    @Test
    fun what_arrives_while_it_is_not_on_the_screen_is_not() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation())
        val viewModel = created()
        viewModel.onResume()
        viewModel.onPause()
        chat.calls.clear()

        chat.messagesState.value = listOf(TestChat.message(id = "m2", clientMessageId = "k2"))

        assertEquals(emptyList<String>(), chat.calls)
    }

    @Test
    fun what_this_person_sends_does_not_count_as_something_to_read() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation())
        val viewModel = created()
        viewModel.onResume()
        chat.calls.clear()

        chat.messagesState.value = listOf(TestChat.message(id = "m2", senderId = TestUsers.user().id))

        assertEquals(emptyList<String>(), chat.calls)
    }

    @Test
    fun sending_trims_the_text_and_empties_the_field() = runTest {
        val viewModel = created()
        viewModel.input.enter("  Hola  ")

        viewModel.onSend()

        assertEquals("send:$id:Hola:null", chat.calls.last())
        assertEquals("", viewModel.input.text.toString())
    }

    @Test
    fun nothing_is_sent_without_text() = runTest {
        val viewModel = created()
        viewModel.input.enter("   ")

        viewModel.onSend()

        assertEquals(emptyList<String>(), chat.calls)
    }

    @Test
    fun a_photo_goes_on_its_own() = runTest {
        val viewModel = created()

        viewModel.onPickPhoto("content://photo")

        assertEquals("send:$id:null:content://photo", chat.calls.single())
    }

    @Test
    fun a_photo_that_cannot_be_read_is_told() = runTest {
        chat.sendResult = Outcome.Failure(AppError.Unexpected("unreadable"))
        val viewModel = created()

        viewModel.onPickPhoto("content://photo")
        assertTrue(viewModel.uiState.value.error is AppError.Unexpected)

        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun a_message_that_failed_can_be_retried() = runTest {
        val viewModel = created()

        viewModel.onRetry("k1")

        assertEquals("retry:k1", chat.calls.single())
    }

    @Test
    fun writing_is_told_at_most_every_three_seconds() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation())
        val viewModel = created()

        viewModel.input.enter("H")
        viewModel.input.enter("Ho")
        assertEquals(listOf("typing:$id"), chat.calls.filter { it.startsWith("typing") })

        clock.now = clock.now.plusMillis(3_000)
        viewModel.input.enter("Hol")
        assertEquals(2, chat.calls.count { it.startsWith("typing") })
    }

    @Test
    fun emptying_the_field_or_being_unable_to_send_tells_nothing() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation(canSend = false))
        val viewModel = created()

        viewModel.input.enter("Hola")
        viewModel.input.enter("")

        assertEquals(emptyList<String>(), chat.calls.filter { it.startsWith("typing") })
    }

    @Test
    fun the_conversation_tells_what_the_screen_needs() = runTest {
        chat.conversationsState.value = listOf(TestChat.conversation(canSend = false, otherLastRead = "m1"))
        chat.messagesState.value = listOf(TestChat.message(status = MessageStatus.Sent))
        chat.typing.value = true

        val state = created().uiState.value

        assertFalse(state.canSend)
        assertEquals("m1", state.otherLastReadMessageId)
        assertEquals(TestChat.bea(), state.otherUser)
        assertTrue(state.isOtherTyping)
    }

    @Test
    fun older_messages_stop_being_asked_for_at_the_beginning() = runTest {
        chat.olderResult = Outcome.Success(false)
        val viewModel = created()

        viewModel.onLoadOlder()
        viewModel.onLoadOlder()

        assertEquals(1, chat.calls.count { it.startsWith("older") })
        assertFalse(viewModel.uiState.value.isLoadingOlder)
    }

    @Test
    fun older_messages_that_fail_can_be_asked_for_again() = runTest {
        chat.olderResult = Outcome.Failure(AppError.Network)
        val viewModel = created()

        viewModel.onLoadOlder()
        viewModel.onLoadOlder()

        assertEquals(2, chat.calls.count { it.startsWith("older") })
    }
}
