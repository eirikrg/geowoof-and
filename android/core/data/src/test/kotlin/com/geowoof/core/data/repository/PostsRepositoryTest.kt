package com.geowoof.core.data.repository

import android.content.Context
import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.database.FeedItemEntity
import com.geowoof.core.database.GeoWoofDatabase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.NewPost
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PostVisibility
import com.geowoof.core.model.ReportReason
import com.geowoof.core.network.PostsNetworkDataSource
import com.geowoof.core.network.SocialNetworkDataSource
import com.geowoof.core.network.model.NetworkBlock
import com.geowoof.core.network.model.NetworkComment
import com.geowoof.core.network.model.NetworkFollowRequest
import com.geowoof.core.network.model.NetworkMention
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkPost
import com.geowoof.core.network.model.NetworkPostLike
import com.geowoof.core.network.model.NetworkPostMedia
import com.geowoof.core.network.model.NetworkPostRepost
import com.geowoof.core.network.model.NetworkRelationship
import com.geowoof.core.network.model.NetworkReport
import com.geowoof.core.network.model.NetworkTaggedPet
import com.geowoof.core.network.model.NetworkTimelineItem
import com.geowoof.core.network.model.NetworkUserSummary
import com.geowoof.core.network.model.PostBody
import com.geowoof.core.network.model.ReportBody
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val TIME = "2026-11-05T10:00:00Z"

private fun author(id: String = "bea-id", username: String = "bea") =
    NetworkUserSummary(id, username, username.replaceFirstChar(Char::uppercase), "Madrid", null)

private fun networkPost(
    id: String = "post-1",
    author: NetworkUserSummary = author(),
    likeCount: Int = 2,
    likedByMe: Boolean = false,
    createdAt: String = TIME,
) = NetworkPost(
    id = id,
    author = author,
    text = "Con @ana",
    visibility = "Everyone",
    media = listOf(
        NetworkPostMedia("Image", "asset-1", "https://thumb", "https://medium"),
        NetworkPostMedia("Video", "asset-2"),
    ),
    pets = listOf(NetworkTaggedPet("luna", "Luna")),
    mentions = listOf(NetworkMention("ana", "ana-id")),
    likeCount = likeCount,
    commentCount = 1,
    repostCount = 0,
    likedByMe = likedByMe,
    canRepost = true,
    createdAt = createdAt,
)

private fun timelineItem(post: NetworkPost = networkPost()) = NetworkTimelineItem(post.id, post)

@Suppress("TooManyFunctions")
private class FakePostsNetwork : PostsNetworkDataSource {
    var feedPages = mutableMapOf<String?, Outcome<NetworkPage<NetworkTimelineItem>>>()
    var likeAnswer: Outcome<NetworkPostLike> = Outcome.Success(NetworkPostLike(3, true))
    var repostAnswer: Outcome<NetworkPostRepost> = Outcome.Success(NetworkPostRepost(1, true))
    var unitAnswer: Outcome<Unit> = Outcome.Success(Unit)
    var putAnswer: Outcome<NetworkPost> = Outcome.Success(networkPost())
    val bodies = mutableListOf<PostBody>()
    val comments = mutableListOf<String>()

    override suspend fun putPost(id: String, body: PostBody): Outcome<NetworkPost> {
        bodies += body
        return putAnswer
    }

    override suspend fun post(id: String) = Outcome.Success(networkPost(id))

    override suspend fun deletePost(id: String) = unitAnswer

    override suspend fun setLiked(id: String, liked: Boolean) = likeAnswer

    override suspend fun comments(id: String, limit: Int, cursor: String?): Outcome<NetworkPage<NetworkComment>> =
        Outcome.Success(NetworkPage(emptyList(), null))

    override suspend fun replies(id: String, commentId: String, limit: Int, cursor: String?) = Outcome.Success(
        NetworkPage(
            listOf(NetworkComment("reply-1", id, author(), "@bea hola", createdAt = TIME, parentId = commentId)),
            "next",
        ),
    )

    override suspend fun addComment(id: String, text: String, parentId: String?): Outcome<NetworkComment> {
        comments += listOfNotNull(parentId, text).joinToString(":")
        return Outcome.Success(NetworkComment("comment-1", id, author(), text, createdAt = TIME, parentId = parentId))
    }

