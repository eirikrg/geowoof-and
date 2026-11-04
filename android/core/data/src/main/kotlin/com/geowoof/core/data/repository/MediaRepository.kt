package com.geowoof.core.data.repository

import com.geowoof.core.data.media.ImageCompressor
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.flatMap
import com.geowoof.core.network.MediaNetworkDataSource
import com.geowoof.core.network.model.NetworkMediaAsset
import com.geowoof.core.network.model.UploadRequestBody
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** Uploads images directly to storage with signed links (docs/decisions/0010). */
interface MediaRepository {
    /**
     * Compresses the picked image, uploads it and waits a moment for the server to process it.
     * Returns the id to attach to a profile or a pet. An image that takes too long is not an
     * error: it can be attached anyway, and it appears once the server has finished.
     */
    suspend fun uploadImage(contentUri: String, purpose: MediaPurpose): Outcome<String>
}

internal class DefaultMediaRepository @Inject constructor(
    private val network: MediaNetworkDataSource,
    private val compressor: ImageCompressor,
) : MediaRepository {
    override suspend fun uploadImage(contentUri: String, purpose: MediaPurpose): Outcome<String> =
        compressor.compress(contentUri).flatMap { image ->
            val body = UploadRequestBody(image.contentType, image.bytes.size.toLong(), purpose.name)
            network.requestUpload(body).flatMap { upload ->
                network.upload(upload, image.bytes).flatMap { network.completeUpload(upload.assetId) }
            }
        }.flatMap { awaitProcessing(it) }

    private suspend fun awaitProcessing(completed: NetworkMediaAsset): Outcome<String> {
        var asset = completed
        var polls = 0
        while (asset.status == PROCESSING && polls < MAX_POLLS) {
            delay(POLL_INTERVAL)
            polls++

            // The upload itself worked: if this check cannot be made, the image can be attached anyway.
            asset = (network.asset(asset.id) as? Outcome.Success)?.value ?: return Outcome.Success(asset.id)
        }

        return if (asset.status == REJECTED) {
            Outcome.Failure(AppError.Api(status = HTTP_UNPROCESSABLE, code = asset.rejectionCode ?: DEFAULT_REJECTION))
        } else {
            Outcome.Success(asset.id)
        }
    }

    private companion object {
        const val PROCESSING = "Processing"
        const val REJECTED = "Rejected"
        const val DEFAULT_REJECTION = "media.invalid_image"
        const val HTTP_UNPROCESSABLE = 422
        const val MAX_POLLS = 20
        val POLL_INTERVAL = 500.milliseconds
    }
}
