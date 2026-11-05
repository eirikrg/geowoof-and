package com.geowoof.core.data.mapper

import com.geowoof.core.database.BlockedUserEntity
import com.geowoof.core.database.FollowRequestEntity
import com.geowoof.core.database.UserColumns
import com.geowoof.core.model.BlockedUser
import com.geowoof.core.model.FollowRequest
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.UserSummary
import com.geowoof.core.network.model.NetworkBlock
import com.geowoof.core.network.model.NetworkFollowRequest
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

internal fun NetworkFollowRequest.toEntity() = FollowRequestEntity(
    id = id,
    user = user.toColumns(),
    direction = direction,
    createdAtEpochMs = Instant.parse(createdAt).toEpochMilli(),
)

internal fun FollowRequestEntity.toModel() = FollowRequest(
    id = id,
    user = user.toModel(),
    direction = RequestDirection.valueOf(direction),
    createdAt = Instant.ofEpochMilli(createdAtEpochMs),
)

internal fun NetworkBlock.toEntity() = BlockedUserEntity(user.toColumns(), Instant.parse(since).toEpochMilli())

internal fun BlockedUserEntity.toModel() = BlockedUser(user.toModel(), Instant.ofEpochMilli(sinceEpochMs))
