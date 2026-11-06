package com.geowoof.feature.chat

import android.os.Looper
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MessagePreview
import com.geowoof.core.model.MessageStatus
import com.geowoof.core.model.Person
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeChatRepository
import com.geowoof.core.testing.FakeOpenConversations
import com.geowoof.core.testing.TestChat
import com.geowoof.core.testing.TestSocial
import com.geowoof.feature.chat.navigation.ConversationRoute
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class ConversationsScreenTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val opened = mutableListOf<String>()
    private var newConversations = 0

    private fun show(uiState: ConversationsUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ConversationsContent(
                    uiState = uiState,
                    onBack = {},
                    onOpenConversation = { opened += it },
                    onNewConversation = { newConversations++ },
                    onDismissError = {},
                )
            }
        }
    }

    private fun loaded(vararg conversations: com.geowoof.core.model.Conversation) =
        ConversationsUiState(conversations.toList(), myUserId = TestChat.ME, isLoading = false)

    @Test
    fun each_conversation_shows_who_and_the_last_thing_said_and_opens() {
        show(loaded(TestChat.conversation(unreadCount = 2)))

        composeRule.onNodeWithText("Bea").assertIsDisplayed()
        composeRule.onNodeWithText("Hola").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()

        composeRule.onNodeWithText("Bea").performClick()

        assertEquals(listOf(TestChat.CONVERSATION_ID), opened)
    }

    @Test
    fun what_this_person_said_last_says_so() {
        show(loaded(TestChat.conversation(lastMessage = MessagePreview(TestChat.ME, "Vamos", false))))

        composeRule.onNodeWithText("You: Vamos").assertIsDisplayed()
    }

    @Test
    fun a_photo_says_it_is_a_photo() {
        show(loaded(TestChat.conversation(lastMessage = MessagePreview(TestChat.BEA, null, true))))

        composeRule.onNodeWithText("Photo").assertIsDisplayed()
    }

    @Test
    fun with_no_conversations_it_says_who_can_be_written_to_and_offers_a_new_one() {
        show(loaded())

        composeRule.onNodeWithText("friends", substring = true).assertIsDisplayed()

        composeRule.onNodeWithContentDescription("New conversation").performClick()
        assertEquals(1, newConversations)
    }
}

