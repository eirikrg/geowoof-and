package com.geowoof.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

// The feed is the only thing of other people that is kept on the device, so that it opens at once and without a
// connection. Each refresh replaces it, and a block removes that person right away.

/**
 * A post or a repost in the feed. What it contains goes as JSON, because it is only ever read whole. The counts
 * and what the person did with it are columns, so that they can change without reading it.
 */
@Entity(tableName = "feed_items", indices = [Index("postId"), Index("atEpochMs")])
data class FeedItemEntity(
    @PrimaryKey val id: String,
    val postId: String,
    /** When it was published or reposted, which is what orders the feed. */
    val atEpochMs: Long,
    @Embedded(prefix = "author_") val author: UserColumns,
    @Embedded(prefix = "reposter_") val repostedBy: UserColumns?,
    val repostedAtEpochMs: Long?,
    val text: String?,
    val visibility: String,
    val mediaJson: String,
    val petsJson: String,
    val mentionsJson: String,
    val likeCount: Int,
    val commentCount: Int,
    val repostCount: Int,
    val likedByMe: Boolean,
    val savedByMe: Boolean,
    val repostedByMe: Boolean,
    val canRepost: Boolean,
    val createdAtEpochMs: Long,
)

/** Where the feed continues on the server. There is only one row. */
@Entity(tableName = "feed_page_key")
data class FeedPageKeyEntity(@PrimaryKey val id: Int = SINGLE_ROW, val nextCursor: String?) {
    companion object {
        const val SINGLE_ROW = 0
    }
}

@Suppress("TooManyFunctions")
@Dao
abstract class FeedDao {
    @Query("SELECT * FROM feed_items ORDER BY atEpochMs DESC, id DESC")
    abstract fun pagingSource(): PagingSource<Int, FeedItemEntity>

    @Query("SELECT * FROM feed_page_key WHERE id = 0")
    abstract suspend fun pageKey(): FeedPageKeyEntity?

    /** The first page replaces what there was, and the screens see it all change at once. */
    @Transaction
    open suspend fun replaceFirstPage(items: List<FeedItemEntity>, nextCursor: String?) {
        clear()
        upsertAll(items)
        storePageKey(FeedPageKeyEntity(nextCursor = nextCursor))
    }

    @Transaction
    open suspend fun appendPage(items: List<FeedItemEntity>, nextCursor: String?) {
        upsertAll(items)
        storePageKey(FeedPageKeyEntity(nextCursor = nextCursor))
    }

    /** What the person has just published, without waiting for the next refresh. */
    @Upsert
    abstract suspend fun upsert(item: FeedItemEntity)

    @Query("UPDATE feed_items SET likedByMe = :liked, likeCount = likeCount + :change WHERE postId = :postId")
    abstract suspend fun changeLike(postId: String, liked: Boolean, change: Int)

    @Query("UPDATE feed_items SET likedByMe = :liked, likeCount = :count WHERE postId = :postId")
    abstract suspend fun setLike(postId: String, liked: Boolean, count: Int)

    @Query("UPDATE feed_items SET repostedByMe = :reposted, repostCount = :count WHERE postId = :postId")
    abstract suspend fun setRepost(postId: String, reposted: Boolean, count: Int)

    @Query("UPDATE feed_items SET savedByMe = :saved WHERE postId = :postId")
    abstract suspend fun setSaved(postId: String, saved: Boolean)

    @Query("UPDATE feed_items SET commentCount = commentCount + :change WHERE postId = :postId")
    abstract suspend fun changeCommentCount(postId: String, change: Int)

    @Query("DELETE FROM feed_items WHERE postId = :postId")
    abstract suspend fun deletePost(postId: String)

    @Query("DELETE FROM feed_items WHERE postId = :postId AND reposter_userId = :userId")
    abstract suspend fun deleteRepost(postId: String, userId: String)

    /** Published or reposted by that person: after a block, nothing of them can stay on screen. */
    @Query("DELETE FROM feed_items WHERE author_userId = :userId OR reposter_userId = :userId")
    abstract suspend fun deleteAllOf(userId: String)

    @Query("DELETE FROM feed_items")
    protected abstract suspend fun clear()

    @Upsert
    protected abstract suspend fun upsertAll(items: List<FeedItemEntity>)

    @Upsert
    protected abstract suspend fun storePageKey(key: FeedPageKeyEntity)
}
