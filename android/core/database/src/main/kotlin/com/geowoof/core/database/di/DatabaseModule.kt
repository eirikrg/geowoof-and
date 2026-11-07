package com.geowoof.core.database.di

import android.content.Context
import androidx.room.Room
import com.geowoof.core.database.ActiveWalkDao
import com.geowoof.core.database.FeedDao
import com.geowoof.core.database.GeoWoofDatabase
import com.geowoof.core.database.PetDao
import com.geowoof.core.database.PetWeightDao
import com.geowoof.core.database.ProfileDao
import com.geowoof.core.database.RecentPersonDao
import com.geowoof.core.database.SocialDao
import com.geowoof.core.database.WalkDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): GeoWoofDatabase =
        Room.databaseBuilder(context, GeoWoofDatabase::class.java, "geowoof.db").build()

    @Provides
    fun providesChatDao(database: GeoWoofDatabase) = database.chatDao()

    @Provides
    fun providesUserSettingsDao(database: GeoWoofDatabase) = database.userSettingsDao()

    @Provides
    fun providesProfileDao(database: GeoWoofDatabase): ProfileDao = database.profileDao()

    @Provides
    fun providesPetDao(database: GeoWoofDatabase): PetDao = database.petDao()

    @Provides
    fun providesPetWeightDao(database: GeoWoofDatabase): PetWeightDao = database.petWeightDao()

    @Provides
    fun providesActiveWalkDao(database: GeoWoofDatabase): ActiveWalkDao = database.activeWalkDao()

    @Provides
    fun providesWalkDao(database: GeoWoofDatabase): WalkDao = database.walkDao()

    @Provides
    fun providesSocialDao(database: GeoWoofDatabase): SocialDao = database.socialDao()

    @Provides
    fun providesFeedDao(database: GeoWoofDatabase): FeedDao = database.feedDao()

    @Provides
    fun providesRecentPersonDao(database: GeoWoofDatabase): RecentPersonDao = database.recentPersonDao()
}
