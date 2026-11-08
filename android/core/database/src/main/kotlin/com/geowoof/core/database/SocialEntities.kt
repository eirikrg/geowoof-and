package com.geowoof.core.database

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// The lists that are only about the signed-in person. Followers and friends are not kept here, because
// they can be very long and other people change them all the time.

/** What a list shows of the other person. */
data class UserColumns(
    val userId: String,
    val username: String,
    val displayName: String,
    val city: String?,
    val avatarAssetId: String?,
    val avatarThumbUrl: String?,
    val avatarMediumUrl: String?,
)

@Entity(tableName = "follow_requests", indices = [Index("direction"), Index("userId")])
data class FollowRequestEntity(
    @PrimaryKey val id: String,
    @Embedded val user: UserColumns,
    val direction: String,
    val createdAtEpochMs: Long,
)

@Entity(tableName = "blocked_users", primaryKeys = ["userId"])
data class BlockedUserEntity(@Embedded val user: UserColumns, val sinceEpochMs: Long)

@Dao
abstract class SocialDao {
    @Query("SELECT * FROM follow_requests WHERE direction = :direction ORDER BY createdAtEpochMs DESC, id DESC")
    abstract fun observeRequests(direction: String): Flow<List<FollowRequestEntity>>

    @Query("SELECT * FROM blocked_users ORDER BY sinceEpochMs DESC, userId DESC")
    abstract fun observeBlocked(): Flow<List<BlockedUserEntity>>

    @Query("DELETE FROM follow_requests WHERE id = :id")
    abstract suspend fun deleteRequest(id: String)

    @Query("DELETE FROM follow_requests WHERE userId = :userId")
    abstract suspend fun deleteRequestsWith(userId: String)

    @Query("DELETE FROM follow_requests WHERE userId = :userId AND direction = :direction")
    abstract suspend fun deleteRequestsWith(userId: String, direction: String)

    @Query("DELETE FROM blocked_users WHERE userId = :userId")
    abstract suspend fun deleteBlocked(userId: String)

    /** What is no longer on the server goes away, and the screens see it all change at once. */
    @Transaction
    open suspend fun replaceRequests(direction: String, requests: List<FollowRequestEntity>) {
        clearRequests(direction)
        upsertRequests(requests)
    }

    @Transaction
    open suspend fun replaceBlocked(blocked: List<BlockedUserEntity>) {
        clearBlocked()
        upsertBlocked(blocked)
    }

    @Query("DELETE FROM follow_requests WHERE direction = :direction")
    protected abstract suspend fun clearRequests(direction: String)

    @Upsert
    protected abstract suspend fun upsertRequests(requests: List<FollowRequestEntity>)

    @Query("DELETE FROM blocked_users")
    protected abstract suspend fun clearBlocked()

    @Upsert
    protected abstract suspend fun upsertBlocked(blocked: List<BlockedUserEntity>)
}
