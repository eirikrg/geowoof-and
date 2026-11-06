package com.geowoof.core.data.repository

import com.geowoof.core.common.UuidV7
import com.geowoof.core.data.mapper.toColumns
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.media.WalkPhotoStore
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.database.ChatDao
import com.geowoof.core.database.ChatMessageEntity
import com.geowoof.core.database.ConversationEntity
import com.geowoof.core.model.AppError
import com.geowoof.core.model.ChatMessage
import com.geowoof.core.model.Conversation
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.MessageStatus
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.flatMap
import com.geowoof.core.model.map
import com.geowoof.core.network.ChatNetworkDataSource
import com.geowoof.core.network.ChatRealtimeDataSource
import com.geowoof.core.network.model.NetworkChatEvent
import com.geowoof.core.network.model.NetworkConversation
import com.geowoof.core.network.model.NetworkMessage
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.SendMessageBody
import com.geowoof.core.network.realtime.RealtimeState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Which conversation is on the screen: it is read as it arrives, and no notification interrupts it. */
interface OpenConversations {
    val current: String?

    fun open(conversationId: String)

    fun close(conversationId: String)
}

/**
 * The chat: read from Room, sent through a queue, and told by the hub while the app is visible
 * (docs/decisions/0014).
 */
interface ChatRepository {
    val conversations: Flow<List<Conversation>>

    val unreadMessages: Flow<Int>

    fun conversation(id: String): Flow<Conversation?>

    /** From the newest to the oldest. */
    fun messages(conversationId: String): Flow<List<ChatMessage>>

    fun isTyping(conversationId: String): Flow<Boolean>

    suspend fun refreshConversations(): Outcome<Unit>

    /** What has arrived since the last message here. */
    suspend fun catchUp(conversationId: String): Outcome<Unit>

    /** @return whether there is more to load. */
    suspend fun loadOlder(conversationId: String): Outcome<Boolean>

    /** @return the id of the conversation with that person. */
    suspend fun openWith(userId: String): Outcome<String>

    /** It only fails if the photo cannot be read: the text is kept and goes when it can. */
    suspend fun send(conversationId: String, body: String?, imageUri: String?): Outcome<Unit>

    suspend fun retry(clientMessageId: String)

    suspend fun markRead(conversationId: String)

    suspend fun setTyping(conversationId: String)

    /** Keeps the hub open until it is cancelled. */
    suspend fun listen()
}

/** What can happen when sending the queue: the worker decides by this. */
enum class ChatSendResult {
    Done,
    Retry,
}

/** Sends what is waiting, in order. */
fun interface ChatSender {
    suspend fun sendPending(): ChatSendResult
}

interface ChatSendScheduler {
    fun enqueue()

    fun cancelAll()
}

