package com.geowoof.core.network.di

import com.geowoof.core.common.AppConfig
import com.geowoof.core.network.AuthApi
import com.geowoof.core.network.AuthNetworkDataSource
import com.geowoof.core.network.RefreshApi
import com.geowoof.core.network.RetrofitAuthNetworkDataSource
import com.geowoof.core.network.auth.AuthInterceptor
import com.geowoof.core.network.auth.SessionTokenProvider
import com.geowoof.core.network.auth.TokenAuthenticator
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun providesRefreshApi(config: AppConfig, json: Json): RefreshApi =
        retrofit(config, baseClient(config).build(), json).create(RefreshApi::class.java)

    @Provides
    @Singleton
    fun providesAuthApi(config: AppConfig, json: Json, tokens: SessionTokenProvider, refreshApi: RefreshApi): AuthApi {
        val client = baseClient(config)
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, refreshApi, json))
            .build()
        return retrofit(config, client, json).create(AuthApi::class.java)
    }

    private fun baseClient(config: AppConfig) = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply {
            if (config.logHttpTraffic) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                    },
                )
            }
        }

    private fun retrofit(config: AppConfig, client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(config.apiBaseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindings {
    @Binds
    fun bindsAuthNetworkDataSource(dataSource: RetrofitAuthNetworkDataSource): AuthNetworkDataSource
}
