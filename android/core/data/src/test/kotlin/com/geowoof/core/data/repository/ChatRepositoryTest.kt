package com.geowoof.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.media.WalkPhotoStore
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.database.GeoWoofDatabase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.MessageStatus
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.ChatNetworkDataSource
import com.geowoof.core.network.ChatRealtimeDataSource
import com.geowoof.core.network.model.NetworkChatEvent
import com.geowoof.core.network.model.NetworkConversation
import com.geowoof.core.network.model.NetworkImage
import com.geowoof.core.network.model.NetworkMessage
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkUserSummary
import com.geowoof.core.network.model.SendMessageBody
import com.geowoof.core.network.realtime.RealtimeState
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakeOpenConversations
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Singleton

private val ME = networkUser().id
private const val BEA = "bea-id"

@Suppress("LongParameterList")
private fun networkMessage(
    id: String = "m1",
    client: String = "k-$id",
    senderId: String = BEA,
    body: String? = "Hola",
    conversationId: String = "c1",
    sentAt: String = "2026-11-05T09:00:00Z",
) = NetworkMessage(id, conversationId, senderId, client, body, null, sentAt)

private fun networkConversation(
    id: String = "c1",
    unreadCount: Int = 0,
    lastMessage: NetworkMessage? = networkMessage(conversationId = id),
    otherLastRead: String? = null,
) = NetworkConversation(
    id = id,
    otherUser = NetworkUserSummary(BEA, "bea", "Bea"),
    canSend = true,
    unreadCount = unreadCount,
    lastMessage = lastMessage,
    otherLastReadMessageId = otherLastRead,
    updatedAt = "2026-11-05T09:00:00Z",
)

private class FakeChatNetwork : ChatNetworkDataSource {
    /** By cursor. */
    val conversationPages = mutableMapOf<String?, Outcome<NetworkPage<NetworkConversation>>>(
        null to Outcome.Success(NetworkPage(emptyList(), null)),
    )
    val conversationCursors = mutableListOf<String?>()
    var conversationAnswer: Outcome<NetworkConversation> = Outcome.Success(networkConversation())
    var conversationCalls = 0

    /** By cursor, from the newest to the oldest. */
    val messagePages = mutableMapOf<String?, NetworkPage<NetworkMessage>>(null to NetworkPage(emptyList(), null))
    val messageCursors = mutableListOf<String?>()

    /** By the message they come after. */
    val laterPages = mutableMapOf<String, NetworkPage<NetworkMessage>>()
    val laterAsked = mutableListOf<String>()

    val sent = mutableListOf<SendMessageBody>()
    var sendAnswer: (SendMessageBody) -> Outcome<NetworkMessage> = { body ->
        Outcome.Success(
            networkMessage(
                id = "server-${body.clientMessageId}",
                client = body.clientMessageId,
                senderId = ME,
                body = body.body,
            ),
        )
    }
    val reads = mutableListOf<String>()
    var readAnswer: Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun openWith(userId: String): Outcome<NetworkConversation> = conversationAnswer

