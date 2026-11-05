package com.geowoof.core.data.mapper

import com.geowoof.core.database.BlockedUserEntity
import com.geowoof.core.database.FriendEntity
import com.geowoof.core.database.FriendRequestEntity
import com.geowoof.core.database.UserColumns
import com.geowoof.core.model.BlockedUser
import com.geowoof.core.model.Friend
import com.geowoof.core.model.FriendRequest
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.UserSummary
import com.geowoof.core.network.model.NetworkBlock
import com.geowoof.core.network.model.NetworkFriend
import com.geowoof.core.network.model.NetworkFriendRequest
import com.geowoof.core.network.model.NetworkImage
import com.geowoof.core.network.model.NetworkUserSummary
import java.time.Instant

internal fun NetworkImage.toModel() = RemoteImage(assetId, thumbUrl, mediumUrl)

internal fun NetworkUserSummary.toModel() = UserSummary(id, username, displayName, city, avatar?.toModel())

private fun NetworkUserSummary.toColumns() = UserColumns(
    userId = id,
    username = username,
    displayName = displayName,
    city = city,
    avatarAssetId = avatar?.assetId,
    avatarThumbUrl = avatar?.thumbUrl,
    avatarMediumUrl = avatar?.mediumUrl,
)

private fun UserColumns.toModel() = UserSummary(
    id = userId,
    username = username,
    displayName = displayName,
    city = city,
    avatar = avatarAssetId?.let { RemoteImage(it, avatarThumbUrl, avatarMediumUrl) },
)

internal fun NetworkFriend.toEntity() = FriendEntity(user.toColumns(), Instant.parse(since).toEpochMilli())

internal fun FriendEntity.toModel() = Friend(user.toModel(), Instant.ofEpochMilli(sinceEpochMs))

internal fun NetworkFriendRequest.toEntity() = FriendRequestEntity(
    id = id,
    user = user.toColumns(),
    direction = direction,
    createdAtEpochMs = Instant.parse(createdAt).toEpochMilli(),
)

internal fun FriendRequestEntity.toModel() = FriendRequest(
    id = id,
    user = user.toModel(),
    direction = RequestDirection.valueOf(direction),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
)

internal fun NetworkBlock.toEntity() = BlockedUserEntity(user.toColumns(), Instant.parse(since).toEpochMilli())

internal fun BlockedUserEntity.toModel() = BlockedUser(user.toModel(), Instant.ofEpochMilli(sinceEpochMs))
