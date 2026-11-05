package com.geowoof.core.data.repository

import com.geowoof.core.data.media.CompressedImage
import com.geowoof.core.data.media.ImageCompressor
import com.geowoof.core.model.AppError
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.MediaNetworkDataSource
import com.geowoof.core.network.model.NetworkMediaAsset
import com.geowoof.core.network.model.NetworkMediaUpload
import com.geowoof.core.network.model.UploadRequestBody
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRepositoryTest {
    private val compressor = FakeCompressor()
    private val network = FakeMediaNetwork()
    private val repository = DefaultMediaRepository(network, compressor)

    @Test
    fun an_image_is_compressed_requested_uploaded_and_confirmed() = runTest {
        val outcome = repository.uploadImage("content://photo", MediaPurpose.PetAvatar)

        assertEquals(Outcome.Success("asset"), outcome)
        assertEquals("content://photo", compressor.compressed.single())
        assertEquals(UploadRequestBody("image/jpeg", 3, "PetAvatar"), network.requested.single())
        assertEquals(listOf("upload:3", "complete:asset"), network.calls)
    }

    @Test
    fun a_stored_image_is_uploaded_without_compressing_it_again() = runTest {
        val outcome = repository.uploadStoredImage("/files/walk/0.jpg", MediaPurpose.WalkPhoto)

        assertEquals(Outcome.Success("asset"), outcome)
        assertEquals(emptyList<String>(), compressor.compressed)
        assertEquals(UploadRequestBody("image/jpeg", 3, "WalkPhoto"), network.requested.single())
    }

    @Test
    fun it_waits_until_the_server_has_processed_the_image() = runTest {
        network.completed = asset("Processing")
        network.statuses += listOf(asset("Processing"), asset("Ready"))

        val outcome = repository.uploadImage("content://photo", MediaPurpose.PetAvatar)

        assertEquals(Outcome.Success("asset"), outcome)
        assertEquals(2, network.polls)
    }

    @Test
    fun an_image_rejected_by_the_server_is_an_error_with_its_reason() = runTest {
        network.completed = asset("Processing")
        network.statuses += asset("Rejected", rejectionCode = "media.invalid_image")

        val outcome = repository.uploadImage("content://photo", MediaPurpose.PetAvatar)

        assertEquals(Outcome.Failure(AppError.Api(status = 422, code = "media.invalid_image")), outcome)
    }

    @Test
    fun an_image_that_takes_too_long_can_still_be_attached() = runTest {
        network.completed = asset("Processing")
        network.alwaysProcessing = true

        val outcome = repository.uploadImage("content://photo", MediaPurpose.PetAvatar)

        assertEquals(Outcome.Success("asset"), outcome)
        assertEquals(20, network.polls)
    }

    @Test
    fun losing_the_connection_while_waiting_does_not_lose_the_upload() = runTest {
        network.completed = asset("Processing")
        network.pollFailure = AppError.Network

        val outcome = repository.uploadImage("content://photo", MediaPurpose.PetAvatar)

        assertEquals(Outcome.Success("asset"), outcome)
    }

    @Test
    fun a_failure_at_any_step_stops_the_upload() = runTest {
        compressor.failure = AppError.Unexpected("unreadable")
        assertEquals(
            Outcome.Failure(AppError.Unexpected("unreadable")),
            repository.uploadImage("content://photo", MediaPurpose.PetAvatar),
        )
        assertEquals(emptyList<UploadRequestBody>(), network.requested)

        compressor.failure = null
        network.requestFailure = AppError.Network
        assertEquals(
            Outcome.Failure(AppError.Network),
            repository.uploadImage("content://photo", MediaPurpose.PetAvatar),
        )
        assertEquals(emptyList<String>(), network.calls)

        network.requestFailure = null
        network.uploadFailure = AppError.Network
        assertEquals(
            Outcome.Failure(AppError.Network),
            repository.uploadImage("content://photo", MediaPurpose.PetAvatar),
        )
        assertEquals(listOf("upload:3"), network.calls)
    }

    private fun asset(status: String, rejectionCode: String? = null) =
        NetworkMediaAsset("asset", status, rejectionCode = rejectionCode)
}

private class FakeCompressor : ImageCompressor {
    val compressed = mutableListOf<String>()
    var failure: AppError? = null

    override suspend fun compress(contentUri: String): Outcome<CompressedImage> {
        failure?.let { return Outcome.Failure(it) }
        compressed += contentUri
        return Outcome.Success(CompressedImage(byteArrayOf(1, 2, 3), "image/jpeg"))
    }

    override suspend fun readCompressed(path: String): Outcome<CompressedImage> =
        Outcome.Success(CompressedImage(byteArrayOf(1, 2, 3), "image/jpeg"))
}

private class FakeMediaNetwork : MediaNetworkDataSource {
    val requested = mutableListOf<UploadRequestBody>()
    val calls = mutableListOf<String>()
    val statuses = mutableListOf<NetworkMediaAsset>()
    var completed = NetworkMediaAsset("asset", "Ready")
    var alwaysProcessing = false
    var polls = 0
    var requestFailure: AppError? = null
    var uploadFailure: AppError? = null
    var pollFailure: AppError? = null

    override suspend fun requestUpload(body: UploadRequestBody): Outcome<NetworkMediaUpload> {
        requestFailure?.let { return Outcome.Failure(it) }
        requested += body
        return Outcome.Success(
            NetworkMediaUpload(
                "asset",
                "http://storage/put",
                mapOf("Content-Type" to body.contentType),
                "2026-11-05T10:05:00Z",
            ),
        )
    }

    override suspend fun upload(upload: NetworkMediaUpload, content: ByteArray): Outcome<Unit> {
        calls += "upload:${content.size}"
        return uploadFailure?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    override suspend fun completeUpload(assetId: String): Outcome<NetworkMediaAsset> {
        calls += "complete:$assetId"
        return Outcome.Success(completed)
    }

    override suspend fun asset(assetId: String): Outcome<NetworkMediaAsset> {
        polls++
        pollFailure?.let { return Outcome.Failure(it) }
        return Outcome.Success(if (alwaysProcessing) NetworkMediaAsset("asset", "Processing") else statuses.removeAt(0))
    }
}