    override suspend fun conversations(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkConversation>> {
        conversationCursors += cursor
        return conversationPages.getValue(cursor)
    }

    override suspend fun conversation(id: String): Outcome<NetworkConversation> {
        conversationCalls++
        return conversationAnswer
    }

    override suspend fun messages(id: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkMessage>> {
        messageCursors += cursor
        return Outcome.Success(messagePages.getValue(cursor))
    }

    override suspend fun messagesAfter(
        id: String,
        messageId: String,
        limit: Int,
    ): Outcome<NetworkPage<NetworkMessage>> {
        laterAsked += messageId
        return Outcome.Success(laterPages[messageId] ?: NetworkPage(emptyList(), null))
    }

    override suspend fun send(id: String, body: SendMessageBody): Outcome<NetworkMessage> {
        sent += body
        return sendAnswer(body)
    }

    override suspend fun read(id: String, messageId: String): Outcome<Unit> {
        reads += messageId
        return readAnswer
    }
}

private class FakeChatRealtime : ChatRealtimeDataSource {
    override val state = MutableStateFlow(RealtimeState.Disconnected)
    override val events = MutableSharedFlow<NetworkChatEvent>(extraBufferCapacity = 16)
    val typing = mutableListOf<String>()

    override suspend fun <T> whileOpen(block: suspend () -> T): T = block()

    override suspend fun setTyping(conversationId: String): Outcome<Unit> {
        typing += conversationId
        return Outcome.Success(Unit)
    }
}

private class FakeChatPhotos : WalkPhotoStore {
    var failure: AppError? = null
    val deleted = mutableListOf<String>()

    override suspend fun store(walkId: String, contentUris: List<String>): Outcome<List<String>> =
        failure?.let { Outcome.Failure(it) } ?: Outcome.Success(contentUris.map { "/stored/$walkId" })

    override suspend fun delete(walkId: String) {
        deleted += walkId
    }

    override suspend fun deleteAll() = Unit
}

private class FakeChatScheduler : ChatSendScheduler {
    var enqueued = 0
        private set

    override fun enqueue() {
        enqueued++
    }

    override fun cancelAll() = Unit
}

@RunWith(AndroidJUnit4::class)
class ChatRepositoryTest {
    private lateinit var database: GeoWoofDatabase
    private val network = FakeChatNetwork()
    private val realtime = FakeChatRealtime()
    private val media = FakeMediaRepository()
    private val photos = FakeChatPhotos()
    private val scheduler = FakeChatScheduler()
    private val open = FakeOpenConversations()
    private val now = Instant.parse("2026-11-05T10:00:00Z")

    private val dao get() = database.chatDao()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            GeoWoofDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private suspend fun TestScope.repository(): DefaultChatRepository {
        val sessions = SessionManager(InMemorySessionStorage(), NoUserData, backgroundScope)
        sessions.start(networkSession())
        runCurrent()
        return DefaultChatRepository(
            network, realtime, dao, sessions, photos, media, scheduler, open,
            Clock.fixed(
                now,
                ZoneOffset.UTC,
            ),
        )
    }

    /** With the conversation already in Room, as after opening it. */
    private suspend fun TestScope.repositoryWithConversation(
        conversation: NetworkConversation = networkConversation(),
    ): DefaultChatRepository {
        val repository = repository()
        network.conversationAnswer = Outcome.Success(conversation)
        repository.openWith(BEA)
        return repository
    }

    /** The hub works by itself, and Room answers from another thread. */
    private fun TestScope.listening(repository: DefaultChatRepository) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository.listen() }
        runCurrent()
    }

    private suspend fun untilConversation(
        id: String = "c1",
        condition: (com.geowoof.core.database.ConversationEntity?) -> Boolean,
    ) = dao.observeConversation(id).first(condition)

    // ------------------------------------------------------------ the list

    @Test
    fun refreshing_goes_through_the_pages_and_stops_at_the_last_one() = runTest {
        val repository = repository()
        network.conversationPages[null] = Outcome.Success(NetworkPage(listOf(networkConversation("c1")), "next"))
        network.conversationPages["next"] = Outcome.Success(NetworkPage(listOf(networkConversation("c2")), null))

        assertEquals(Outcome.Success(Unit), repository.refreshConversations())

        assertEquals(listOf(null, "next"), network.conversationCursors)
        assertEquals(setOf("c1", "c2"), repository.conversations.first().map { it.id }.toSet())
    }

    @Test
    fun a_refresh_that_fails_leaves_what_there_was() = runTest {
        val repository = repositoryWithConversation()
        network.conversationPages[null] = Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Failure(AppError.Network), repository.refreshConversations())

