package com.geowoof.core.data.repository

import com.geowoof.core.database.RecentPersonDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * The people opened from the search, on this device. It is cleared with the rest of the data when the session ends, and
 * it only keeps who they are: their profile is asked for each time, and the server may no longer show it.
 */
interface RecentPeopleRepository {
    /** The last one first. */
    val ids: Flow<List<String>>

    suspend fun add(userId: String)

    suspend fun remove(userId: String)

    suspend fun removeAll()
}

internal class DefaultRecentPeopleRepository @Inject constructor(private val dao: RecentPersonDao) :
    RecentPeopleRepository {
    override val ids: Flow<List<String>> = dao.observeIds(MAX_PEOPLE)

    override suspend fun add(userId: String) {
        dao.add(userId)
        dao.keepOnly(MAX_PEOPLE)
    }

    override suspend fun remove(userId: String) = dao.delete(userId)

    override suspend fun removeAll() = dao.deleteAll()

    private companion object {
        const val MAX_PEOPLE = 10
    }
}
