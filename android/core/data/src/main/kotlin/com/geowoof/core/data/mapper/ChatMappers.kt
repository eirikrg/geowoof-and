package com.geowoof.core.data.mapper

import com.geowoof.core.database.ChatMessageEntity
import com.geowoof.core.database.ConversationEntity
import com.geowoof.core.model.ChatMessage
import com.geowoof.core.model.Conversation
import com.geowoof.core.model.MessagePreview
import com.geowoof.core.model.MessageStatus
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.toEnumOr
import com.geowoof.core.network.model.NetworkMessage
import java.time.Instant

internal fun NetworkMessage.toEntity() = ChatMessageEntity(
    clientMessageId = clientMessageId,
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    body = body,
    imageAssetId = image?.assetId,
    imageThumbUrl = image?.thumbUrl,
    imageMediumUrl = image?.mediumUrl,
    localImagePath = null,
    status = MessageStatus.Sent.name,
    errorCode = null,
    sentAtEpochMs = Instant.parse(sentAt).toEpochMilli(),
)

internal fun ChatMessageEntity.toModel() = ChatMessage(
    clientMessageId = clientMessageId,
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    body = body,
    image = imageAssetId?.let { RemoteImage(it, imageThumbUrl, imageMediumUrl) },
    localImagePath = localImagePath,
    status = status.toEnumOr(MessageStatus.Failed),
    errorCode = errorCode,
    sentAt = Instant.ofEpochMilli(sentAtEpochMs),
)

internal fun ConversationEntity.toModel() = Conversation(
    id = id,
    otherUser = other.toModel(),
    canSend = canSend,
    unreadCount = unreadCount,
    lastMessage = lastSenderId?.let { MessagePreview(it, lastBody, lastHasImage) },
    otherLastReadMessageId = otherLastReadMessageId,
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMs),
)