@RunWith(AndroidJUnit4::class)
class NewConversationScreenTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val chosen = mutableListOf<String>()

    private fun show(friends: PagingData<Person>) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                NewConversationContent(
                    friends = flowOf(friends).collectAsLazyPagingItems(),
                    onBack = {},
                    onOpenChat = { chosen += it },
                )
            }
        }
    }

    @Test
    fun choosing_a_friend_opens_the_chat_with_them() {
        show(TestSocial.pages(listOf(Person(TestChat.bea(), TestChat.SENT_AT))))
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("Bea")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Bea").performClick()

        assertEquals(listOf(TestChat.BEA), chosen)
    }

    @Test
    fun without_friends_it_explains_who_they_are() {
        show(TestSocial.pages(emptyList()))

        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasText("follow each other", substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("You have no friends yet").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
class ConversationScreenTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = ConversationActions(
        onBack = { calls += "back" },
        onOpenUser = { calls += "user:$it" },
        onSend = { calls += "send" },
        onPickPhoto = { calls += "photo" },
        onRetry = { calls += "retry:$it" },
        onLoadOlder = { calls += "older" },
        onDismissError = { calls += "dismiss" },
    )

    private fun show(uiState: ConversationUiState) {
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ConversationContent(uiState = uiState, input = TextFieldState(), actions = actions)
            }
        }
    }

    private fun loaded(
        vararg messages: com.geowoof.core.model.ChatMessage,
        otherLastRead: String? = null,
        canSend: Boolean = true,
    ) = ConversationUiState(
        myUserId = TestChat.ME,
        isLoading = false,
        otherUser = TestChat.bea(),
        canSend = canSend,
        messages = messages.toList(),
        otherLastReadMessageId = otherLastRead,
    )

    private fun mine(id: String? = "m2", status: MessageStatus = MessageStatus.Sent) =
        TestChat.message(clientMessageId = "k2", id = id, senderId = TestChat.ME, body = "Vamos", status = status)

    @Test
    fun it_shows_who_it_is_with_and_leads_to_their_profile() {
        show(loaded(TestChat.message()))

        composeRule.onNodeWithText("Bea").performClick()

        assertEquals("user:${TestChat.BEA}", calls.single { it.startsWith("user") })
    }

    @Test
    fun a_message_that_is_waiting_shows_a_clock() {
        show(loaded(mine(id = null, status = MessageStatus.Pending)))

        composeRule.onNodeWithContentDescription("Waiting to be sent", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun a_message_sent_shows_one_check_until_it_is_read() {
        show(loaded(mine(), otherLastRead = "m1"))

        composeRule.onNodeWithContentDescription("Sent", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun a_message_read_shows_two_even_if_later_ones_were_read_instead() {
        show(loaded(mine(), otherLastRead = "m3"))

        composeRule.onNodeWithContentDescription("Read", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun a_message_that_failed_can_be_tried_again_by_tapping_it() {
        show(loaded(mine(id = null, status = MessageStatus.Failed)))

        composeRule.onNodeWithText("Not sent. Tap to try again.").assertIsDisplayed().performClick()

        assertEquals("retry:k2", calls.single { it.startsWith("retry") })
    }

    @Test
    fun what_the_other_person_sends_has_no_checks() {
        show(loaded(TestChat.message()))

        composeRule.onNodeWithText("Hola").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Sent", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun it_tells_when_the_other_person_is_writing() {
        show(loaded(TestChat.message()).copy(isOtherTyping = true))

        composeRule.onNodeWithText("Bea is writing…").assertIsDisplayed()
    }

    private fun received(name: String) = TestChat.message(clientMessageId = name, id = name, body = name)

    private fun history() = (LONG_HISTORY downTo 1).map { received("Old $it") }.toTypedArray()

    /** Shows the history, lets [before] do something with it and then several messages arrive together. */
    private fun whenSeveralArrive(before: () -> Unit = {}) {
        var uiState by mutableStateOf(loaded(*history()))
        composeRule.setContent {
            GeoWoofTheme(dynamicColor = false) {
                ConversationContent(uiState = uiState, input = TextFieldState(), actions = actions)
            }
        }
        composeRule.waitForIdle()
        before()
        composeRule.waitForIdle()
        uiState = loaded(received("New 3"), received("New 2"), received("New 1"), *history())
        composeRule.waitForIdle()
    }

    @Test
    fun several_messages_arriving_together_do_not_leave_the_newest_out_of_sight() {
        whenSeveralArrive()

        composeRule.onNodeWithText("New 3").assertIsDisplayed()
    }

    @Test
    fun someone_reading_further_up_stays_there_when_several_arrive() {
        whenSeveralArrive(before = { composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(READING_AT) })

        composeRule.onNodeWithText("Old ${LONG_HISTORY - READING_AT}").assertIsDisplayed()
        composeRule.onNodeWithText("New 3").assertDoesNotExist()
    }

    @Test
    fun touching_a_photo_enlarges_it() {
        show(loaded(TestChat.message().copy(image = RemoteImage("a1", "https://t", "https://m"), body = null)))

        // Without the image, which does not load in these tests, it has no size that can be touched.
        composeRule.onNodeWithContentDescription("Photo", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.OnClick)

        composeRule.onNodeWithContentDescription("Close").assertIsDisplayed()
    }

    @Test
    fun a_photo_that_is_not_ready_cannot_be_enlarged() {
        show(loaded(TestChat.message().copy(image = RemoteImage("a1", null, null), body = null)))

        composeRule.onNodeWithContentDescription("Photo", useUnmergedTree = true).performClick()

        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
    }

    @Test
    fun a_photo_that_failed_is_sent_again_instead_of_enlarged() {
        val photo = RemoteImage("a1", "https://t", "https://m")
        show(loaded(TestChat.message(status = MessageStatus.Failed).copy(image = photo, body = null)))

        composeRule.onNodeWithContentDescription("Photo").performClick()

        composeRule.onNodeWithContentDescription("Close").assertDoesNotExist()
        assertEquals(listOf("retry:client-1"), calls.filter { it.startsWith("retry") })
    }

    @Test
    fun a_photo_that_is_not_ready_keeps_its_place_instead_of_an_empty_bubble() {
        show(loaded(TestChat.message().copy(image = RemoteImage("a1", null, null), body = null)))

        composeRule.onNodeWithContentDescription("Photo", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(100.dp)
    }

    @Test
    fun someone_who_can_no_longer_write_sees_why_instead_of_the_field() {
        show(loaded(TestChat.message(), canSend = false))

        composeRule.onNodeWithText("You can no longer write here", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    @Test
    fun sending_and_choosing_a_photo_are_at_hand() {
        show(loaded(TestChat.message()))

        composeRule.onNodeWithContentDescription("Send").performClick()
        composeRule.onNodeWithContentDescription("Send a photo").performClick()

        assertEquals(listOf("send", "photo"), calls.filter { it == "send" || it == "photo" })
    }

    @Test
    fun an_empty_conversation_invites_to_write() {
        show(loaded())

        composeRule.onNodeWithText("Say hello!").assertIsDisplayed()
    }

    @Test
    fun a_conversation_that_no_longer_exists_says_so() {
        show(ConversationUiState(isLoading = false, isGone = true))

        composeRule.onNodeWithText("This conversation is no longer available.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Send").assertDoesNotExist()
    }

    @Test
    fun an_error_is_told() {
        show(loaded(TestChat.message()).copy(error = AppError.Network))

        composeRule.onNodeWithText("No connection. Check your internet and try again.").assertIsDisplayed()
    }

    @Test
    fun close_to_the_beginning_it_asks_for_older_messages() {
        show(loaded(TestChat.message()))

        composeRule.waitUntil(WAIT_MILLIS) { calls.contains("older") }
    }
}

/** The ViewModel reads the arguments by name: this checks, with real navigation, that they get there. */
@RunWith(AndroidJUnit4::class)
class ChatRouteArgumentsTest {
    @get:Rule(order = -2)
    val drainMainLooper = DrainMainLooperRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val chat = FakeChatRepository()

    @Serializable
    data object StartRoute

    private fun navigateTo(route: Any) {
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = StartRoute) {
                composable<StartRoute> {
                    LaunchedEffect(Unit) { navController.navigate(route) }
                }
                composable<ConversationRoute> {
                    viewModel {
                        ConversationViewModel(
                            createSavedStateHandle(),
                            chat,
                            FakeAuthRepository(),
                            FakeOpenConversations(),
                            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                        )
                    }
                    Text("calls=${chat.calls}")
                }
            }
        }
    }

    @Test
    fun a_person_gets_to_the_conversation_as_a_person() {
        navigateTo(ConversationRoute(userId = TestChat.BEA))

        composeRule.waitUntil(WAIT_MILLIS) { chat.calls.isNotEmpty() }
        assertEquals(listOf("open:${TestChat.BEA}"), chat.calls)
    }

    @Test
    fun a_conversation_gets_there_with_its_id_and_nothing_is_looked_for() {
        navigateTo(ConversationRoute(conversationId = TestChat.CONVERSATION_ID))

        composeRule.waitForIdle()
        composeRule.onNodeWithText("calls=[]").assertIsDisplayed()
    }
}

class ChatTimeTest {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

    @Test
    fun today_is_the_time_and_any_other_day_the_date() {
        val now = Instant.parse("2026-11-05T18:00:00Z")
        val today = Instant.parse("2026-11-05T09:30:00Z")
        val yesterday = Instant.parse("2026-11-04T09:30:00Z")

        assertEquals(time.format(today.atZone(ZoneOffset.UTC)), formatChatTime(today, now, ZoneOffset.UTC))
        assertEquals(date.format(yesterday.atZone(ZoneOffset.UTC)), formatChatTime(yesterday, now, ZoneOffset.UTC))
    }

    @Test
    fun what_counts_as_today_is_where_the_person_is() {
        // Both are the same day in UTC, but midnight falls between them in Madrid.
        val madrid = ZoneId.of("Europe/Madrid")
        val message = Instant.parse("2026-11-05T22:30:00Z")
        val now = Instant.parse("2026-11-05T23:30:00Z")

        assertEquals(time.format(message.atZone(ZoneOffset.UTC)), formatChatTime(message, now, ZoneOffset.UTC))
        assertEquals(date.format(message.atZone(madrid)), formatChatTime(message, now, madrid))
    }
}

/**
 * Robolectric empties the main looper between tests, and AndroidUiDispatcher, which is one for the whole JVM,
 * believes for ever that what it had just posted is on its way: it runs nothing else, and LazyPagingItems needs it.
 * The screen stays in Loading, in about half of the runs, depending on how the previous test ended.
 */
class DrainMainLooperRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }
}

private const val WAIT_MILLIS = 10_000L
private const val LONG_HISTORY = 40
private const val READING_AT = 25
