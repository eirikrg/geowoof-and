package com.geowoof.core.network

import com.geowoof.core.common.Dispatcher
import com.geowoof.core.common.GeoWoofDispatchers
import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkMediaAsset
import com.geowoof.core.network.model.NetworkMediaUpload
import com.geowoof.core.network.model.UploadRequestBody
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import javax.inject.Inject
import javax.inject.Qualifier

/** The client for signed storage links. It has no authenticator: an Authorization header would break the signature. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class StorageClient

/** Media endpoints. */
internal interface MediaApi {
    @POST("api/v1/media/uploads")
    suspend fun requestUpload(@Body body: UploadRequestBody): NetworkMediaUpload

    @POST("api/v1/media/uploads/{id}/complete")
    suspend fun completeUpload(@Path("id") id: String): NetworkMediaAsset

    @GET("api/v1/media/uploads/{id}")
    suspend fun asset(@Path("id") id: String): NetworkMediaAsset
}

interface MediaNetworkDataSource {
    suspend fun requestUpload(body: UploadRequestBody): Outcome<NetworkMediaUpload>

    /** Sends the bytes directly to storage with the signed link. */
    suspend fun upload(upload: NetworkMediaUpload, content: ByteArray): Outcome<Unit>

    suspend fun completeUpload(assetId: String): Outcome<NetworkMediaAsset>

    suspend fun asset(assetId: String): Outcome<NetworkMediaAsset>
}

internal class RetrofitMediaNetworkDataSource @Inject constructor(
    private val api: MediaApi,
    @StorageClient private val storageClient: OkHttpClient,
    private val json: Json,
    @Dispatcher(GeoWoofDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : MediaNetworkDataSource {
    override suspend fun requestUpload(body: UploadRequestBody) = apiCall(json) { api.requestUpload(body) }

    override suspend fun completeUpload(assetId: String) = apiCall(json) { api.completeUpload(assetId) }

    override suspend fun asset(assetId: String) = apiCall(json) { api.asset(assetId) }

    override suspend fun upload(upload: NetworkMediaUpload, content: ByteArray): Outcome<Unit> =
        withContext(ioDispatcher) { put(upload, content) }

    private fun put(upload: NetworkMediaUpload, content: ByteArray): Outcome<Unit> {
        val contentType = upload.uploadHeaders.entries
            .first { it.key.equals(CONTENT_TYPE, ignoreCase = true) }
            .value
        val request = Request.Builder()
            .url(upload.uploadUrl)
            .put(content.toRequestBody(contentType.toMediaType()))
            .build()
        return try {
            storageClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Outcome.Success(Unit)
                } else {
                    Outcome.Failure(AppError.Api(status = response.code, code = UPLOAD_FAILED_CODE))
                }
            }
        } catch (@Suppress("SwallowedException") exception: IOException) {
            Outcome.Failure(AppError.Network)
        }
    }

    private companion object {
        const val CONTENT_TYPE = "Content-Type"
        const val UPLOAD_FAILED_CODE = "media.upload_failed"
    }
}
