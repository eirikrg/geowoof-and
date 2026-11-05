package com.geowoof.core.network.auth

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.AuthApi
import com.geowoof.core.network.AuthNetworkDataSource
import com.geowoof.core.network.FakeTokens
import com.geowoof.core.network.RefreshApi
import com.geowoof.core.network.RetrofitAuthNetworkDataSource
import com.geowoof.core.network.TestJson
import com.geowoof.core.network.USER_JSON
import com.geowoof.core.network.authenticatedClient
import com.geowoof.core.network.retrofitFor
import com.geowoof.core.network.sessionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

class TokenAuthenticatorTest {
    private val server = MockWebServer()
    private val refreshCalls = AtomicInteger()

    /** What the refresh endpoint answers: a new session, or a status code. */
    @Volatile private var refreshResponse: MockResponse = MockResponse.Builder().body(
        sessionJson("new-access", "new-refresh"),
    ).build()

    @Before
    fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.url.encodedPath) {
                "/api/v1/auth/refresh" -> {
                    refreshCalls.incrementAndGet()
                    Thread.sleep(100)
                    refreshResponse
                }

                "/api/v1/me" -> if (request.headers["Authorization"] == "Bearer new-access") {
                    MockResponse.Builder().body(USER_JSON).build()
                } else {
                    MockResponse.Builder().code(401).build()
                }

                else -> MockResponse.Builder().code(404).build()
            }
        }
        server.start()
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun an_expired_access_token_is_refreshed_and_the_request_retried() = runTest {
        val tokens = FakeTokens(access = "old-access", refresh = "old-refresh")

        val outcome = dataSource(tokens).currentUser()

        assertTrue(outcome is Outcome.Success)
        assertEquals("new-access", tokens.access)
        assertEquals("new-refresh", tokens.refresh)
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun concurrent_failures_share_a_single_refresh() = runTest {
        val tokens = FakeTokens(access = "old-access", refresh = "old-refresh")
        val dataSource = dataSource(tokens)

        val outcomes = (1..4).map { async(kotlinx.coroutines.Dispatchers.IO) { dataSource.currentUser() } }.awaitAll()

        assertTrue(outcomes.all { it is Outcome.Success })
        assertEquals(1, refreshCalls.get())
    }

    @Test
    fun a_rejected_refresh_token_ends_the_session() = runTest {
        refreshResponse =
            MockResponse.Builder().code(401).body("""{"status":401,"code":"auth.refresh_token_reused"}""").build()
        val tokens = FakeTokens(access = "old-access", refresh = "old-refresh")

        dataSource(tokens).currentUser()

        assertTrue(tokens.expired)
    }

    @Test
    fun a_network_failure_while_refreshing_keeps_the_session() = runTest {
        refreshResponse = MockResponse.Builder().code(503).build()
        val tokens = FakeTokens(access = "old-access", refresh = "old-refresh")

        dataSource(tokens).currentUser()

        assertFalse(tokens.expired)
        assertEquals("old-refresh", tokens.refresh)
    }

    private val now = Instant.parse("2026-11-05T10:00:00Z")

    private fun jwt(expiresAt: Instant?): String {
        val claims = if (expiresAt == null) "{}" else """{"sub":"ana","exp":${expiresAt.epochSecond}}"""
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())
        return "header.$encoded.signature"
    }

    private fun authenticator(tokens: FakeTokens): TokenAuthenticator {
        val refreshApi = retrofitFor(server.url("/").toString(), OkHttpClient(), RefreshApi::class.java)
        return TokenAuthenticator(tokens, refreshApi, TestJson)
    }

    @Test
    fun a_token_with_time_left_is_not_renewed_before_connecting() = runTest {
        val token = jwt(now.plusSeconds(61))
        val tokens = FakeTokens(access = token, refresh = "old-refresh")

        authenticator(tokens).renewIfExpiring(now)

        assertEquals(token, tokens.access)
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun a_token_about_to_expire_is_renewed_before_connecting() = runTest {
        val tokens = FakeTokens(access = jwt(now.plusSeconds(60)), refresh = "old-refresh")

        authenticator(tokens).renewIfExpiring(now)

        assertEquals("new-access", tokens.access)
        assertEquals("new-refresh", tokens.refresh)
        assertEquals(1, refreshCalls.get())
    }

    /** The one that caused the storm: expired, but the server still takes it for 30 s. */
    @Test
    fun a_token_that_has_just_expired_is_renewed_before_connecting() = runTest {
        val tokens = FakeTokens(access = jwt(now.minusSeconds(1)), refresh = "old-refresh")

        authenticator(tokens).renewIfExpiring(now)

        assertEquals("new-access", tokens.access)
    }

    @Test
    fun several_connections_at_once_share_one_renewal() = runTest {
        val tokens = FakeTokens(access = jwt(now), refresh = "old-refresh")
        val authenticator = authenticator(tokens)

        List(5) { async(Dispatchers.IO) { authenticator.renewIfExpiring(now) } }.awaitAll()

        assertEquals(1, refreshCalls.get())
        assertEquals("new-access", tokens.access)
    }

    @Test
    fun a_token_it_cannot_read_is_left_to_the_401() = runTest {
        listOf("opaque", "a.%%%.c", "a.bm90LWpzb24.c", jwt(expiresAt = null)).forEach { token ->
            val tokens = FakeTokens(access = token, refresh = "old-refresh")

            authenticator(tokens).renewIfExpiring(now)

            assertEquals(token, tokens.access)
        }
        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun without_a_session_there_is_nothing_to_renew() = runTest {
        authenticator(FakeTokens(access = null, refresh = null)).renewIfExpiring(now)

        assertEquals(0, refreshCalls.get())
    }

    @Test
    fun if_the_renewal_is_rejected_the_session_ends() = runTest {
        refreshResponse =
            MockResponse.Builder().code(401).body("""{"status":401,"code":"auth.refresh_token_reused"}""").build()
        val tokens = FakeTokens(access = jwt(now), refresh = "old-refresh")

        authenticator(tokens).renewIfExpiring(now)

        assertTrue(tokens.expired)
    }

    @Test
    fun without_a_connection_the_session_stays() = runTest {
        refreshResponse = MockResponse.Builder().code(503).build()
        val token = jwt(now)
        val tokens = FakeTokens(access = token, refresh = "old-refresh")

        authenticator(tokens).renewIfExpiring(now)

        assertFalse(tokens.expired)
        assertEquals(token, tokens.access)
    }

    private fun dataSource(tokens: FakeTokens): AuthNetworkDataSource {
        val baseUrl = server.url("/").toString()
        val refreshApi = retrofitFor(baseUrl, OkHttpClient(), RefreshApi::class.java)
        val api = retrofitFor(baseUrl, authenticatedClient(tokens, refreshApi), AuthApi::class.java)
        return RetrofitAuthNetworkDataSource(api, TestJson)
    }
}
