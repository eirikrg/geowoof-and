package com.geowoof.core.network

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ApiCallTest {
    private val server = MockWebServer()
    private lateinit var dataSource: AuthNetworkDataSource

    @Before
    fun setUp() {
        server.start()
        val api = retrofitFor(server.url("/").toString(), OkHttpClient(), AuthApi::class.java)
        dataSource = RetrofitAuthNetworkDataSource(api, TestJson)
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun problem_details_become_api_errors_with_their_code_and_fields() = runTest {
        server.enqueue(
            problem(
                400,
                """{"status":400,"code":"validation.failed","errors":{"email":["Enter a valid email address."]}}""",
            ),
        )

        val outcome = dataSource.register("bad", "Password1", "Ana", null)

        assertEquals(
            Outcome.Failure(
                AppError.Api(400, "validation.failed", mapOf("email" to listOf("Enter a valid email address."))),
            ),
            outcome,
        )
    }

    @Test
    fun retry_after_is_kept_for_lockouts_and_cooldowns() = runTest {
        server.enqueue(
            problem(
                403,
                """{"status":403,"code":"auth.locked_out"}""",
            ).newBuilder().addHeader("Retry-After", "300").build(),
        )

        val outcome = dataSource.signIn("ana@example.com", "Password1")

        assertEquals(Outcome.Failure(AppError.Api(403, "auth.locked_out", retryAfterSeconds = 300)), outcome)
    }

    @Test
    fun responses_without_problem_details_get_the_default_code() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())

        val outcome = dataSource.providers()

        assertEquals(Outcome.Failure(AppError.Api(503, "server.unavailable")), outcome)
    }

    @Test
    fun an_unauthorized_problem_means_the_session_is_over() = runTest {
        server.enqueue(problem(401, """{"status":401,"code":"auth.unauthorized"}"""))

        assertEquals(Outcome.Failure(AppError.SessionExpired), dataSource.currentUser())
    }

    @Test
    fun connection_failures_are_network_errors() = runTest {
        server.close()

        assertEquals(Outcome.Failure(AppError.Network), dataSource.providers())
    }

    @Test
    fun empty_success_responses_are_supported() = runTest {
        server.enqueue(MockResponse.Builder().code(202).build())

        assertEquals(Outcome.Success(Unit), dataSource.requestPasswordReset("ana@example.com"))
    }

    private fun problem(code: Int, body: String) = MockResponse.Builder()
        .code(code)
        .addHeader("Content-Type", "application/problem+json")
        .body(body)
        .build()
}
