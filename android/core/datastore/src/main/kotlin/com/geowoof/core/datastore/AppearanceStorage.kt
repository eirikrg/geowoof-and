package com.geowoof.core.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Not encrypted, unlike the session: it only says how the app looks. */
@Serializable
data class StoredAppearance(val themeMode: String = "System", val useWallpaperColors: Boolean = false)

interface AppearanceStorage {
    val appearance: Flow<StoredAppearance>

    suspend fun update(transform: (StoredAppearance) -> StoredAppearance)
}

internal class DataStoreAppearanceStorage @Inject constructor(private val dataStore: DataStore<StoredAppearance>) :
    AppearanceStorage {
    override val appearance: Flow<StoredAppearance> = dataStore.data

    override suspend fun update(transform: (StoredAppearance) -> StoredAppearance) {
        dataStore.updateData(transform)
    }
}

internal object AppearanceSerializer : Serializer<StoredAppearance> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = StoredAppearance()

    override suspend fun readFrom(input: InputStream): StoredAppearance = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (exception: SerializationException) {
        throw CorruptionException("The appearance cannot be read", exception)
    }

    override suspend fun writeTo(t: StoredAppearance, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
