package com.geowoof.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import com.geowoof.core.data.mapper.toBody
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.database.FeedDao
import com.geowoof.core.database.FeedItemEntity
import com.geowoof.core.model.Comment
import com.geowoof.core.model.CommentReplies
import com.geowoof.core.model.NewPost
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Post
import com.geowoof.core.model.PostLikes
import com.geowoof.core.model.PostReposts
import com.geowoof.core.model.ReportReason
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.TimelineItem
import com.geowoof.core.model.map
import com.geowoof.core.model.onFailure
import com.geowoof.core.model.onSuccess
import com.geowoof.core.network.PostsNetworkDataSource
import com.geowoof.core.network.SocialNetworkDataSource
import com.geowoof.core.network.model.NetworkTimelineItem
import com.geowoof.core.network.model.ReportBody
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Posts, and what people do with them. The feed is kept in Room so that it opens at once, and everything else
 * comes from the server: it depends on who is looking, and a block has to count at once.
 */
@Suppress("TooManyFunctions")
interface PostsRepository {
    fun feed(): Flow<PagingData<TimelineItem>>

    fun explore(): Flow<PagingData<Post>>

    /** What this person published and reposted, that the signed-in person can see. */
    fun userPosts(userId: String): Flow<PagingData<TimelineItem>>

    fun savedPosts(): Flow<PagingData<Post>>

    /** The comments of the post itself, each with how many replies it has and the first of them. */
    fun comments(postId: String): Flow<PagingData<Comment>>

    /** A page of the replies of a comment of the post. */
    suspend fun replies(postId: String, commentId: String, cursor: String?): Outcome<CommentReplies>

    suspend fun post(postId: String): Outcome<Post>

    suspend fun publish(post: NewPost): Outcome<Post>

    suspend fun delete(postId: String): Outcome<Unit>

    /** The feed changes at once, and goes back if the server says no. */
    suspend fun setLiked(postId: String, liked: Boolean): Outcome<PostLikes>

    suspend fun setReposted(postId: String, reposted: Boolean): Outcome<PostReposts>

    suspend fun setSaved(postId: String, saved: Boolean): Outcome<Unit>

    /** With [parentId], a reply in the thread of that comment. */
    suspend fun addComment(postId: String, text: String, parentId: String? = null): Outcome<Comment>

    /** One with replies stays as deleted, so that they keep their place. */
    suspend fun deleteComment(postId: String, commentId: String): Outcome<Unit>

    suspend fun reportPost(postId: String, reason: ReportReason, details: String?): Outcome<Unit>

    suspend fun reportComment(commentId: String, reason: ReportReason, details: String?): Outcome<Unit>
}

