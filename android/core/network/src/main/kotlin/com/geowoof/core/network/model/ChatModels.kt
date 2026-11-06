package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// The chat and the notifications of docs/api.md (phase 8), and the events of docs/realtime.md.

@Serializable
data class NetworkMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val clientMessageId: String,
    val body: String? = null,
    val image: NetworkImage? = null,
    val sentAt: String,
)

@Serializable
data class NetworkConversation(
    val id: String,
    val otherUser: NetworkUserSummary,
    val canSend: Boolean,
    val unreadCount: Int,
    val lastMessage: NetworkMessage? = null,
    val otherLastReadMessageId: String? = null,
    val updatedAt: String,
)

@Serializable
data class SendMessageBody(val clientMessageId: String, val body: String? = null, val imageAssetId: String? = null)

@Serializable
data class ReadBody(val lastReadMessageId: String)

@Serializable
data class NetworkCount(val count: Int)

@Serializable
data class NetworkNotification(
    val id: String,
    val type: String,
    val actor: NetworkUserSummary,
    val postId: String? = null,
    val commentId: String? = null,
    val requestId: String? = null,
    val isRead: Boolean,
    val createdAt: String,
)

@Serializable
data class DeviceBody(val token: String, val platform: String)

@Serializable
data class DeviceTokenBody(val token: String)

@Serializable
internal data class NetworkConversationRead(
    val conversationId: String,
    val userId: String,
    val lastReadMessageId: String,
)

@Serializable
internal data class NetworkTyping(val conversationId: String, val userId: String)

@Serializable
internal data class TypingBody(val conversationId: String)

sealed interface NetworkChatEvent {
    data class MessageReceived(val message: NetworkMessage) : NetworkChatEvent

    data class ConversationRead(val conversationId: String, val userId: String, val lastReadMessageId: String) :
        NetworkChatEvent

    data class TypingChanged(val conversationId: String, val userId: String) : NetworkChatEvent
}
