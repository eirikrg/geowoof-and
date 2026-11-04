package com.geowoof.core.data.di

import com.geowoof.core.data.repository.AccountRepository
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.DefaultAccountRepository
import com.geowoof.core.data.repository.DefaultAuthRepository
import com.geowoof.core.data.session.SessionManager
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
    fun bindsAccountRepository(repository: DefaultAccountRepository): AccountRepository
}
