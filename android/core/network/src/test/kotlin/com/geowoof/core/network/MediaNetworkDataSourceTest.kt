package com.geowoof.core.network

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.model.NetworkMediaAsset
import com.geowoof.core.network.model.NetworkMediaUpload
import com.geowoof.core.network.model.UploadRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MediaNetworkDataSourceTest {
    private val server = MockWebServer()
    private val unusedApi = object : MediaApi {
        override suspend fun requestUpload(body: UploadRequestBody): NetworkMediaUpload = error("Not used")

        override suspend fun completeUpload(id: String): NetworkMediaAsset = error("Not used")

        override suspend fun asset(id: String): NetworkMediaAsset = error("Not used")
    }
    private val dataSource = RetrofitMediaNetworkDataSource(unusedApi, OkHttpClient(), TestJson, Dispatchers.Unconfined)

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun upload(path: String = "/bucket/key?X-Amz-Signature=abc") = NetworkMediaUpload(
        assetId = "asset",
        uploadUrl = server.url(path).toString(),
        uploadHeaders = mapOf("Content-Type" to "image/jpeg"),
        expiresAt = "2026-11-05T10:05:00Z",
    )

    @Test
    fun the_bytes_go_to_the_signed_link_with_the_signed_content_type_and_nothing_else() = runTest {
        server.enqueue(MockResponse.Builder().code(200).build())

        val outcome = dataSource.upload(upload(), byteArrayOf(1, 2, 3))

        assertEquals(Outcome.Success(Unit), outcome)
        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/bucket/key?X-Amz-Signature=abc", request.target)
        assertEquals("image/jpeg", request.headers["Content-Type"])
        assertEquals(3L, request.bodySize)

        // An Authorization header would make storage reject the signed link.
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun a_rejected_upload_is_an_api_error() = runTest {
        server.enqueue(MockResponse.Builder().code(403).build())

        val outcome = dataSource.upload(upload(), byteArrayOf(1))

        assertEquals(Outcome.Failure(AppError.Api(status = 403, code = "media.upload_failed")), outcome)
    }

    @Test
    fun without_a_connection_the_upload_is_a_network_error() = runTest {
        val unreachable = upload()
        server.close()

        val outcome = dataSource.upload(unreachable, byteArrayOf(1))

        assertEquals(Outcome.Failure(AppError.Network), outcome)
    }
}
