package com.geowoof.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.geowoof.core.common.ApplicationScope
import com.geowoof.core.common.Dispatcher
import com.geowoof.core.common.GeoWoofDispatchers
import com.geowoof.core.datastore.AeadSessionCipher
import com.geowoof.core.datastore.EncryptedSessionStorage
import com.geowoof.core.datastore.SessionCipher
import com.geowoof.core.datastore.SessionFile
import com.geowoof.core.datastore.SessionFileSerializer
import com.geowoof.core.datastore.SessionStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun providesSessionDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(GeoWoofDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ): DataStore<SessionFile> = DataStoreFactory.create(
        serializer = SessionFileSerializer(LazySessionCipher { AeadSessionCipher.withAndroidKeystore(context) }),
        corruptionHandler = ReplaceFileCorruptionHandler { SessionFile() },
        scope = scope + ioDispatcher,
        produceFile = { context.dataStoreFile("session.bin") },
    )
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DataStoreBindings {
    @Binds
    fun bindsSessionStorage(storage: EncryptedSessionStorage): SessionStorage
}

/** Creates the Keystore-backed cipher on first use, on the DataStore's IO dispatcher. */
private class LazySessionCipher(factory: () -> SessionCipher) : SessionCipher {
    private val cipher by lazy(factory)

    override fun encrypt(plaintext: ByteArray): ByteArray = cipher.encrypt(plaintext)

    override fun decrypt(ciphertext: ByteArray): ByteArray = cipher.decrypt(ciphertext)
}
