package com.geowoof.core.model

import java.time.Instant

enum class MessageStatus {
    /** Waiting on the device until it can be sent. */
    Pending,
    Sent,

    /** The server rejected it: it stays for the person to try again. */
    Failed,
}

/**
 * A message. [clientMessageId] is its identity on the device, because [id] does not exist until the
 * server has it. Ordering the ids of the server is ordering the messages.
 */
data class ChatMessage(
    val clientMessageId: String,
    val id: String?,
    val conversationId: String,
    val senderId: String,
    val body: String?,
    val image: RemoteImage?,
    /** The copy on the device of a photo that has not been sent yet. */
    val localImagePath: String?,
    val status: MessageStatus,
    val errorCode: String?,
    val sentAt: Instant,
)

/** The last message, as the list shows it. */
data class MessagePreview(val senderId: String, val body: String?, val hasImage: Boolean)

data class Conversation(
    val id: String,
    val otherUser: UserSummary,
    /** Only friends can write. Otherwise, it can still be read. */
    val canSend: Boolean,
    val unreadCount: Int,
    val lastMessage: MessagePreview?,
    /** How far the other person has read. */
    val otherLastReadMessageId: String?,
    val updatedAt: Instant,
)

enum class NotificationType {
    UserFollowed,
    FollowRequested,
    FollowRequestAccepted,
    FriendshipStarted,
    PostLiked,
    CommentAdded,
    PostReposted,
    UserMentioned,
}

/** Something that someone did and this person should know about. */
data class AppNotification(
    val id: String,
    val type: NotificationType,
    val actor: UserSummary,
    val postId: String?,
    val requestId: String?,
    val isRead: Boolean,
    val createdAt: Instant,
)
