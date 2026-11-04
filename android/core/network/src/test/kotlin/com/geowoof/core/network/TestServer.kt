package com.geowoof.core.network

import com.geowoof.core.network.auth.AuthInterceptor
import com.geowoof.core.network.auth.SessionTokenProvider
import com.geowoof.core.network.auth.TokenAuthenticator
import com.geowoof.core.network.model.NetworkSession
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.atomic.AtomicInteger

internal val TestJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun <T> retrofitFor(baseUrl: String, client: OkHttpClient, api: Class<T>): T = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(TestJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(api)

internal fun authenticatedClient(tokens: SessionTokenProvider, refreshApi: RefreshApi): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokens))
        .authenticator(TokenAuthenticator(tokens, refreshApi, TestJson))
        .build()

/** In-memory tokens that record what the authenticator did with them. */
internal class FakeTokens(access: String?, refresh: String?) : SessionTokenProvider {
    @Volatile var access: String? = access

    @Volatile var refresh: String? = refresh
    val refreshes = AtomicInteger()

    @Volatile var expired = false

    override fun accessToken() = access

    override fun refreshToken() = refresh

    override suspend fun onTokensRefreshed(session: NetworkSession) {
        refreshes.incrementAndGet()
        access = session.accessToken
        refresh = session.refreshToken
    }

    override suspend fun onSessionExpired() {
        expired = true
        access = null
        refresh = null
    }
}

internal const val USER_JSON =
    """{"id":"u1","email":"ana@example.com","emailVerified":true,"username":"ana","displayName":"Ana",""" +
        """"hasPassword":true,"signInMethods":["Password"],"language":"es","createdAt":"2026-11-01T10:00:00Z"}"""

internal fun sessionJson(accessToken: String, refreshToken: String) =
    """{"accessToken":"$accessToken","accessTokenExpiresAt":"2026-11-03T10:15:00Z",""" +
        """"refreshToken":"$refreshToken","refreshTokenExpiresAt":"2026-12-03T10:00:00Z",""" +
        """"isNewUser":false,"user":$USER_JSON}"""
