package com.geowoof.core.model

import java.time.Instant

/** Who a post is for. Keeping something private is not publishing it, so there is no third option. */
enum class PostVisibility { Everyone, Friends }

/** An `@username` of a text that links to that person. What is not here stays as plain text. */
data class Mention(val username: String, val userId: String)

/** A pet of the author that appears in a post. */
data class TaggedPet(val id: String, val name: String, val avatar: RemoteImage?)

data class Post(
    val id: String,
    val author: UserSummary,
    val text: String?,
    val visibility: PostVisibility,
    val media: List<RemoteImage>,
    val pets: List<TaggedPet>,
    val mentions: List<Mention>,
    val likeCount: Int,
    val commentCount: Int,
    val repostCount: Int,
    val likedByMe: Boolean,
    val savedByMe: Boolean,
    val repostedByMe: Boolean,
    val canRepost: Boolean,
    val createdAt: Instant,
)

/** A post in the feed or on a profile, published or reposted by someone. Its [id] is not always that of the post. */
data class TimelineItem(val id: String, val post: Post, val repostedBy: UserSummary?, val repostedAt: Instant?)

/**
 * A comment of a post, or a reply to one of them, with [parentId]: there is only one level, so a reply to a reply goes
 * to the same thread. A comment of the post brings how many replies it has and the first of them. One that
 * [isDeleted] only stays because of its replies, without its text.
 */
data class Comment(
    val id: String,
    val postId: String,
    val author: UserSummary,
    val text: String,
    val mentions: List<Mention>,
    val canDelete: Boolean,
    val createdAt: Instant,
    val parentId: String? = null,
    val isDeleted: Boolean = false,
    val replyCount: Int = 0,
    val replies: List<Comment> = emptyList(),
) {
    /** The comment of the post whose thread this is: itself, unless it is a reply. */
    val threadId: String get() = parentId ?: id
}

/** A page of the replies of a comment, the oldest first. */
data class CommentReplies(val items: List<Comment>, val nextCursor: String?)

/** What is sent to publish. The [id] comes from the app, so that repeating the request does not publish twice. */
data class NewPost(
    val id: String,
    val text: String?,
    val visibility: PostVisibility,
    val mediaAssetIds: List<String>,
    val petIds: List<String>,
)

data class PostLikes(val count: Int, val likedByMe: Boolean)

data class PostReposts(val count: Int, val repostedByMe: Boolean)
