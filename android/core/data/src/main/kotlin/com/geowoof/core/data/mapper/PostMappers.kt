package com.geowoof.core.data.mapper

import com.geowoof.core.model.Comment
import com.geowoof.core.model.Mention
import com.geowoof.core.model.NewPost
import com.geowoof.core.model.Post
import com.geowoof.core.model.PostLikes
import com.geowoof.core.model.PostReposts
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.TaggedPet
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.network.model.NetworkComment
import com.geowoof.core.network.model.NetworkMention
import com.geowoof.core.network.model.NetworkPost
import com.geowoof.core.network.model.NetworkPostLike
import com.geowoof.core.network.model.NetworkPostMedia
import com.geowoof.core.network.model.NetworkPostRepost
import com.geowoof.core.network.model.NetworkTaggedPet
import com.geowoof.core.network.model.NetworkTimelineItem
import com.geowoof.core.network.model.PostBody
import java.time.Instant

private const val IMAGE_TYPE = "Image"

/** A value from a newer API counts as the most limited one. */
internal fun visibilityOf(value: String) = PostVisibility.entries.find { it.name == value } ?: PostVisibility.Friends

/** What this version cannot show, such as a video of a newer API, is left out. */
internal fun List<NetworkPostMedia>.toImages() = filter { it.type == IMAGE_TYPE }
    .map { RemoteImage(it.assetId, it.thumbUrl, it.mediumUrl) }

internal fun NetworkTaggedPet.toModel() = TaggedPet(id, name, avatar?.toModel())

internal fun NetworkMention.toModel() = Mention(username, userId)

internal fun NetworkPost.toModel() = Post(
    id = id,
    author = author.toModel(),
    text = text,
    visibility = visibilityOf(visibility),
    media = media.toImages(),
    pets = pets.map { it.toModel() },
    mentions = mentions.map { it.toModel() },
    likeCount = likeCount,
    commentCount = commentCount,
    repostCount = repostCount,
    likedByMe = likedByMe,
    savedByMe = savedByMe,
    repostedByMe = repostedByMe,
    canRepost = canRepost,
    createdAt = Instant.parse(createdAt),
)

internal fun NetworkTimelineItem.toModel() = TimelineItem(
    id = id,
    post = post.toModel(),
    repostedBy = repostedBy?.toModel(),
    repostedAt = repostedAt?.let(Instant::parse),
)

internal fun NetworkComment.toModel(): Comment = Comment(
    id = id,
    postId = postId,
    author = author.toModel(),
    text = text,
    mentions = mentions.map { it.toModel() },
    canDelete = canDelete,
    createdAt = Instant.parse(createdAt),
    parentId = parentId,
    isDeleted = isDeleted,
    replyCount = replyCount,
    replies = replies.map { it.toModel() },
)

internal fun NetworkPostLike.toModel() = PostLikes(likeCount, likedByMe)

internal fun NetworkPostRepost.toModel() = PostReposts(repostCount, repostedByMe)

internal fun NewPost.toBody() = PostBody(text?.trim()?.ifEmpty { null }, visibility.name, mediaAssetIds, petIds)
