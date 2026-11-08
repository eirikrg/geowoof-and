package com.geowoof.core.testing

import androidx.paging.PagingData
import com.geowoof.core.data.repository.PostsRepository
import com.geowoof.core.model.Comment
import com.geowoof.core.model.CommentReplies
import com.geowoof.core.model.Mention
import com.geowoof.core.model.NewPost
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Post
import com.geowoof.core.model.PostLikes
import com.geowoof.core.model.PostReposts
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.UserSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object TestPosts {
    val IMAGE = RemoteImage("asset-1", "https://thumb", "https://medium")

    @Suppress("LongParameterList")
    fun post(
        id: String = "post-1",
        author: UserSummary = TestSocial.user(),
        text: String? = "En el Retiro",
        visibility: PostVisibility = PostVisibility.Everyone,
        media: List<RemoteImage> = listOf(IMAGE),
        mentions: List<Mention> = emptyList(),
        likeCount: Int = 0,
        commentCount: Int = 0,
        repostCount: Int = 0,
        likedByMe: Boolean = false,
        savedByMe: Boolean = false,
        repostedByMe: Boolean = false,
        canRepost: Boolean = true,
    ) = Post(
        id = id,
        author = author,
        text = text,
        visibility = visibility,
        media = media,
        pets = emptyList(),
        mentions = mentions,
        likeCount = likeCount,
        commentCount = commentCount,
        repostCount = repostCount,
        likedByMe = likedByMe,
        savedByMe = savedByMe,
        repostedByMe = repostedByMe,
        canRepost = canRepost,
        createdAt = TestSocial.SINCE,
    )

    fun item(post: Post = post(), repostedBy: UserSummary? = null) = TimelineItem(
        id = if (repostedBy == null) post.id else "repost-of-${post.id}",
        post = post,
        repostedBy = repostedBy,
        repostedAt = repostedBy?.let { TestSocial.SINCE },
    )

    @Suppress("LongParameterList")
    fun comment(
        id: String = "comment-1",
        author: UserSummary = TestSocial.user(),
        text: String = "Qué bonita",
        canDelete: Boolean = false,
        parentId: String? = null,
        isDeleted: Boolean = false,
        replies: List<Comment> = emptyList(),
        replyCount: Int = replies.size,
    ) = Comment(
        id = id,
        postId = "post-1",
        author = author,
        text = text,
        mentions = emptyList(),
        canDelete = canDelete,
        createdAt = TestSocial.SINCE,
        parentId = parentId,
        isDeleted = isDeleted,
        replyCount = replyCount,
        replies = replies,
    )
}

@Suppress("TooManyFunctions")
class FakePostsRepository : PostsRepository {
    var feedItems: List<TimelineItem> = emptyList()
    var explorePosts: List<Post> = emptyList()
    var userItems: List<TimelineItem> = emptyList()
    var savedItems: List<Post> = emptyList()
    var commentItems: List<Comment> = emptyList()
    var postResult: Outcome<Post> = Outcome.Success(TestPosts.post())
    var publishResult: Outcome<Post> = Outcome.Success(TestPosts.post())
    var addCommentResult: Outcome<Comment> = Outcome.Success(TestPosts.comment())

    /** The pages of replies, by the cursor that asks for them: `null` is the first. */
    var repliesResults: Map<String?, Outcome<CommentReplies>> = emptyMap()
    var actionResult: Outcome<Unit> = Outcome.Success(Unit)
    var likeResult: Outcome<PostLikes>? = null
    var repostResult: Outcome<PostReposts>? = null

    /** What was asked of the repository, in order: "like:post-1:true", "comment:post-1:Hola"... */
    val calls = mutableListOf<String>()
    val published = mutableListOf<NewPost>()

    /** The lists that were asked for: "user:bea-id", "saved", "comments:post-1"... */
    val reads = mutableListOf<String>()

    private fun record(call: String): Outcome<Unit> {
        calls += call
        return actionResult
    }

    override fun feed(): Flow<PagingData<TimelineItem>> {
        reads += "feed"
        return flowOf(TestSocial.pages(feedItems))
    }

    override fun explore(): Flow<PagingData<Post>> {
        reads += "explore"
        return flowOf(TestSocial.pages(explorePosts))
    }

    override fun userPosts(userId: String): Flow<PagingData<TimelineItem>> {
        reads += "user:$userId"
        return flowOf(TestSocial.pages(userItems))
    }

    override fun savedPosts(): Flow<PagingData<Post>> {
        reads += "saved"
        return flowOf(TestSocial.pages(savedItems))
    }

    override fun comments(postId: String): Flow<PagingData<Comment>> {
        reads += "comments:$postId"
        return flowOf(TestSocial.pages(commentItems))
    }

    override suspend fun post(postId: String) = postResult

    override suspend fun publish(post: NewPost): Outcome<Post> {
        published += post
        return publishResult
    }

    override suspend fun delete(postId: String) = record("delete:$postId")

    override suspend fun setLiked(postId: String, liked: Boolean): Outcome<PostLikes> {
        calls += "like:$postId:$liked"
        return likeResult ?: Outcome.Success(PostLikes(if (liked) 1 else 0, liked))
    }

    override suspend fun setReposted(postId: String, reposted: Boolean): Outcome<PostReposts> {
        calls += "repost:$postId:$reposted"
        return repostResult ?: Outcome.Success(PostReposts(if (reposted) 1 else 0, reposted))
    }

    override suspend fun setSaved(postId: String, saved: Boolean) = record("save:$postId:$saved")

    override suspend fun replies(postId: String, commentId: String, cursor: String?): Outcome<CommentReplies> {
        calls += "replies:$commentId:$cursor"
        return repliesResults[cursor] ?: Outcome.Success(CommentReplies(emptyList(), null))
    }

    override suspend fun addComment(postId: String, text: String, parentId: String?): Outcome<Comment> {
        calls += if (parentId == null) "comment:$postId:$text" else "reply:$parentId:$text"
        return addCommentResult
    }

    override suspend fun deleteComment(postId: String, commentId: String) = record("deleteComment:$postId:$commentId")

    override suspend fun reportPost(postId: String, reason: ReportReason, details: String?) =
        record("reportPost:$postId:$reason:$details")

    override suspend fun reportComment(commentId: String, reason: ReportReason, details: String?) =
        record("reportComment:$commentId:$reason:$details")
}