    override suspend fun deleteComment(id: String, commentId: String) = unitAnswer

    override suspend fun setReposted(id: String, reposted: Boolean) = repostAnswer

    override suspend fun setSaved(id: String, saved: Boolean) = unitAnswer

    override suspend fun savedPosts(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkPost>> =
        Outcome.Success(NetworkPage(emptyList(), null))

    override suspend fun feed(limit: Int, cursor: String?) = feedPages.getValue(cursor)

    override suspend fun explore(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkPost>> =
        Outcome.Success(NetworkPage(emptyList(), null))

    override suspend fun userPosts(
        userId: String,
        limit: Int,
        cursor: String?,
    ): Outcome<NetworkPage<NetworkTimelineItem>> = Outcome.Success(NetworkPage(emptyList(), null))
}

@Suppress("TooManyFunctions")
private class ReportsOnlyNetwork : SocialNetworkDataSource {
    val reports = mutableListOf<ReportBody>()

    override suspend fun report(body: ReportBody): Outcome<NetworkReport> {
        reports += body
        return Outcome.Success(NetworkReport("report-1"))
    }

    override suspend fun follow(userId: String): Outcome<NetworkRelationship> = unused()

    override suspend fun unfollow(userId: String): Outcome<NetworkRelationship> = unused()

    override suspend fun removeFollower(userId: String): Outcome<NetworkRelationship> = unused()

    override suspend fun receivedRequests(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkFollowRequest>> =
        unused()

    override suspend fun sentRequests(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkFollowRequest>> =
        unused()

    override suspend fun acceptRequest(id: String): Outcome<NetworkRelationship> = unused()

    override suspend fun rejectRequest(id: String): Outcome<Unit> = unused()

    override suspend fun cancelRequest(id: String): Outcome<Unit> = unused()

    override suspend fun blocks(limit: Int, cursor: String?): Outcome<NetworkPage<NetworkBlock>> = unused()

    override suspend fun block(userId: String): Outcome<Unit> = unused()

    override suspend fun unblock(userId: String): Outcome<Unit> = unused()

    private fun unused(): Nothing = error("Not for reports")
}

@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class PostsRepositoryTest {
    private val database = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        GeoWoofDatabase::class.java,
    ).allowMainThreadQueries().build()
    private val feedDao = database.feedDao()
    private val network = FakePostsNetwork()
    private val social = ReportsOnlyNetwork()
    private val changes = ContentChanges()

    @After
    fun closeDatabase() = database.close()

    private suspend fun TestScope.repository(): DefaultPostsRepository {
        val sessions = SessionManager(InMemorySessionStorage(), NoUserData, backgroundScope)
        runCurrent()
        sessions.start(networkSession(user = networkUser(username = "ana")))
        runCurrent()
        return DefaultPostsRepository(network, social, feedDao, sessions, changes)
    }

    private fun emptyState() = PagingState<Int, FeedItemEntity>(emptyList(), null, PagingConfig(pageSize = 20), 0)

    /** What the feed has in Room, in the order that the screen shows it. */
    private suspend fun storedFeed(): List<FeedItemEntity> {
        val source = feedDao.pagingSource()
        val page = source.load(PagingSource.LoadParams.Refresh(null, 100, false)) as PagingSource.LoadResult.Page
        return page.data
    }

    @Test
    fun the_first_page_replaces_the_feed_and_the_next_ones_add_to_it() = runTest {
        val mediator = FeedRemoteMediator(feedDao, network)
        network.feedPages[null] = Outcome.Success(NetworkPage(listOf(timelineItem(networkPost("old"))), null))
        mediator.load(LoadType.REFRESH, emptyState())
        network.feedPages[null] = Outcome.Success(
            NetworkPage(listOf(timelineItem(networkPost("newest", createdAt = "2026-11-05T12:00:00Z"))), "next"),
        )
        network.feedPages["next"] = Outcome.Success(
            NetworkPage(listOf(timelineItem(networkPost("older", createdAt = "2026-11-05T09:00:00Z"))), null),
        )

        val first = mediator.load(LoadType.REFRESH, emptyState()) as RemoteMediator.MediatorResult.Success
        assertFalse(first.endOfPaginationReached)
        assertEquals(listOf("newest"), storedFeed().map { it.postId })

        val second = mediator.load(LoadType.APPEND, emptyState()) as RemoteMediator.MediatorResult.Success
        assertTrue(second.endOfPaginationReached)
        assertEquals(listOf("newest", "older"), storedFeed().map { it.postId })
    }

    @Test
    fun at_the_end_of_the_feed_nothing_more_is_asked() = runTest {
        val mediator = FeedRemoteMediator(feedDao, network)
        network.feedPages[null] = Outcome.Success(NetworkPage(listOf(timelineItem()), null))
        mediator.load(LoadType.REFRESH, emptyState())

        val result = mediator.load(LoadType.APPEND, emptyState()) as RemoteMediator.MediatorResult.Success

        assertTrue(result.endOfPaginationReached)
    }

    @Test
    fun without_a_connection_the_feed_stays_as_it_was_and_says_why() = runTest {
        val mediator = FeedRemoteMediator(feedDao, network)
        network.feedPages[null] = Outcome.Success(NetworkPage(listOf(timelineItem()), null))
        mediator.load(LoadType.REFRESH, emptyState())
        network.feedPages[null] = Outcome.Failure(AppError.Network)

        val result = mediator.load(LoadType.REFRESH, emptyState()) as RemoteMediator.MediatorResult.Error

        assertEquals(AppError.Network, (result.throwable as RemoteLoadException).error)
        assertEquals(1, storedFeed().size)
    }

    @Test
    fun a_post_comes_out_of_room_as_it_went_in() = runTest {
        val repostedBy = author("ana-id", "ana")
        val item = NetworkTimelineItem("repost-1", networkPost(), repostedBy, "2026-11-05T11:00:00Z")

        val stored = item.toEntity()
        val model = stored.toModel()

        assertEquals("repost-1", model.id)
        assertEquals("post-1", model.post.id)
        assertEquals("bea", model.post.author.username)
        assertEquals("ana", model.repostedBy?.username)
        assertEquals(java.time.Instant.parse("2026-11-05T11:00:00Z").toEpochMilli(), stored.atEpochMs)
        assertEquals(listOf("Luna"), model.post.pets.map { it.name })
        assertEquals("ana-id", model.post.mentions.single().userId)
        assertEquals(PostVisibility.Everyone, model.post.visibility)
    }

    @Test
    fun what_this_version_cannot_show_is_left_out() {
        val post = networkPost().toModel()

        assertEquals(listOf("asset-1"), post.media.map { it.assetId })
    }

    @Test
    fun a_visibility_of_a_newer_api_counts_as_the_most_limited() {
        assertEquals(PostVisibility.Friends, networkPost().copy(visibility = "Something").toModel().visibility)
    }

    @Test
    fun a_like_changes_the_feed_at_once_and_takes_the_number_of_the_server() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem().toEntity())

        val answer = repository.setLiked("post-1", liked = true)

        assertEquals(3, (answer as Outcome.Success).value.count)
        assertEquals(3, storedFeed().single().likeCount)
        assertTrue(storedFeed().single().likedByMe)
    }

    @Test
    fun a_like_that_fails_goes_back() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem().toEntity())
        network.likeAnswer = Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Failure(AppError.Network), repository.setLiked("post-1", liked = true))

        assertEquals(2, storedFeed().single().likeCount)
        assertFalse(storedFeed().single().likedByMe)
    }

    @Test
    fun a_removed_like_that_fails_also_goes_back() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem(networkPost(likedByMe = true)).toEntity())
        network.likeAnswer = Outcome.Failure(AppError.Network)

        repository.setLiked("post-1", liked = false)

        assertEquals(2, storedFeed().single().likeCount)
        assertTrue(storedFeed().single().likedByMe)
    }

    @Test
    fun undoing_a_repost_takes_your_own_out_of_the_feed_and_leaves_the_rest() = runTest {
        val repository = repository()
        val me = networkUser(username = "ana").id
        feedDao.upsert(timelineItem().toEntity())
        feedDao.upsert(NetworkTimelineItem("mine", networkPost(), author(me, "ana"), TIME).toEntity())
        feedDao.upsert(NetworkTimelineItem("theirs", networkPost(), author("carlos-id", "carlos"), TIME).toEntity())
        network.repostAnswer = Outcome.Success(NetworkPostRepost(1, false))

        repository.setReposted("post-1", reposted = false)

        assertEquals(setOf("post-1", "theirs"), storedFeed().map { it.id }.toSet())
        assertTrue(storedFeed().all { !it.repostedByMe && it.repostCount == 1 })
    }

    @Test
    fun what_is_published_appears_in_the_feed_without_waiting() = runTest {
        val repository = repository()
        val post = NewPost("post-1", "  Hola  ", PostVisibility.Friends, listOf("asset-1"), listOf("luna"))

        val answer = repository.publish(post)

        assertEquals("post-1", (answer as Outcome.Success).value.id)
        assertEquals(PostBody("Hola", "Friends", listOf("asset-1"), listOf("luna")), network.bodies.single())
        assertEquals(listOf("post-1"), storedFeed().map { it.postId })
    }

    @Test
    fun publishing_tells_the_lists_and_a_like_does_not_because_room_has_the_numbers() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem().toEntity())

        repository.setLiked("post-1", liked = true)
        assertEquals(0, changes.of(ContentChange.Posts).first())
        repository.publish(NewPost("post-2", "Hola", PostVisibility.Friends, listOf("asset-1"), emptyList()))

        assertEquals(1, changes.of(ContentChange.Posts).first())
    }

    @Test
    fun a_post_without_a_text_sends_nothing_instead_of_spaces() = runTest {
        repository().publish(NewPost("post-1", "   ", PostVisibility.Everyone, listOf("asset-1"), emptyList()))

        assertNull(network.bodies.single().text)
    }

    @Test
    fun deleting_a_post_takes_it_out_of_the_feed_only_if_the_server_agrees() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem().toEntity())
        network.unitAnswer = Outcome.Failure(AppError.Network)

        repository.delete("post-1")
        assertEquals(1, storedFeed().size)

        network.unitAnswer = Outcome.Success(Unit)
        repository.delete("post-1")
        assertEquals(0, storedFeed().size)
    }

    @Test
    fun a_comment_changes_the_number_of_the_feed() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem().toEntity())

        repository.addComment("post-1", "  Hola  ")
        assertEquals(listOf("Hola"), network.comments)
        assertEquals(2, storedFeed().single().commentCount)

        repository.deleteComment("post-1", "comment-1")
        assertEquals(1, storedFeed().single().commentCount)
    }

    /** A reply is one more comment of the post, as the server counts them. */
    @Test
    fun a_reply_goes_to_its_comment_and_counts_as_a_comment() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem().toEntity())

        val reply = repository.addComment("post-1", " @bea hola ", parentId = "comment-1")

        assertEquals(listOf("comment-1:@bea hola"), network.comments)
        assertEquals("comment-1", (reply as Outcome.Success).value.parentId)
        assertEquals(2, storedFeed().single().commentCount)
    }

    @Test
    fun the_replies_of_a_comment_come_page_by_page() = runTest {
        val page = repository().replies("post-1", "comment-1", cursor = null) as Outcome.Success

        assertEquals("comment-1", page.value.items.single().parentId)
        assertEquals("comment-1", page.value.items.single().threadId)
        assertEquals("next", page.value.nextCursor)
    }

    @Test
    fun saving_changes_the_feed() = runTest {
        val repository = repository()
        feedDao.upsert(timelineItem().toEntity())

        repository.setSaved("post-1", saved = true)

        assertTrue(storedFeed().single().savedByMe)
    }

    @Test
    fun a_report_says_what_it_is_about() = runTest {
        val repository = repository()

        repository.reportPost("post-1", ReportReason.Spam, "  ")
        repository.reportComment("comment-1", ReportReason.Harassment, " insiste ")

        assertEquals(ReportBody("Post", "post-1", "Spam", null), social.reports[0])
        assertEquals(ReportBody("Comment", "comment-1", "Harassment", "insiste"), social.reports[1])
    }

    @Test
    fun blocking_someone_takes_what_they_published_and_reposted_out_of_the_feed() = runTest {
        feedDao.upsert(timelineItem(networkPost("theirs")).toEntity())
        feedDao.upsert(
            NetworkTimelineItem("reposted", networkPost("other", author("ana-id", "ana")), author(), TIME).toEntity(),
        )
        feedDao.upsert(timelineItem(networkPost("stays", author("carlos-id", "carlos"))).toEntity())

        feedDao.deleteAllOf("bea-id")

        assertEquals(listOf("stays"), storedFeed().map { it.postId })
    }
}
