package com.geowoof.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.paging.map
import com.geowoof.core.common.Dispatcher
import com.geowoof.core.common.GeoWoofDispatchers
import com.geowoof.core.data.mapper.toBundle
import com.geowoof.core.data.mapper.toEntity
import com.geowoof.core.data.mapper.toModel
import com.geowoof.core.data.mapper.toRouteSegments
import com.geowoof.core.data.media.WalkPhotoStore
import com.geowoof.core.database.MY_WALKS_KEY
import com.geowoof.core.database.WalkDao
import com.geowoof.core.database.WalkWithChildren
import com.geowoof.core.model.AppError
import com.geowoof.core.model.NewWalk
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.RouteSegment
import com.geowoof.core.model.Walk
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.WalkTotals
import com.geowoof.core.model.map
import com.geowoof.core.network.WalksNetworkDataSource
import com.geowoof.core.network.model.NetworkPage
import com.geowoof.core.network.model.NetworkWalk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Uploads the walks in the background. It lives in `:sync:work`; the data layer only asks for it. */
interface WalkSyncScheduler {
    /** Runs when there is a connection, and it replaces the pending upload of that walk. */
    fun enqueue(walkId: String)

    fun cancelAll()
}

/** A list of walks could not be updated. What Room already has stays on screen. */
class WalkLoadException(val error: AppError) : Exception("The walks could not be loaded: $error")

/** Saved walks: read from Room, and written in a queue that works without a connection (docs/decisions/0009). */
interface WalksRepository {
    /** The walks of the person, or those of one of their pets, from the most recent to the oldest. */
    fun walks(petId: String? = null): Flow<PagingData<Walk>>

    fun observeWalk(walkId: String): Flow<Walk?>

    /** `null` until the full route is on the device: see [refreshWalk]. */
    fun observeRoute(walkId: String): Flow<List<RouteSegment>?>

    suspend fun refreshWalk(walkId: String): Outcome<Unit>

    fun observeTotals(petId: String? = null): Flow<WalkTotals?>

    suspend fun refreshTotals(petId: String? = null): Outcome<Unit>

    /** Keeps a finished walk, ends the active one and asks for the upload. It needs no connection. */
    suspend fun savePending(walk: NewWalk): Outcome<String>

    /** Tries again with a walk that the server rejected. */
    suspend fun retrySync(walkId: String)

    /** It disappears at once, and the server finds out as soon as there is a connection. */
    suspend fun delete(walkId: String)

    suspend fun hasUnsyncedWalks(): Boolean
}

internal class DefaultWalksRepository @Inject constructor(
    private val walks: WalkDao,
    private val network: WalksNetworkDataSource,
    private val photos: WalkPhotoStore,
    private val scheduler: WalkSyncScheduler,
    @Dispatcher(GeoWoofDispatchers.Default) private val defaultDispatcher: CoroutineDispatcher,
) : WalksRepository {
    @OptIn(ExperimentalPagingApi::class)
    override fun walks(petId: String?): Flow<PagingData<Walk>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE),
        remoteMediator = WalkRemoteMediator(petId, walks, network),
        pagingSourceFactory = { walkSource(petId) },
    ).flow.map { page -> page.map { it.toModel() } }

    private fun walkSource(petId: String?): PagingSource<Int, WalkWithChildren> =
        if (petId == null) walks.pagingSource() else walks.pagingSourceForPet(petId)

    override fun observeWalk(walkId: String): Flow<Walk?> = walks.observe(walkId).map { it?.toModel() }

    override fun observeRoute(walkId: String): Flow<List<RouteSegment>?> =
        walks.observeTrack(walkId).map { it?.toRouteSegments() }.flowOn(defaultDispatcher)

    override suspend fun refreshWalk(walkId: String): Outcome<Unit> {
        val result = network.walk(walkId)
        if (result is Outcome.Failure && (result.error as? AppError.Api)?.status == HTTP_NOT_FOUND) {
            removeIfSynced(walkId)
        }

        return result.map { walks.storeFromServer(listOf(it.toBundle())) }
    }

    /** It was deleted from another device. One that has not been uploaded yet is still expected to get there. */
    private suspend fun removeIfSynced(walkId: String) {
        if (walks.get(walkId)?.walk?.syncStatus == WalkSyncStatus.Synced.name) walks.delete(walkId)
    }

    override fun observeTotals(petId: String?): Flow<WalkTotals?> =
        walks.observeTotals(petId ?: MY_WALKS_KEY).map { it?.toModel() }

    override suspend fun refreshTotals(petId: String?): Outcome<Unit> {
        val result = if (petId == null) network.myStats() else network.petStats(petId)
        return result.map { walks.storeTotals(it.toEntity(petId ?: MY_WALKS_KEY)) }
    }

    override suspend fun savePending(walk: NewWalk): Outcome<String> =
        photos.store(walk.id, walk.photoUris).map { paths ->
            walks.savePendingAndClearActive(walk.toBundle(paths))
            scheduler.enqueue(walk.id)
            walk.id
        }

    override suspend fun retrySync(walkId: String) {
        walks.setSyncStatus(walkId, WalkSyncStatus.PendingSync.name, errorCode = null)
        scheduler.enqueue(walkId)
    }

    override suspend fun delete(walkId: String) {
        walks.setSyncStatus(walkId, WalkSyncStatus.PendingDelete.name, errorCode = null)
        scheduler.enqueue(walkId)
    }

    override suspend fun hasUnsyncedWalks(): Boolean = walks.countUnsynced() > 0

    private companion object {
        const val PAGE_SIZE = 20
        const val HTTP_NOT_FOUND = 404
    }
}

/** Brings the pages of the server to Room, which is what the list reads (docs/decisions/0009). */
@OptIn(ExperimentalPagingApi::class)
internal class WalkRemoteMediator(
    private val petId: String?,
    private val walks: WalkDao,
    private val network: WalksNetworkDataSource,
) : RemoteMediator<Int, WalkWithChildren>() {
    private val listKey = petId ?: MY_WALKS_KEY

    override suspend fun load(loadType: LoadType, state: PagingState<Int, WalkWithChildren>): MediatorResult {
        val isRefresh = loadType == LoadType.REFRESH
        val cursor = if (isRefresh) null else walks.pageKey(listKey)?.nextCursor
        val isAtTheEnd = loadType == LoadType.PREPEND || (loadType == LoadType.APPEND && cursor == null)
        return if (isAtTheEnd) {
            MediatorResult.Success(endOfPaginationReached = true)
        } else {
            loadPage(cursor, state.config.pageSize, isRefresh)
        }
    }

    private suspend fun loadPage(cursor: String?, pageSize: Int, isRefresh: Boolean): MediatorResult {
        val result = if (petId == null) {
            network.myWalks(pageSize, cursor)
        } else {
            network.petWalks(petId, pageSize, cursor)
        }
        return when (result) {
            is Outcome.Failure -> MediatorResult.Error(WalkLoadException(result.error))

            is Outcome.Success -> {
                store(result.value, isRefresh)
                MediatorResult.Success(endOfPaginationReached = result.value.nextCursor == null)
            }
        }
    }

    private suspend fun store(page: NetworkPage<NetworkWalk>, isRefresh: Boolean) {
        val bundles = page.items.map { it.toBundle() }
        when {
            !isRefresh -> walks.appendPage(listKey, bundles, page.nextCursor)
            petId == null -> walks.replaceFirstPage(bundles, page.nextCursor)
            else -> walks.replaceFirstPageForPet(petId, bundles, page.nextCursor)
        }
    }
}
