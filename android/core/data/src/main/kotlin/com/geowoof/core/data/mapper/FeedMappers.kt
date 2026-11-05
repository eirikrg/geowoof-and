package com.geowoof.core.data.mapper

import com.geowoof.core.database.FeedItemEntity
import com.geowoof.core.model.Post
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.network.model.NetworkMention
import com.geowoof.core.network.model.NetworkPostMedia
import com.geowoof.core.network.model.NetworkTaggedPet
import com.geowoof.core.network.model.NetworkTimelineItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

internal fun NetworkTimelineItem.toEntity(): FeedItemEntity {
    val createdAtMs = Instant.parse(post.createdAt).toEpochMilli()
    val repostedAtMs = repostedAt?.let { Instant.parse(it).toEpochMilli() }
    return FeedItemEntity(
        id = id,
        postId = post.id,
        atEpochMs = repostedAtMs ?: createdAtMs,
        author = post.author.toColumns(),
        repostedBy = repostedBy?.toColumns(),
        repostedAtEpochMs = repostedAtMs,
        text = post.text,
        visibility = post.visibility,
        mediaJson = json.encodeToString(ListSerializer(NetworkPostMedia.serializer()), post.media),
        petsJson = json.encodeToString(ListSerializer(NetworkTaggedPet.serializer()), post.pets),
        mentionsJson = json.encodeToString(ListSerializer(NetworkMention.serializer()), post.mentions),
        likeCount = post.likeCount,
        commentCount = post.commentCount,
        repostCount = post.repostCount,
        likedByMe = post.likedByMe,
        savedByMe = post.savedByMe,
        repostedByMe = post.repostedByMe,
        canRepost = post.canRepost,
        createdAtEpochMs = createdAtMs,
    )
}

internal fun FeedItemEntity.toModel() = TimelineItem(
    id = id,
    post = Post(
        id = postId,
        author = author.toModel(),
        text = text,
        visibility = visibilityOf(visibility),
        media = json.decodeFromString(ListSerializer(NetworkPostMedia.serializer()), mediaJson).toImages(),
        pets = json.decodeFromString(ListSerializer(NetworkTaggedPet.serializer()), petsJson).map { it.toModel() },
        mentions = json.decodeFromString(ListSerializer(NetworkMention.serializer()), mentionsJson).map {
            it.toModel()
        },
        likeCount = likeCount,
        commentCount = commentCount,
        repostCount = repostCount,
        likedByMe = likedByMe,
        savedByMe = savedByMe,
        repostedByMe = repostedByMe,
        canRepost = canRepost,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
    ),
    repostedBy = repostedBy?.toModel(),
    repostedAt = repostedAtEpochMs?.let(Instant::ofEpochMilli),
)
