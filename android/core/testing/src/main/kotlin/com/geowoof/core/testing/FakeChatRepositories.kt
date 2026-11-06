package com.geowoof.core.testing

import androidx.paging.PagingData
import com.geowoof.core.data.repository.ChatRepository
import com.geowoof.core.data.repository.NotificationsRepository
import com.geowoof.core.data.repository.OpenConversations
import com.geowoof.core.data.repository.PushTokenProvider
import com.geowoof.core.model.AppError
import com.geowoof.core.model.AppNotification
import com.geowoof.core.model.ChatMessage
import com.geowoof.core.model.Conversation
import com.geowoof.core.model.MessagePreview
import com.geowoof.core.model.MessageStatus
import com.geowoof.core.model.NotificationType
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSummary
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

object TestChat {
    val SENT_AT: Instant = Instant.parse("2026-11-05T10:00:00Z")
    const val CONVERSATION_ID = "conversation-1"
    const val ME = "me-id"
    const val BEA = "bea-id"

    fun bea() = UserSummary(BEA, "bea", "Bea", null, null)

    fun conversation(
        id: String = CONVERSATION_ID,
        canSend: Boolean = true,
        unreadCount: Int = 0,
        lastMessage: MessagePreview? = MessagePreview(BEA, "Hola", false),
        otherLastRead: String? = null,
    ) = Conversation(id, bea(), canSend, unreadCount, lastMessage, otherLastRead, SENT_AT)

    fun message(
        clientMessageId: String = "client-1",
        id: String? = "message-1",
        senderId: String = BEA,
        body: String? = "Hola",
        status: MessageStatus = MessageStatus.Sent,
    ) = ChatMessage(clientMessageId, id, CONVERSATION_ID, senderId, body, null, null, status, null, SENT_AT)

    fun notification(
        id: String = "notification-1",
        type: NotificationType = NotificationType.PostLiked,
        postId: String? = "post-1",
        isRead: Boolean = false,
    ) = AppNotification(id, type, bea(), postId, null, isRead, SENT_AT)
}

/** Behaves like the real one, in memory. What it was asked stays written in [calls]. */
class FakeChatRepository : ChatRepository {
    val conversationsState = MutableStateFlow<List<Conversation>>(emptyList())
    val messagesState = MutableStateFlow<List<ChatMessage>>(emptyList())
    val typing = MutableStateFlow(false)

    /** "catchUp:id", "markRead:id", "send:id:body:image", "typing:id", "retry:id", "older:id"... */
    val calls = mutableListOf<String>()

    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var openResult: Outcome<String> = Outcome.Success(TestChat.CONVERSATION_ID)
    var catchUpResult: Outcome<Unit> = Outcome.Success(Unit)
    var sendResult: Outcome<Unit> = Outcome.Success(Unit)
    var olderResult: Outcome<Boolean> = Outcome.Success(true)
    var isListening = false
        private set

    override val conversations: Flow<List<Conversation>> = conversationsState

    override val unreadMessages: Flow<Int> = conversationsState.map { list -> list.sumOf { it.unreadCount } }

    override fun conversation(id: String): Flow<Conversation?> = conversationsState.map { list ->
        list.firstOrNull {
            it.id ==
                id
        }
    }

    override fun messages(conversationId: String): Flow<List<ChatMessage>> = messagesState

    override fun isTyping(conversationId: String): Flow<Boolean> = typing

    override suspend fun refreshConversations(): Outcome<Unit> {
        calls += "refresh"
        return refreshResult
    }

    override suspend fun catchUp(conversationId: String): Outcome<Unit> {
        calls += "catchUp:$conversationId"
        return catchUpResult
    }

    override suspend fun loadOlder(conversationId: String): Outcome<Boolean> {
        calls += "older:$conversationId"
        return olderResult
    }

    override suspend fun openWith(userId: String): Outcome<String> {
        calls += "open:$userId"
        return openResult
    }

    override suspend fun send(conversationId: String, body: String?, imageUri: String?): Outcome<Unit> {
        calls += "send:$conversationId:$body:$imageUri"
        return sendResult
    }

    override suspend fun retry(clientMessageId: String) {
        calls += "retry:$clientMessageId"
    }

    override suspend fun markRead(conversationId: String) {
        calls += "markRead:$conversationId"
    }

    override suspend fun setTyping(conversationId: String) {
        calls += "typing:$conversationId"
    }

    override suspend fun listen() {
        isListening = true
        try {
            awaitCancellation()
        } finally {
            isListening = false
        }
    }
}

class FakeNotificationsRepository : NotificationsRepository {
    val unread = MutableStateFlow(0)
    val devices = mutableListOf<String>()

    /** What the list shows. */
    var pages: Flow<PagingData<AppNotification>> = flowOf(PagingData.empty())
    var refreshes = 0
        private set
    var markedAllRead = 0
        private set
    var failure: AppError? = null

    override val unreadCount: Flow<Int> = unread

    override val wasAskedForPermission = MutableStateFlow(false)

    override suspend fun markAskedForPermission() {
        wasAskedForPermission.value = true
    }

    override fun notifications(): Flow<PagingData<AppNotification>> = pages

    override suspend fun refreshUnreadCount(): Outcome<Unit> {
        refreshes++
        return Outcome.Success(Unit)
    }

    override suspend fun markAllRead(): Outcome<Unit> {
        markedAllRead++
        failure?.let { return Outcome.Failure(it) }
        unread.update { 0 }
        return Outcome.Success(Unit)
    }

    override suspend fun registerDevice(token: String): Outcome<Unit> {
        devices += token
        return Outcome.Success(Unit)
    }
}

class FakeOpenConversations : OpenConversations {
    override var current: String? = null
        private set

    override fun open(conversationId: String) {
        current = conversationId
    }

    override fun close(conversationId: String) {
        if (current == conversationId) current = null
    }
}

class FakePushTokenProvider(var token: String? = null) : PushTokenProvider {
    var registrations = 0
        private set

    override suspend fun register() {
        registrations++
    }

    override suspend fun currentToken(): String? = token
}
