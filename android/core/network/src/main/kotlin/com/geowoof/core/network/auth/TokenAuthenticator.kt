package com.geowoof.core.network.auth

import com.geowoof.core.model.AppError
import com.geowoof.core.model.Outcome
import com.geowoof.core.network.RefreshApi
import com.geowoof.core.network.apiCall
import com.geowoof.core.network.auth.AuthInterceptor.Companion.AUTHORIZATION
import com.geowoof.core.network.auth.AuthInterceptor.Companion.BEARER
import com.geowoof.core.network.model.RefreshBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.Base64

/**
 * Refreshes the session when an authenticated request gets a 401 and retries it once.
 * A mutex makes concurrent 401s share a single refresh; a request that failed with a token that
 * was already replaced is simply retried with the new one. Network errors never sign the user
 * out; a rejected refresh token does.
 */
internal class TokenAuthenticator(
    private val tokens: SessionTokenProvider,
    private val refreshApi: RefreshApi,
    private val json: Json,
) : Authenticator {
    private val mutex = Mutex()

    /**
     * For the hubs. One closes a connection the moment its token expires, while the server still
     * accepts that token for a while (`ClockSkew`): connecting with it would be thrown out at once,
     * again and again, until it finally got the 401 that renews it. A token that cannot be read is
     * left to that 401.
     */
    suspend fun renewIfExpiring(now: Instant = Instant.now()) {
        val token = tokens.accessToken() ?: return
        val expiresAt = expirationOf(token) ?: return
        if (now.plus(RENEW_MARGIN) >= expiresAt) mutex.withLock { refreshUnlessAlreadyRefreshed(token) }
    }

    private fun expirationOf(token: String): Instant? {
        val payload = token.split('.').getOrNull(1) ?: return null
        return try {
            val claims = json.parseToJsonElement(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8))
            claims.jsonObject["exp"]?.jsonPrimitive?.longOrNull?.let(Instant::ofEpochSecond)
        } catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
            null
        }
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        val failedToken = response.request.header(AUTHORIZATION)?.removePrefix(BEARER)
        if (failedToken == null || response.priorResponse != null) {
            return null
        }

        return when (val result = runBlocking { mutex.withLock { refreshUnlessAlreadyRefreshed(failedToken) } }) {
            is RefreshResult.Refreshed -> response.request.newBuilder().header(
                AUTHORIZATION,
                BEARER + result.accessToken,
            ).build()

            RefreshResult.Rejected -> null

            // Surfaces as a network error: the session may still be valid, so it is kept.
            RefreshResult.Unavailable -> throw IOException("The session could not be refreshed right now.")
        }
    }

    private suspend fun refreshUnlessAlreadyRefreshed(failedToken: String): RefreshResult {
        val currentToken = tokens.accessToken()
        val refreshToken = tokens.refreshToken()
        return when {
            currentToken != null && currentToken != failedToken -> RefreshResult.Refreshed(currentToken)
            refreshToken == null -> RefreshResult.Rejected
            else -> refresh(refreshToken)
        }
    }

    private suspend fun refresh(refreshToken: String): RefreshResult {
        val body = RefreshBody(refreshToken)
        var outcome = apiCall(json) { refreshApi.refresh(body) }
        // Another request rotated the same token at the same moment: retry once.
        if (outcome is Outcome.Failure && outcome.error.isRefreshInProgress()) {
            delay(RETRY_DELAY_MS)
            outcome = apiCall(json) { refreshApi.refresh(body) }
        }

        return when (outcome) {
            is Outcome.Success -> {
                tokens.onTokensRefreshed(outcome.value)
                RefreshResult.Refreshed(outcome.value.accessToken)
            }

            is Outcome.Failure -> if (outcome.error.isRejectedSession()) {
                tokens.onSessionExpired()
                RefreshResult.Rejected
            } else {
                RefreshResult.Unavailable
            }
        }
    }

    private sealed interface RefreshResult {
        data class Refreshed(val accessToken: String) : RefreshResult

        data object Rejected : RefreshResult

        data object Unavailable : RefreshResult
    }

    private fun AppError.isRefreshInProgress() = this is AppError.Api && status == HTTP_CONFLICT

    private fun AppError.isRejectedSession() =
        this is AppError.SessionExpired || (this is AppError.Api && status == HTTP_UNAUTHORIZED)

    private companion object {
        val RENEW_MARGIN: Duration = Duration.ofSeconds(60)
        const val RETRY_DELAY_MS = 300L
        const val HTTP_CONFLICT = 409
        const val HTTP_UNAUTHORIZED = 401
    }
}
