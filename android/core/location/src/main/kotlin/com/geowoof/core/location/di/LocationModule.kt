package com.geowoof.core.location.di

import com.geowoof.core.location.AndroidLocationAccess
import com.geowoof.core.location.FusedLocationDataSource
import com.geowoof.core.location.LocationAccess
import com.geowoof.core.location.LocationDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface LocationModule {
    @Binds
    fun bindsLocationAccess(access: AndroidLocationAccess): LocationAccess

    @Binds
    fun bindsLocationDataSource(dataSource: FusedLocationDataSource): LocationDataSource
}