        assertEquals(listOf("c1"), repository.conversations.first().map { it.id })
    }

    @Test
    fun what_the_server_no_longer_lists_goes_away_unless_something_is_waiting_to_be_sent() = runTest {
        val repository = repository()
        network.conversationPages[null] =
            Outcome.Success(NetworkPage(listOf(networkConversation("c1"), networkConversation("c2")), null))
        repository.refreshConversations()
        repository.send("c2", "Hola", null)
        network.conversationPages[null] = Outcome.Success(NetworkPage(emptyList(), null))

        repository.refreshConversations()

        assertEquals(listOf("c2"), repository.conversations.first().map { it.id })
    }

    @Test
    fun a_conversation_without_messages_is_not_listed_but_stays_to_write_in_it() = runTest {
        val repository = repositoryWithConversation(networkConversation(lastMessage = null))

        repository.refreshConversations()

        assertEquals(emptyList<String>(), repository.conversations.first().map { it.id })
        assertNotNull(repository.conversation("c1").first())
    }

    @Test
    fun the_badge_adds_up_what_each_conversation_has_unread() = runTest {
        val repository = repository()
        network.conversationPages[null] = Outcome.Success(
            NetworkPage(
                listOf(networkConversation("c1", unreadCount = 2), networkConversation("c2", unreadCount = 3)),
                null,
            ),
        )

        repository.refreshConversations()

        assertEquals(5, repository.unreadMessages.first())
    }

    // ------------------------------------------------------------ what has arrived

    @Test
    fun the_first_time_it_reads_the_newest_page() = runTest {
        val repository = repositoryWithConversation()
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m2"), networkMessage("m1")), "older")

        assertEquals(Outcome.Success(Unit), repository.catchUp("c1"))

        assertEquals(1, network.conversationCalls)
        assertEquals(listOf(null), network.messageCursors)
        assertEquals(listOf("m2", "m1"), repository.messages("c1").first().map { it.id })
    }

    @Test
    fun after_that_it_asks_for_what_came_later_until_there_is_no_more() = runTest {
        val repository = repositoryWithConversation()
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m1")), null)
        repository.catchUp("c1")
        network.laterPages["m1"] = NetworkPage(listOf(networkMessage("m2", sentAt = "2026-11-05T09:01:00Z")), "more")
        network.laterPages["m2"] = NetworkPage(listOf(networkMessage("m3", sentAt = "2026-11-05T09:02:00Z")), null)

        repository.catchUp("c1")

        assertEquals(listOf("m1", "m2"), network.laterAsked)
        assertEquals(listOf("m3", "m2", "m1"), repository.messages("c1").first().map { it.id })
    }

    @Test
    fun opening_it_again_brings_the_photo_that_was_not_ready_and_renews_its_links() = runTest {
        val repository = repositoryWithConversation()
        fun photo(url: String?) = networkMessage("m1").copy(image = NetworkImage("a1", url, url))
        network.messagePages[null] = NetworkPage(listOf(photo(url = null)), null)
        repository.catchUp("c1")
        assertNull(repository.messages("c1").first().single().image?.mediumUrl)

        network.messagePages[null] = NetworkPage(listOf(photo(url = "https://files/a1")), null)
        repository.catchUp("c1")

        assertEquals("https://files/a1", repository.messages("c1").first().single().image?.mediumUrl)
    }

    @Test
    fun renewing_the_links_does_not_lose_the_place_in_the_older_messages() = runTest {
        val repository = repositoryWithConversation()
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m3", sentAt = "2026-11-05T09:03:00Z")), "older")
        network.messagePages["older"] =
            NetworkPage(listOf(networkMessage("m2", sentAt = "2026-11-05T09:02:00Z")), "oldest")
        network.messagePages["oldest"] =
            NetworkPage(listOf(networkMessage("m1", sentAt = "2026-11-05T09:01:00Z")), null)
        repository.loadOlder("c1")
        repository.catchUp("c1")
        network.messageCursors.clear()

        assertEquals(Outcome.Success(false), repository.loadOlder("c1"))

        assertEquals(listOf("oldest"), network.messageCursors)
    }

    @Test
    fun a_conversation_that_is_gone_is_removed_with_its_messages() = runTest {
        val repository = repositoryWithConversation()
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m1")), null)
        repository.catchUp("c1")
        val gone = AppError.Api(status = 404, code = "chat.conversation_not_found")
        network.conversationAnswer = Outcome.Failure(gone)

        assertEquals(Outcome.Failure(gone), repository.catchUp("c1"))

        assertNull(repository.conversation("c1").first())
        assertEquals(0, repository.messages("c1").first().size)
    }

    @Test
    fun without_a_connection_the_conversation_stays() = runTest {
        val repository = repositoryWithConversation()
        network.conversationAnswer = Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Failure(AppError.Network), repository.catchUp("c1"))

        assertNotNull(repository.conversation("c1").first())
    }

    @Test
    fun older_messages_after_a_restart_learn_the_cursor_and_go_on_in_the_same_call() = runTest {
        val repository = repositoryWithConversation()
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m3", sentAt = "2026-11-05T09:03:00Z")), "older")
        network.messagePages["older"] = NetworkPage(listOf(networkMessage("m2", sentAt = "2026-11-05T09:02:00Z")), null)

        assertEquals(Outcome.Success(false), repository.loadOlder("c1"))

        assertEquals(listOf(null, "older"), network.messageCursors)
        assertEquals(listOf("m3", "m2"), repository.messages("c1").first().map { it.id })
    }

    @Test
    fun older_messages_stop_at_the_beginning() = runTest {
        val repository = repositoryWithConversation()
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m1")), null)
        repository.catchUp("c1")

        assertEquals(Outcome.Success(false), repository.loadOlder("c1"))

        assertEquals(listOf(null), network.messageCursors)
    }

    // ------------------------------------------------------------ sending

    @Test
    fun a_message_shows_at_once_and_waits_in_the_queue() = runTest {
        val repository = repositoryWithConversation()

        assertEquals(Outcome.Success(Unit), repository.send("c1", "Hola, Bea", null))

        val message = repository.messages("c1").first().single()
        assertEquals(MessageStatus.Pending, message.status)
        assertEquals(ME, message.senderId)
        assertEquals("Hola, Bea", message.body)
        assertEquals(now, message.sentAt)
        assertEquals(1, scheduler.enqueued)
        assertEquals("Hola, Bea", repository.conversation("c1").first()?.lastMessage?.body)
    }

    @Test
    fun a_photo_that_cannot_be_read_says_so_and_nothing_is_queued() = runTest {
        val repository = repositoryWithConversation()
        photos.failure = AppError.Unexpected("unreadable")

        assertTrue(repository.send("c1", null, "content://photo") is Outcome.Failure)

        assertEquals(0, repository.messages("c1").first().size)
        assertEquals(0, scheduler.enqueued)
    }

    @Test
    fun the_queue_goes_in_order_and_each_message_becomes_the_one_of_the_server() = runTest {
        val repository = repositoryWithConversation()
        repository.send("c1", "Primero", null)
        repository.send("c1", "Segundo", null)

        assertEquals(ChatSendResult.Done, repository.sendPending())

        assertEquals(listOf("Primero", "Segundo"), network.sent.map { it.body })
        val messages = repository.messages("c1").first()
        assertTrue(messages.all { it.status == MessageStatus.Sent && it.id != null })
        assertEquals(0, dao.pendingMessages().size)
    }

    @Test
    fun a_problem_of_the_connection_stops_the_queue_to_keep_the_order() = runTest {
        val repository = repositoryWithConversation()
        repository.send("c1", "Primero", null)
        repository.send("c1", "Segundo", null)
        network.sendAnswer = { Outcome.Failure(AppError.Network) }

        assertEquals(ChatSendResult.Retry, repository.sendPending())

        assertEquals(listOf("Primero"), network.sent.map { it.body })
        assertEquals(2, dao.pendingMessages().size)
    }

    @Test
    fun a_server_that_is_busy_or_failing_is_tried_again() = runTest {
        val repository = repositoryWithConversation()
        repository.send("c1", "Hola", null)

        network.sendAnswer = { Outcome.Failure(AppError.Api(status = 429, code = "rate_limited")) }
        assertEquals(ChatSendResult.Retry, repository.sendPending())

        network.sendAnswer = { Outcome.Failure(AppError.Api(status = 503, code = "unavailable")) }
        assertEquals(ChatSendResult.Retry, repository.sendPending())

        assertEquals(1, dao.pendingMessages().size)
    }

    @Test
    fun what_the_server_rejects_stays_for_the_person_and_the_queue_goes_on() = runTest {
        val repository = repositoryWithConversation()
        repository.send("c1", "Rechazado", null)
        repository.send("c1", "Siguiente", null)
        network.sendAnswer = { body ->
            if (body.body == "Rechazado") {
                Outcome.Failure(AppError.Api(status = 403, code = "chat.not_friends"))
            } else {
                Outcome.Success(
                    networkMessage(id = "m9", client = body.clientMessageId, senderId = ME, body = body.body),
                )
            }
        }

        assertEquals(ChatSendResult.Done, repository.sendPending())

        val byBody = repository.messages("c1").first().associateBy { it.body }
        assertEquals(MessageStatus.Failed, byBody.getValue("Rechazado").status)
        assertEquals("chat.not_friends", byBody.getValue("Rechazado").errorCode)
        assertEquals(MessageStatus.Sent, byBody.getValue("Siguiente").status)
    }

    @Test
    fun a_photo_is_not_uploaded_twice_if_sending_it_has_to_be_retried() = runTest {
        val repository = repositoryWithConversation()
        repository.send("c1", null, "content://photo")
        network.sendAnswer = { Outcome.Failure(AppError.Network) }

        assertEquals(ChatSendResult.Retry, repository.sendPending())
        network.sendAnswer =
            { body ->
                Outcome.Success(networkMessage(id = "m9", client = body.clientMessageId, senderId = ME, body = null))
            }
        assertEquals(ChatSendResult.Done, repository.sendPending())

        assertEquals(1, media.uploads.size)
        assertEquals(MediaPurpose.ChatImage, media.uploads.single().second)
        assertEquals(listOf("asset-1", "asset-1"), network.sent.map { it.imageAssetId })
        assertEquals(1, photos.deleted.size)
    }

    @Test
    fun a_message_that_failed_goes_back_to_the_queue_when_asked() = runTest {
        val repository = repositoryWithConversation()
        repository.send("c1", "Hola", null)
        network.sendAnswer = { Outcome.Failure(AppError.Api(status = 403, code = "chat.not_friends")) }
        repository.sendPending()
        val failed = repository.messages("c1").first().single()

        repository.retry(failed.clientMessageId)

        val again = repository.messages("c1").first().single()
        assertEquals(MessageStatus.Pending, again.status)
        assertNull(again.errorCode)
        assertEquals(2, scheduler.enqueued)
    }

    // ------------------------------------------------------------ reading

    @Test
    fun reading_tells_the_last_message_received_and_only_once() = runTest {
        val repository = repositoryWithConversation(networkConversation(unreadCount = 2))
        network.messagePages[null] = NetworkPage(
            listOf(networkMessage("m2", senderId = ME), networkMessage("m1")),
            null,
        )
        repository.catchUp("c1")

        repository.markRead("c1")
        repository.markRead("c1")

        assertEquals(listOf("m1"), network.reads)
        assertEquals(0, repository.conversation("c1").first()?.unreadCount)
    }

    @Test
    fun if_telling_it_fails_it_is_tried_again_the_next_time() = runTest {
        val repository = repositoryWithConversation()
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m1")), null)
        repository.catchUp("c1")
        network.readAnswer = Outcome.Failure(AppError.Network)
        repository.markRead("c1")

        network.readAnswer = Outcome.Success(Unit)
        repository.markRead("c1")

        assertEquals(listOf("m1", "m1"), network.reads)
    }

    // ------------------------------------------------------------ the hub

    @Test
    fun a_message_that_arrives_counts_as_unread_and_becomes_the_preview() = runTest {
        val repository = repositoryWithConversation()
        listening(repository)

        realtime.events.emit(NetworkChatEvent.MessageReceived(networkMessage("m2", body = "¿Salimos?")))

        val conversation = untilConversation { it?.lastBody == "¿Salimos?" }
        assertEquals(1, conversation?.unreadCount)
        assertNotNull(dao.message("k-m2"))
    }

    @Test
    fun in_the_conversation_on_the_screen_it_does_not_count() = runTest {
        val repository = repositoryWithConversation()
        open.open("c1")
        listening(repository)

        realtime.events.emit(NetworkChatEvent.MessageReceived(networkMessage("m2", body = "¿Salimos?")))

        assertEquals(0, untilConversation { it?.lastBody == "¿Salimos?" }?.unreadCount)
    }

    @Test
    fun what_this_person_sends_from_another_device_does_not_count() = runTest {
        val repository = repositoryWithConversation()
        listening(repository)

        realtime.events.emit(
            NetworkChatEvent.MessageReceived(networkMessage("m2", senderId = ME, body = "Desde el otro")),
        )

        assertEquals(0, untilConversation { it?.lastBody == "Desde el otro" }?.unreadCount)
    }

    @Test
    fun the_same_message_twice_counts_once() = runTest {
        val repository = repositoryWithConversation()
        listening(repository)

        realtime.events.emit(NetworkChatEvent.MessageReceived(networkMessage("m2", body = "Una vez")))
        untilConversation { it?.lastBody == "Una vez" }
        realtime.events.emit(NetworkChatEvent.MessageReceived(networkMessage("m2", body = "Otra vez")))

        assertEquals(1, untilConversation { it?.lastBody == "Otra vez" }?.unreadCount)
    }

    @Test
    fun a_message_in_a_conversation_just_started_brings_the_list() = runTest {
        val repository = repository()
        listening(repository)
        network.conversationPages[null] = Outcome.Success(NetworkPage(listOf(networkConversation("c9")), null))

        realtime.events.emit(
            NetworkChatEvent.MessageReceived(networkMessage("m2", conversationId = "c9", body = "Primero")),
        )

        assertEquals("Primero", untilConversation("c9") { it?.lastBody == "Primero" }?.lastBody)
    }

    @Test
    fun the_other_person_reading_moves_the_checks() = runTest {
        val repository = repositoryWithConversation()
        listening(repository)

        realtime.events.emit(NetworkChatEvent.ConversationRead("c1", BEA, "m5"))

        assertEquals("m5", untilConversation { it?.otherLastReadMessageId == "m5" }?.otherLastReadMessageId)
    }

    @Test
    fun reading_on_another_device_clears_the_badge_here() = runTest {
        val repository = repositoryWithConversation(networkConversation(unreadCount = 4))
        listening(repository)

        realtime.events.emit(NetworkChatEvent.ConversationRead("c1", ME, "m5"))

        assertEquals(0, untilConversation { it?.unreadCount == 0 }?.unreadCount)
    }

    @Test
    fun someone_who_stops_writing_stops_being_shown() = runTest {
        val repository = repository()
        val shown = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repository.isTyping("c1").collect {
                shown +=
                    it
            }
        }
        listening(repository)

        realtime.events.emit(NetworkChatEvent.TypingChanged("c1", BEA))
        runCurrent()
        assertEquals(listOf(false, true), shown)

        advanceTimeBy(5_001)

        assertEquals(listOf(false, true, false), shown)
    }

    /** With two, who listens to the hub writes in one and the screen reads the other: nothing fails, it never shows. */
    @Test
    fun the_whole_app_shares_one_because_some_of_it_is_only_in_memory() {
        assertTrue(DefaultChatRepository::class.java.isAnnotationPresent(Singleton::class.java))
    }

    @Test
    fun connecting_asks_for_what_happened_while_it_was_closed() = runTest {
        val repository = repositoryWithConversation()
        open.open("c1")
        network.conversationPages[null] = Outcome.Success(NetworkPage(listOf(networkConversation("c1")), null))
        network.messagePages[null] = NetworkPage(listOf(networkMessage("m7", body = "Mientras tanto")), null)
        listening(repository)

        realtime.state.value = RealtimeState.Connected

        assertEquals("Mientras tanto", repository.messages("c1").first { it.isNotEmpty() }.single().body)
        assertEquals(listOf<String?>(null), network.conversationCursors)
    }
}

