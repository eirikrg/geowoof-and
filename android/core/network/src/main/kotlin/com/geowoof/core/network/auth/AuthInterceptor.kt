package com.geowoof.core.network.auth

import okhttp3.Interceptor
import okhttp3.Response

/** Adds `Authorization: Bearer <access token>` when there is a session. */
internal class AuthInterceptor(private val tokens: SessionTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val accessToken = tokens.accessToken()
        if (accessToken == null || request.header(AUTHORIZATION) != null) {
            return chain.proceed(request)
        }

        return chain.proceed(request.newBuilder().header(AUTHORIZATION, BEARER + accessToken).build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
        const val BEARER = "Bearer "
    }
}
