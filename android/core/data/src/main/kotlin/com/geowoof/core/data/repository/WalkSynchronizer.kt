package com.geowoof.core.data.repository

import com.geowoof.core.data.mapper.toBody
import com.geowoof.core.data.mapper.toBundle
import com.geowoof.core.data.media.WalkPhotoStore
import com.geowoof.core.database.WalkDao
import com.geowoof.core.database.WalkWithChildren
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.WalkSyncStatus
import com.geowoof.core.model.flatMap
import com.geowoof.core.model.map
import com.geowoof.core.network.WalksNetworkDataSource
import com.geowoof.core.network.model.NetworkWalk
import javax.inject.Inject

/** How an upload ended, for the worker that runs it. */
sealed interface WalkSyncResult {
    data object Done : WalkSyncResult

    /** No connection or a problem of the server: it can work later. */
    data object Retry : WalkSyncResult

    /** The server rejected it. Trying again would give the same answer, so the user decides. */
    data class Failed(val code: String?) : WalkSyncResult
}

/**
 * Sends a walk to the server: first its photos, then the walk (docs/decisions/0009). Each uploaded
 * photo is remembered, and repeating the `PUT` never duplicates a walk, so a retry is always safe.
 */
interface WalkSynchronizer {
    suspend fun sync(walkId: String): WalkSyncResult
}

internal class DefaultWalkSynchronizer @Inject constructor(
    private val walks: WalkDao,
    private val network: WalksNetworkDataSource,
    private val media: MediaRepository,
    private val photos: WalkPhotoStore,
) : WalkSynchronizer {
    override suspend fun sync(walkId: String): WalkSyncResult {
        val stored = walks.get(walkId)
        return when (stored?.walk?.syncStatus) {
            null -> WalkSyncResult.Done

            // A list of the server may have found it first, if the answer to the upload was lost.
            WalkSyncStatus.Synced.name -> {
                photos.delete(walkId)
                WalkSyncResult.Done
            }

            WalkSyncStatus.PendingDelete.name -> delete(walkId)

            else -> upload(stored)
        }
    }

    private suspend fun delete(walkId: String): WalkSyncResult {
        val result = network.deleteWalk(walkId)
        val isGone = result is Outcome.Success || (result as? Outcome.Failure)?.error.isNotFound()
        return if (isGone) {
            walks.delete(walkId)
            photos.delete(walkId)
            WalkSyncResult.Done
        } else {
            onFailure(walkId, (result as Outcome.Failure).error)
        }
    }

    private suspend fun upload(stored: WalkWithChildren): WalkSyncResult {
        val walkId = stored.walk.id
        val track = walks.track(walkId)
        val result: Outcome<NetworkWalk> = if (track == null) {
            Outcome.Failure(AppError.Unexpected("The walk $walkId has no route."))
        } else {
            uploadPhotos(stored).flatMap { assetIds -> network.putWalk(walkId, stored.toBody(track, assetIds)) }
        }
        return when (result) {
            is Outcome.Failure -> onFailure(walkId, result.error)

            is Outcome.Success -> {
                walks.storeFromServer(listOf(result.value.toBundle()))
                photos.delete(walkId)
                WalkSyncResult.Done
            }
        }
    }

    private suspend fun uploadPhotos(stored: WalkWithChildren): Outcome<List<String>> {
        var assetIds: Outcome<List<String>> = Outcome.Success(emptyList())
        for (photo in stored.photos.sortedBy { it.position }) {
            assetIds = assetIds.flatMap { uploaded ->
                val assetId =
                    photo.assetId?.let { Outcome.Success(it) }
                        ?: uploadPhoto(photo.walkId, photo.position, photo.localPath)
                assetId.map { uploaded + it }
            }
        }

        return assetIds
    }

    private suspend fun uploadPhoto(walkId: String, position: Int, path: String?): Outcome<String> {
        val uploaded = path?.let { media.uploadStoredImage(it, MediaPurpose.WalkPhoto) }
            ?: Outcome.Failure(AppError.Unexpected("The photo $position of the walk $walkId is missing."))
        return uploaded.map { assetId ->
            walks.setPhotoAsset(walkId, position, assetId)
            assetId
        }
    }

    private suspend fun onFailure(walkId: String, error: AppError): WalkSyncResult = if (error.canBeRetried()) {
        WalkSyncResult.Retry
    } else {
        val code = (error as? AppError.Api)?.code
        walks.setSyncStatus(walkId, WalkSyncStatus.Failed.name, code)
        WalkSyncResult.Failed(code)
    }

    private fun AppError?.isNotFound(): Boolean = (this as? AppError.Api)?.status == HTTP_NOT_FOUND

    /** A closed session is retried too: when it really ends, the pending uploads are cancelled. */
    private fun AppError.canBeRetried(): Boolean = when (this) {
        AppError.Network, AppError.SessionExpired -> true
        is AppError.Api -> status >= HTTP_SERVER_ERROR || status == HTTP_TIMEOUT || status == HTTP_TOO_MANY_REQUESTS
        else -> false
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
        const val HTTP_TIMEOUT = 408
        const val HTTP_TOO_MANY_REQUESTS = 429
        const val HTTP_SERVER_ERROR = 500
    }
}
