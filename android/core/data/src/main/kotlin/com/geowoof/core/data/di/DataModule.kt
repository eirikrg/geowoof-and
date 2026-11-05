package com.geowoof.core.data.di

import com.geowoof.core.data.media.BitmapImageCompressor
import com.geowoof.core.data.media.FilesWalkPhotoStore
import com.geowoof.core.data.media.ImageCompressor
import com.geowoof.core.data.media.WalkPhotoStore
import com.geowoof.core.data.repository.AccountRepository
import com.geowoof.core.data.repository.ActiveWalkRepository
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.DefaultAccountRepository
import com.geowoof.core.data.repository.DefaultActiveWalkRepository
import com.geowoof.core.data.repository.DefaultAuthRepository
import com.geowoof.core.data.repository.DefaultMediaRepository
import com.geowoof.core.data.repository.DefaultPeopleRepository
import com.geowoof.core.data.repository.DefaultPetsRepository
import com.geowoof.core.data.repository.DefaultProfileRepository
import com.geowoof.core.data.repository.DefaultSocialRepository
import com.geowoof.core.data.repository.DefaultWalkSynchronizer
import com.geowoof.core.data.repository.DefaultWalksRepository
import com.geowoof.core.data.repository.MediaRepository
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.ProfileRepository
import com.geowoof.core.data.repository.SocialRepository
import com.geowoof.core.data.repository.WalkSynchronizer
import com.geowoof.core.data.repository.WalksRepository
import com.geowoof.core.data.session.DatabaseUserDataCleaner
import com.geowoof.core.data.session.SessionManager
import com.geowoof.core.data.session.UserDataCleaner
import com.geowoof.core.network.auth.SessionTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface DataModule {
    @Binds
    fun bindsSessionTokenProvider(sessionManager: SessionManager): SessionTokenProvider

    @Binds
    fun bindsAuthRepository(repository: DefaultAuthRepository): AuthRepository

    @Binds
    fun bindsUserDataCleaner(cleaner: DatabaseUserDataCleaner): UserDataCleaner

    @Binds
    fun bindsProfileRepository(repository: DefaultProfileRepository): ProfileRepository

    @Binds
    fun bindsPetsRepository(repository: DefaultPetsRepository): PetsRepository

    @Binds
    fun bindsMediaRepository(repository: DefaultMediaRepository): MediaRepository

    @Binds
    fun bindsImageCompressor(compressor: BitmapImageCompressor): ImageCompressor

    @Binds
    fun bindsAccountRepository(repository: DefaultAccountRepository): AccountRepository

    @Binds
    fun bindsActiveWalkRepository(repository: DefaultActiveWalkRepository): ActiveWalkRepository

    @Binds
    fun bindsWalksRepository(repository: DefaultWalksRepository): WalksRepository

    @Binds
    fun bindsWalkSynchronizer(synchronizer: DefaultWalkSynchronizer): WalkSynchronizer

    @Binds
    fun bindsWalkPhotoStore(store: FilesWalkPhotoStore): WalkPhotoStore

    @Binds
    fun bindsSocialRepository(repository: DefaultSocialRepository): SocialRepository

    @Binds
    fun bindsPeopleRepository(repository: DefaultPeopleRepository): PeopleRepository
}