@Suppress("TooManyFunctions")
internal class DefaultPostsRepository @Inject constructor(
    private val network: PostsNetworkDataSource,
    private val social: SocialNetworkDataSource,
    private val feedDao: FeedDao,
    private val sessions: SessionManager,
    private val changes: ContentChanges,
) : PostsRepository {
    @OptIn(ExperimentalPagingApi::class)
    override fun feed(): Flow<PagingData<TimelineItem>> = Pager(
        config = PagingConfig(pageSize = FEED_PAGE_SIZE),
        remoteMediator = FeedRemoteMediator(feedDao, network),
        pagingSourceFactory = { feedDao.pagingSource() },
    ).flow.map { page -> page.map { it.toModel() } }

    override fun explore(): Flow<PagingData<Post>> = cursorPager { limit, cursor ->
        network.explore(limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override fun userPosts(userId: String): Flow<PagingData<TimelineItem>> = cursorPager { limit, cursor ->
        network.userPosts(userId, limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override fun savedPosts(): Flow<PagingData<Post>> = cursorPager { limit, cursor ->
        network.savedPosts(limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override fun comments(postId: String): Flow<PagingData<Comment>> = cursorPager { limit, cursor ->
        network.comments(postId, limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override suspend fun replies(postId: String, commentId: String, cursor: String?): Outcome<CommentReplies> =
        network.replies(postId, commentId, REPLIES_PER_PAGE, cursor)
            .map { page -> CommentReplies(page.items.map { it.toModel() }, page.nextCursor) }

    override suspend fun post(postId: String): Outcome<Post> = network.post(postId).map { it.toModel() }

    override suspend fun publish(post: NewPost): Outcome<Post> =
        network.putPost(post.id, post.toBody()).onSuccess { created ->
            feedDao.upsert(NetworkTimelineItem(id = created.id, post = created).toEntity())
            changes.notify(ContentChange.Posts)
        }.map { it.toModel() }

    override suspend fun delete(postId: String): Outcome<Unit> = network.deletePost(postId).onSuccess {
        feedDao.deletePost(postId)
        changes.notify(ContentChange.Posts)
    }

    override suspend fun setLiked(postId: String, liked: Boolean): Outcome<PostLikes> {
        val change = if (liked) 1 else -1
        feedDao.changeLike(postId, liked, change)
        return network.setLiked(postId, liked)
            .onSuccess { feedDao.setLike(postId, it.likedByMe, it.likeCount) }
            .onFailure { feedDao.changeLike(postId, !liked, -change) }
            .map { it.toModel() }
    }

    override suspend fun setReposted(postId: String, reposted: Boolean): Outcome<PostReposts> =
        network.setReposted(postId, reposted).onSuccess { answer ->
            feedDao.setRepost(postId, answer.repostedByMe, answer.repostCount)
            if (!reposted) myId()?.let { feedDao.deleteRepost(postId, it) }
            changes.notify(ContentChange.Posts)
        }.map { it.toModel() }

    override suspend fun setSaved(postId: String, saved: Boolean): Outcome<Unit> =
        network.setSaved(postId, saved).onSuccess {
            feedDao.setSaved(postId, saved)
            changes.notify(ContentChange.Posts)
        }

    override suspend fun addComment(postId: String, text: String, parentId: String?): Outcome<Comment> =
        network.addComment(postId, text.trim(), parentId).onSuccess {
            feedDao.changeCommentCount(postId, 1)
        }.map { it.toModel() }

    override suspend fun deleteComment(postId: String, commentId: String): Outcome<Unit> =
        network.deleteComment(postId, commentId).onSuccess { feedDao.changeCommentCount(postId, -1) }

    override suspend fun reportPost(postId: String, reason: ReportReason, details: String?): Outcome<Unit> =
        report(TARGET_POST, postId, reason, details)

    override suspend fun reportComment(commentId: String, reason: ReportReason, details: String?): Outcome<Unit> =
        report(TARGET_COMMENT, commentId, reason, details)

    private suspend fun report(target: String, id: String, reason: ReportReason, details: String?) =
        social.report(ReportBody(target, id, reason.name, details?.trim()?.ifEmpty { null })).map { }

    private fun myId(): String? = (sessions.sessionState.value as? SessionState.SignedIn)?.user?.id

    private companion object {
        const val FEED_PAGE_SIZE = 20
        const val TARGET_POST = "Post"
        const val TARGET_COMMENT = "Comment"
        const val REPLIES_PER_PAGE = 20
    }
}

/** Brings the pages of the feed from the server to Room, which is what the screen reads. */
@OptIn(ExperimentalPagingApi::class)
internal class FeedRemoteMediator(private val feed: FeedDao, private val network: PostsNetworkDataSource) :
    RemoteMediator<Int, FeedItemEntity>() {
    override suspend fun load(loadType: LoadType, state: PagingState<Int, FeedItemEntity>): MediatorResult {
        val isRefresh = loadType == LoadType.REFRESH
        val cursor = if (isRefresh) null else feed.pageKey()?.nextCursor
        val isAtTheEnd = loadType == LoadType.PREPEND || (loadType == LoadType.APPEND && cursor == null)
        if (isAtTheEnd) return MediatorResult.Success(endOfPaginationReached = true)

        return when (val result = network.feed(state.config.pageSize, cursor)) {
            is Outcome.Failure -> MediatorResult.Error(RemoteLoadException(result.error))

            is Outcome.Success -> {
                val items = result.value.items.map { it.toEntity() }
                if (isRefresh) {
                    feed.replaceFirstPage(items, result.value.nextCursor)
                } else {
                    feed.appendPage(items, result.value.nextCursor)
                }
                MediatorResult.Success(endOfPaginationReached = result.value.nextCursor == null)
            }
        }
    }
}
