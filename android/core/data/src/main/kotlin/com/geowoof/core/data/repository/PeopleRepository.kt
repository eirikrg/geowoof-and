package com.geowoof.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.mapper.toUserWalk
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Person
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetSearchResult
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.UserSummary
import com.geowoof.core.model.UserWalk
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.model.map
import com.geowoof.core.network.PeopleNetworkDataSource
import com.geowoof.core.network.model.NetworkPage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Tells the screen why a page of a list could not be loaded. */
class RemoteLoadException(val error: AppError) : Exception("The list could not be loaded: $error")

/**
 * What can be found and seen of other people. It always comes from the server and it is never kept on
 * the device: what is kept could still be seen after a block or a change of visibility.
 */
interface PeopleRepository {
    fun searchUsers(query: String): Flow<PagingData<UserSummary>>

    fun searchPets(query: String): Flow<PagingData<PetSearchResult>>

    /** A few people for the text after an `@`, while the person writes. */
    suspend fun suggestUsers(query: String): Outcome<List<UserSummary>>

    suspend fun profile(userId: String): Outcome<Profile>

    suspend fun counts(userId: String): Outcome<ProfileCounts>

    /** Only that person and their friends can see these lists. */
    fun people(userId: String, kind: PeopleListKind): Flow<PagingData<Person>>

    suspend fun pets(userId: String): Outcome<List<Pet>>

    fun walks(userId: String): Flow<PagingData<Walk>>

    /** The totals of the walks that the person asking can see. */
    suspend fun walkTotals(userId: String): Outcome<WalkTotals>

    suspend fun walk(walkId: String): Outcome<UserWalk>
}

internal class DefaultPeopleRepository @Inject constructor(private val network: PeopleNetworkDataSource) :
    PeopleRepository {
    override fun searchUsers(query: String): Flow<PagingData<UserSummary>> = cursorPager { limit, cursor ->
        network.searchUsers(query, limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override fun searchPets(query: String): Flow<PagingData<PetSearchResult>> = cursorPager { limit, cursor ->
        network.searchPets(query, limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override suspend fun suggestUsers(query: String): Outcome<List<UserSummary>> =
        network.searchUsers(query, SUGGESTIONS, null).map { page -> page.items.map { it.toModel() } }

    override suspend fun profile(userId: String): Outcome<Profile> =
        network.profile(userId).map { it.toEntity().toModel() }

    override suspend fun counts(userId: String): Outcome<ProfileCounts> = network.counts(userId).map { it.toModel() }

    override fun people(userId: String, kind: PeopleListKind): Flow<PagingData<Person>> = cursorPager { limit, cursor ->
        network.people(userId, kind.name.lowercase(), limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override suspend fun pets(userId: String): Outcome<List<Pet>> =
        network.pets(userId).map { page -> page.items.map { it.toEntity().toModel() } }

    override fun walks(userId: String): Flow<PagingData<Walk>> = cursorPager { limit, cursor ->
        network.walks(userId, limit, cursor).map { page -> page.mapItems { it.toModel() } }
    }

    override suspend fun walkTotals(userId: String): Outcome<WalkTotals> =
        network.walkStats(userId).map { it.toModel() }

    override suspend fun walk(walkId: String): Outcome<UserWalk> = network.walk(walkId).map { it.toUserWalk() }

    private companion object {
        const val SUGGESTIONS = 5
    }
}

private const val PAGE_SIZE = 20

/** A list of the API that is not kept on the device. */
internal fun <T : Any> cursorPager(load: suspend (limit: Int, cursor: String?) -> Outcome<NetworkPage<T>>) = Pager(
    config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false),
    pagingSourceFactory = { CursorPagingSource(load) },
).flow

internal fun <T, R> NetworkPage<T>.mapItems(transform: (T) -> R) = NetworkPage(items.map(transform), nextCursor)

/** Reads the pages of a list of the API, which go forward with a cursor. */
internal class CursorPagingSource<T : Any>(
    private val loadPage: suspend (limit: Int, cursor: String?) -> Outcome<NetworkPage<T>>,
) : PagingSource<String, T>() {
    /** A cursor cannot go back: refreshing starts from the top. */
    override fun getRefreshKey(state: PagingState<String, T>): String? = null

    override suspend fun load(params: LoadParams<String>): LoadResult<String, T> =
        when (val result = loadPage(params.loadSize, params.key)) {
            is Outcome.Failure -> LoadResult.Error(RemoteLoadException(result.error))
            is Outcome.Success -> LoadResult.Page(result.value.items, prevKey = null, nextKey = result.value.nextCursor)
        }
}