class NotificationsRepositoryTest {
    private val network = FakeDevicesNetwork()

    @Test
    fun unknown_kinds_of_notification_from_a_newer_server_are_left_out() = runTest {
        val known = com.geowoof.core.network.model.NetworkNotification(
            "n1",
            "PostLiked",
            NetworkUserSummary(BEA, "bea", "Bea"),
            postId = "p1",
            isRead = false,
            createdAt = "2026-11-05T10:00:00Z",
        )
        network.page = Outcome.Success(NetworkPage(listOf(known, known.copy(id = "n2", type = "SomethingNew")), null))
        val source = CursorPagingSource { limit, cursor -> notificationPage(limit, cursor) }

        val page = source.load(androidx.paging.PagingSource.LoadParams.Refresh(null, 20, false))
            as androidx.paging.PagingSource.LoadResult.Page

        assertEquals(listOf("n1"), page.data.map { it.id })
    }

    private suspend fun notificationPage(limit: Int, cursor: String?) =
        when (val page = network.notifications(limit, cursor)) {
            is Outcome.Failure -> page

            is Outcome.Success -> Outcome.Success(
                NetworkPage(
                    page.value.items.mapNotNull {
                        it.toModelOrNull()
                    },
                    page.value.nextCursor,
                ),
            )
        }
}