/** One for the app: who listens to the hub and the screens have to share what is only in memory. */
@Singleton
@Suppress("LongParameterList")
@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultChatRepository @Inject constructor(
    private val network: ChatNetworkDataSource,
    private val realtime: ChatRealtimeDataSource,
    private val dao: ChatDao,
    private val sessions: SessionManager,
    private val photos: WalkPhotoStore,
    private val media: MediaRepository,
    private val scheduler: ChatSendScheduler,
    private val openConversations: OpenConversations,
    private val clock: Clock,
) : ChatRepository,
    ChatSender {
    private val typingUntil = MutableStateFlow<Map<String, Long>>(emptyMap())

    /** Where the next older page starts. A cursor cannot be made up, so it is learned from the server. */
    private val olderCursors = mutableMapOf<String, String?>()

    private val myUserId: String? get() = (sessions.sessionState.value as? SessionState.SignedIn)?.user?.id

    override val conversations: Flow<List<Conversation>> =
        dao.observeConversations().map { rows -> rows.map { it.toModel() } }

    override val unreadMessages: Flow<Int> = dao.observeUnreadMessages().distinctUntilChanged()

    override fun conversation(id: String): Flow<Conversation?> = dao.observeConversation(id).map { it?.toModel() }

    override fun messages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observeMessages(conversationId).map { rows -> rows.map { it.toModel() } }

    /** It stops on its own: someone who stops writing does not say so. */
    override fun isTyping(conversationId: String): Flow<Boolean> = typingUntil
        .map { it[conversationId] }
        .distinctUntilChanged()
        .flatMapLatest { until ->
            flow {
                val left = (until ?: 0L) - clock.millis()
                if (left > 0) {
                    emit(true)
                    delay(left)
                }
                emit(false)
            }
        }
        .distinctUntilChanged()

    override suspend fun refreshConversations(): Outcome<Unit> {
        val found = mutableListOf<NetworkConversation>()
        var cursor: String? = null
        var pages = 0
        do {
            when (val page = network.conversations(PAGE_SIZE, cursor)) {
                is Outcome.Failure -> return page

                is Outcome.Success -> {
                    found += page.value.items
                    cursor = page.value.nextCursor
                }
            }
            pages++
        } while (cursor != null && pages < MAX_CONVERSATION_PAGES)
        dao.replaceConversations(found.map { it.toEntity(known = dao.conversation(it.id)) })
        return Outcome.Success(Unit)
    }

    override suspend fun catchUp(conversationId: String): Outcome<Unit> {
        val known = refreshConversation(conversationId)
        if (known is Outcome.Failure) return known
        val last = dao.lastMessageId(conversationId)
        return if (last == null) {
            newestPage(conversationId).map { }
        } else {
            messagesAfter(conversationId, last).flatMap { renewNewest(conversationId) }
        }
    }

    /**
     * The links of a photo last a day, and a photo may not have been ready when its message arrived.
     * It does not touch the cursor: whoever is reading older messages goes on from where they were.
     */
    private suspend fun renewNewest(conversationId: String): Outcome<Unit> =
        network.messages(conversationId, PAGE_SIZE, cursor = null).map { page ->
            dao.upsertMessages(page.items.map { it.toEntity() })
        }

    private suspend fun refreshConversation(id: String): Outcome<Unit> = when (
        val conversation = network.conversation(id)
    ) {
        is Outcome.Failure -> {
            // It is gone: it has been deleted, or this person has blocked the other.
            if ((conversation.error as? AppError.Api)?.code == CONVERSATION_NOT_FOUND) dao.deleteConversation(id)
            conversation
        }

        is Outcome.Success -> {
            dao.upsertConversation(conversation.value.toEntity(dao.conversation(id)))
            Outcome.Success(Unit)
        }
    }

    private suspend fun messagesAfter(conversationId: String, messageId: String): Outcome<Unit> {
        var last = messageId
        while (true) {
            when (val page = network.messagesAfter(conversationId, last, PAGE_SIZE)) {
                is Outcome.Failure -> return page

                is Outcome.Success -> {
                    dao.upsertMessages(page.value.items.map { it.toEntity() })
                    if (page.value.nextCursor == null) return Outcome.Success(Unit)
                    last = page.value.items.last().id
                }
            }
        }
    }

    private suspend fun newestPage(conversationId: String): Outcome<Boolean> =
        network.messages(conversationId, PAGE_SIZE, cursor = null).map { page -> storeOlder(conversationId, page) }

    /** @return whether there is more. */
    private suspend fun storeOlder(conversationId: String, page: NetworkPage<NetworkMessage>): Boolean {
        dao.upsertMessages(page.items.map { it.toEntity() })
        olderCursors[conversationId] = page.nextCursor
        return page.nextCursor != null
    }

    override suspend fun loadOlder(conversationId: String): Outcome<Boolean> {
        // What Room has may come from another session: the cursor to go on from is learned from the first page.
        val learned = if (conversationId in olderCursors) Outcome.Success(true) else newestPage(conversationId)
        if (learned is Outcome.Failure) return learned
        val cursor = olderCursors[conversationId]
        return if (cursor == null) {
            Outcome.Success(false)
        } else {
            network.messages(conversationId, PAGE_SIZE, cursor).map { page -> storeOlder(conversationId, page) }
        }
    }

    override suspend fun openWith(userId: String): Outcome<String> = network.openWith(userId).map { conversation ->
        dao.upsertConversation(conversation.toEntity(known = dao.conversation(conversation.id)))
        conversation.id
    }

    override suspend fun send(conversationId: String, body: String?, imageUri: String?): Outcome<Unit> {
        val userId = myUserId ?: return Outcome.Failure(AppError.SessionExpired)
        val now = clock.millis()
        val clientMessageId = UuidV7.generate(now)
        return storedPhoto(clientMessageId, imageUri).map { localPath ->
            dao.upsertMessage(
                ChatMessageEntity(
                    clientMessageId = clientMessageId,
                    id = null,
                    conversationId = conversationId,
                    senderId = userId,
                    body = body,
                    imageAssetId = null,
                    imageThumbUrl = null,
                    imageMediumUrl = null,
                    localImagePath = localPath,
                    status = MessageStatus.Pending.name,
                    errorCode = null,
                    sentAtEpochMs = now,
                ),
            )
            dao.conversation(conversationId)?.let { conversation ->
                dao.upsertConversation(
                    conversation.copy(
                        lastSenderId = userId,
                        lastBody = body,
                        lastHasImage = localPath != null,
                        updatedAtEpochMs = now,
                    ),
                )
            }
            scheduler.enqueue()
        }
    }

    private suspend fun storedPhoto(clientMessageId: String, imageUri: String?): Outcome<String?> =
        if (imageUri == null) {
            Outcome.Success(null)
        } else {
            photos.store(PHOTO_FOLDER_PREFIX + clientMessageId, listOf(imageUri)).map { it.single() }
        }

    override suspend fun retry(clientMessageId: String) {
        val message = dao.message(clientMessageId) ?: return
        dao.upsertMessage(message.copy(status = MessageStatus.Pending.name, errorCode = null))
        scheduler.enqueue()
    }

    override suspend fun sendPending(): ChatSendResult {
        for (message in dao.pendingMessages()) {
            if (sendOne(message) == ChatSendResult.Retry) return ChatSendResult.Retry
        }
        return ChatSendResult.Done
    }

    /** A problem of the connection stops the queue, to keep the order. What the server rejects stays for the person. */
    private suspend fun sendOne(message: ChatMessageEntity): ChatSendResult {
        val assetId = when (val image = uploadPhoto(message)) {
            is Outcome.Failure -> return onSendFailure(message, image.error)
            is Outcome.Success -> image.value
        }
        val body = SendMessageBody(message.clientMessageId, message.body, assetId)
        return when (val sent = network.send(message.conversationId, body)) {
            is Outcome.Failure -> onSendFailure(message.copy(imageAssetId = assetId), sent.error)

            is Outcome.Success -> {
                dao.upsertMessage(sent.value.toEntity())
                message.localImagePath?.let { photos.delete(PHOTO_FOLDER_PREFIX + message.clientMessageId) }
                ChatSendResult.Done
            }
        }
    }

    private suspend fun uploadPhoto(message: ChatMessageEntity): Outcome<String?> {
        val path = message.localImagePath
        return when {
            message.imageAssetId != null -> Outcome.Success(message.imageAssetId)

            path == null -> Outcome.Success(null)

            else -> media.uploadStoredImage(path, MediaPurpose.ChatImage).map { assetId ->
                // If what comes next fails, the photo is not uploaded twice.
                dao.upsertMessage(message.copy(imageAssetId = assetId))
                assetId
            }
        }
    }

    private suspend fun onSendFailure(message: ChatMessageEntity, error: AppError): ChatSendResult {
        if (error.isWorthRetrying()) return ChatSendResult.Retry
        dao.upsertMessage(message.copy(status = MessageStatus.Failed.name, errorCode = (error as? AppError.Api)?.code))
        return ChatSendResult.Done
    }

    private fun AppError.isWorthRetrying(): Boolean = when (this) {
        AppError.Network -> true
        is AppError.Api -> status == HTTP_TOO_MANY_REQUESTS || status >= HTTP_SERVER_ERRORS
        else -> false
    }

    override suspend fun markRead(conversationId: String) {
        val last = myUserId?.let { dao.lastReceivedMessageId(conversationId, it) } ?: return
        val isNew = dao.conversation(conversationId)?.myLastReadMessageId != last
        if (isNew && network.read(conversationId, last) is Outcome.Success) dao.setMyLastRead(conversationId, last)
    }

    override suspend fun setTyping(conversationId: String) {
        realtime.setTyping(conversationId)
    }

    override suspend fun listen() = realtime.whileOpen {
        coroutineScope {
            launch(start = CoroutineStart.UNDISPATCHED) {
                realtime.events.collect { event -> onEvent(event) }
            }
            // The hub tells each thing once: what happened while it was closed is asked for.
            realtime.state.filter { it == RealtimeState.Connected }.collect {
                refreshConversations()
                openConversations.current?.let { open -> catchUp(open) }
            }
        }
    }

    private suspend fun onEvent(event: NetworkChatEvent) {
        when (event) {
            is NetworkChatEvent.MessageReceived -> onMessage(event.message)

            is NetworkChatEvent.ConversationRead -> {
                if (event.userId == myUserId) {
                    dao.setMyLastRead(event.conversationId, event.lastReadMessageId)
                } else {
                    dao.setOtherLastRead(event.conversationId, event.lastReadMessageId)
                }
            }

            is NetworkChatEvent.TypingChanged -> typingUntil.update {
                it +
                    (event.conversationId to clock.millis() + TYPING_LIFETIME_MS)
            }
        }
    }

    private suspend fun onMessage(message: NetworkMessage) {
        val conversation = dao.conversation(message.conversationId)
        if (conversation == null) {
            // A conversation that the other person has just started.
            refreshConversations()
            if (dao.conversation(message.conversationId) == null) return
        }
        val isNew = dao.message(message.clientMessageId)?.id == null
        dao.upsertMessage(message.toEntity())
        typingUntil.update { it - message.conversationId }
        val known = dao.conversation(message.conversationId) ?: return
        val isMine = message.senderId == myUserId
        val isSeen = openConversations.current == message.conversationId
        dao.upsertConversation(
            known.copy(
                lastSenderId = message.senderId,
                lastBody = message.body,
                lastHasImage = message.image != null,
                unreadCount = if (isNew && !isMine && !isSeen) known.unreadCount + 1 else known.unreadCount,
                updatedAtEpochMs = Instant.parse(message.sentAt).toEpochMilli(),
            ),
        )
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val MAX_CONVERSATION_PAGES = 5
        const val TYPING_LIFETIME_MS = 5_000L
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERRORS = 500
        const val CONVERSATION_NOT_FOUND = "chat.conversation_not_found"
        const val PHOTO_FOLDER_PREFIX = "chat-"
    }
}

internal fun NetworkConversation.toEntity(known: ConversationEntity?) = ConversationEntity(
    id = id,
    other = otherUser.toColumns(),
    canSend = canSend,
    unreadCount = unreadCount,
    lastSenderId = lastMessage?.senderId,
    lastBody = lastMessage?.body,
    lastHasImage = lastMessage?.image != null,
    otherLastReadMessageId = otherLastReadMessageId,
    myLastReadMessageId = known?.myLastReadMessageId,
    updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
)
