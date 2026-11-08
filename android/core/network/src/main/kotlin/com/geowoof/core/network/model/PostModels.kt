package com.geowoof.core.network.model

import kotlinx.serialization.Serializable

// Wire format of posts, comments, reposts and the feed.

/** The [type] is `Image`. The videos of phase 9 add another, which this version leaves out. */
@Serializable
data class NetworkPostMedia(
    val type: String,
    val assetId: String,
    val thumbUrl: String? = null,
    val mediumUrl: String? = null,
)

@Serializable
data class NetworkTaggedPet(val id: String, val name: String, val avatar: NetworkImage? = null)

@Serializable
data class NetworkMention(val username: String, val userId: String)

@Serializable
data class NetworkPost(
    val id: String,
    val author: NetworkUserSummary,
    val text: String? = null,
    val visibility: String,
    val media: List<NetworkPostMedia> = emptyList(),
    val pets: List<NetworkTaggedPet> = emptyList(),
    val mentions: List<NetworkMention> = emptyList(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val repostCount: Int = 0,
    val likedByMe: Boolean = false,
    val savedByMe: Boolean = false,
    val repostedByMe: Boolean = false,
    val canRepost: Boolean = false,
    val createdAt: String,
)

@Serializable
data class NetworkTimelineItem(
    val id: String,
    val post: NetworkPost,
    val repostedBy: NetworkUserSummary? = null,
    val repostedAt: String? = null,
)

@Serializable
data class NetworkComment(
    val id: String,
    val postId: String,
    val author: NetworkUserSummary,
    val text: String,
    val mentions: List<NetworkMention> = emptyList(),
    val canDelete: Boolean = false,
    val createdAt: String,
)

@Serializable
data class NetworkPostLike(val likeCount: Int, val likedByMe: Boolean)

@Serializable
data class NetworkPostRepost(val repostCount: Int, val repostedByMe: Boolean)

@Serializable
data class PostBody(
    val text: String?,
    val visibility: String,
    val mediaAssetIds: List<String>,
    val petIds: List<String>,
)

@Serializable
data class CommentBody(val text: String)
