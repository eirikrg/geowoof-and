package com.geowoof.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Someone opened from the search. Only the identifier, because what can be seen of another person comes from the server
 * each time, so that a block or a change of visibility counts at once. The highest [position] is the most recent.
 */
@Entity(tableName = "recent_people")
data class RecentPersonEntity(@PrimaryKey val userId: String, val position: Long)

@Dao
interface RecentPersonDao {
    @Query("SELECT userId FROM recent_people ORDER BY position DESC LIMIT :limit")
    fun observeIds(limit: Int): Flow<List<String>>

    /** Also when they were already there: they go back to the top. */
    @Query(
        "INSERT OR REPLACE INTO recent_people (userId, position) " +
            "VALUES (:userId, (SELECT COALESCE(MAX(position), 0) + 1 FROM recent_people))",
    )
    suspend fun add(userId: String)

    @Query(
        "DELETE FROM recent_people WHERE userId NOT IN " +
            "(SELECT userId FROM recent_people ORDER BY position DESC LIMIT :limit)",
    )
    suspend fun keepOnly(limit: Int)

    @Query("DELETE FROM recent_people WHERE userId = :userId")
    suspend fun delete(userId: String)

    @Query("DELETE FROM recent_people")
    suspend fun deleteAll()
}
