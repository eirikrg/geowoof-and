package com.geowoof.app.di

import com.geowoof.app.BuildConfig
import com.geowoof.core.common.AppConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Build-time configuration from secrets.properties / local.defaults.properties (docs/setup.md). */
@Module
@InstallIn(SingletonComponent::class)
object AppConfigModule {
    @Provides
    @Singleton
    fun providesAppConfig(): AppConfig = AppConfig(
        apiBaseUrl = BuildConfig.API_BASE_URL,
        googleServerClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID.ifBlank { null },
        logHttpTraffic = BuildConfig.DEBUG,
        isMapsConfigured = BuildConfig.MAPS_API_KEY != "DEFAULT_API_KEY",
    )
}
