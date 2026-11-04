package com.geowoof.core.network.di

import com.geowoof.core.common.AppConfig
import com.geowoof.core.network.AuthApi
import com.geowoof.core.network.AuthNetworkDataSource
import com.geowoof.core.network.MediaApi
import com.geowoof.core.network.MediaNetworkDataSource
import com.geowoof.core.network.PetsApi
import com.geowoof.core.network.PetsNetworkDataSource
import com.geowoof.core.network.ProfileApi
import com.geowoof.core.network.ProfileNetworkDataSource
import com.geowoof.core.network.RefreshApi
import com.geowoof.core.network.RetrofitAuthNetworkDataSource
import com.geowoof.core.network.RetrofitMediaNetworkDataSource
import com.geowoof.core.network.RetrofitPetsNetworkDataSource
import com.geowoof.core.network.RetrofitProfileNetworkDataSource
import com.geowoof.core.network.StorageClient
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
import retrofit2.create
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
private annotation class Authenticated

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
    @Authenticated
    fun providesAuthenticatedRetrofit(
        config: AppConfig,
        json: Json,
        tokens: SessionTokenProvider,
        refreshApi: RefreshApi,
    ): Retrofit {
        val client = baseClient(config)
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, refreshApi, json))
            .build()
        return retrofit(config, client, json)
    }

    @Provides
    @Singleton
    fun providesAuthApi(@Authenticated retrofit: Retrofit): AuthApi = retrofit.create()

    @Provides
    @Singleton
    fun providesProfileApi(@Authenticated retrofit: Retrofit): ProfileApi = retrofit.create()

    @Provides
    @Singleton
    fun providesPetsApi(@Authenticated retrofit: Retrofit): PetsApi = retrofit.create()

    @Provides
    @Singleton
    fun providesMediaApi(@Authenticated retrofit: Retrofit): MediaApi = retrofit.create()

    /** No logging either: a signed link in a log works for anyone who reads it until it expires. */
    @Provides
    @Singleton
    @StorageClient
    fun providesStorageClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(UPLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

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
    private const val UPLOAD_TIMEOUT_SECONDS = 120L
}

@Module
@InstallIn(SingletonComponent::class)
internal interface NetworkBindings {
    @Binds
    fun bindsAuthNetworkDataSource(dataSource: RetrofitAuthNetworkDataSource): AuthNetworkDataSource

    @Binds
    fun bindsProfileNetworkDataSource(dataSource: RetrofitProfileNetworkDataSource): ProfileNetworkDataSource

    @Binds
    fun bindsPetsNetworkDataSource(dataSource: RetrofitPetsNetworkDataSource): PetsNetworkDataSource

    @Binds
    fun bindsMediaNetworkDataSource(dataSource: RetrofitMediaNetworkDataSource): MediaNetworkDataSource
}
