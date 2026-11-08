package com.geowoof.core.network

import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkChatEvent
import com.geowoof.core.network.model.SendMessageBody
import com.geowoof.core.network.realtime.RealtimeConnection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val MESSAGE_JSON =
    """{"id":"m1","conversationId":"c1","senderId":"bea-id","clientMessageId":"k1","body":"Hola",""" +
        """"image":{"assetId":"a","thumbUrl":"t","mediumUrl":"m"},"sentAt":"2026-11-05T10:00:00Z","somethingNew":1}"""

/** What each call sends: the contract of the API. */
class ChatNetworkDataSourceTest {
    private val server = MockWebServer()
    private lateinit var chat: RetrofitChatNetworkDataSource
    private lateinit var notifications: RetrofitNotificationsNetworkDataSource

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient()
        chat =
            RetrofitChatNetworkDataSource(
                retrofitFor(server.url("/").toString(), client, ChatApi::class.java),
                TestJson,
            )
        notifications = RetrofitNotificationsNetworkDataSource(
            retrofitFor(server.url("/").toString(), client, NotificationsApi::class.java),
            TestJson,
        )
    }

    @After
    fun tearDown() = server.close()

    private fun answer(code: Int = 200, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun what_came_after_a_message_is_asked_with_after_and_not_with_a_cursor() = runTest {
        answer(body = """{"items":[$MESSAGE_JSON],"nextCursor":null}""")

        val page = (chat.messagesAfter("c1", "m0", 30) as Outcome.Success).value

        assertEquals("/api/v1/conversations/c1/messages?limit=30&after=m0", server.takeRequest().target)
        assertEquals("Hola", page.items.single().body)
        assertEquals("t", page.items.single().image?.thumbUrl)
    }

    @Test
    fun a_message_goes_with_the_id_that_recognises_it_when_retried() = runTest {
        answer(code = 201, body = MESSAGE_JSON)

        chat.send("c1", SendMessageBody("k1", "Hola", null))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/conversations/c1/messages", request.target)
        assertEquals("""{"clientMessageId":"k1","body":"Hola"}""", request.body?.utf8())
    }

    @Test
    fun reading_says_up_to_which_message() = runTest {
        answer(code = 204)

        assertEquals(Outcome.Success(Unit), chat.read("c1", "m1"))

        val request = server.takeRequest()
        assertEquals("/api/v1/conversations/c1/read", request.target)
        assertEquals("""{"lastReadMessageId":"m1"}""", request.body?.utf8())
    }

    @Test
    fun a_device_is_registered_for_android_and_removed_with_its_id_in_the_body() = runTest {
        answer(code = 204)
        answer(code = 204)

        notifications.registerDevice("fid-1")
        notifications.unregisterDevice("fid-1")

        val registered = server.takeRequest()
        assertEquals("PUT", registered.method)
        assertEquals("/api/v1/devices", registered.target)
        assertTrue(registered.body?.utf8().orEmpty().contains(""""token":"fid-1""""))
        assertTrue(registered.body?.utf8().orEmpty().contains(""""platform":"Android""""))
        val removed = server.takeRequest()
        assertEquals("DELETE", removed.method)
        assertEquals("""{"token":"fid-1"}""", removed.body?.utf8())
    }

    @Test
    fun the_badge_of_the_notifications_is_a_count() = runTest {
        answer(body = """{"count":4}""")

        assertEquals(Outcome.Success(4), notifications.unreadCount())
        assertEquals("/api/v1/notifications/unread-count", server.takeRequest().target)
    }
}

/** What the chat hub says and how it is read: the contract of the hub. */
class ChatRealtimeDataSourceTest {
    private val client = RecordingHubClient()

    private fun TestScope.source(): SignalRChatRealtimeDataSource {
        val connection = RealtimeConnection(
            newClient = { client },
            events = listOf(
                SignalRChatRealtimeDataSource.MESSAGE_RECEIVED,
                SignalRChatRealtimeDataSource.CONVERSATION_READ,
                SignalRChatRealtimeDataSource.TYPING_CHANGED,
            ),
            scope = backgroundScope,
        )
        val source = SignalRChatRealtimeDataSource(connection, TestJson)
        val done = CompletableDeferred<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.whileOpen { done.await() } }
        runCurrent()
        return source
    }

    private fun TestScope.eventsOf(source: SignalRChatRealtimeDataSource): List<NetworkChatEvent> {
        val events = mutableListOf<NetworkChatEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.events.toList(events) }
        return events
    }

    @Test
    fun each_event_is_read_as_what_it_is() = runTest {
        val source = source()
        val events = eventsOf(source)

        client.receive("MessageReceived", MESSAGE_JSON)
        client.receive("ConversationRead", """{"conversationId":"c1","userId":"bea-id","lastReadMessageId":"m1"}""")
        client.receive("TypingChanged", """{"conversationId":"c1","userId":"bea-id"}""")
        runCurrent()

        assertEquals("Hola", (events[0] as NetworkChatEvent.MessageReceived).message.body)
        assertEquals(NetworkChatEvent.ConversationRead("c1", "bea-id", "m1"), events[1])
        assertEquals(NetworkChatEvent.TypingChanged("c1", "bea-id"), events[2])
    }

    @Test
    fun an_event_that_cannot_be_read_is_ignored_and_the_next_one_arrives() = runTest {
        val source = source()
        val events = eventsOf(source)

        client.receive("TypingChanged", """{"somethingElse":true}""")
        client.receive("TypingChanged", """{"conversationId":"c1","userId":"bea-id"}""")
        runCurrent()

        assertEquals(listOf<NetworkChatEvent>(NetworkChatEvent.TypingChanged("c1", "bea-id")), events)
    }

    @Test
    fun writing_is_told_with_the_conversation() = runTest {
        val source = source()

        assertEquals(Outcome.Success(Unit), source.setTyping("c1"))

        assertEquals("SetTyping" to mapOf("conversationId" to "c1"), client.calls.single())
    }
}
