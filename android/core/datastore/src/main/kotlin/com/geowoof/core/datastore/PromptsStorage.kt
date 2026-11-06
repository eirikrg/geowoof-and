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

/** What this device has already been asked. Not encrypted, unlike the session: it says nothing about anyone. */
@Serializable
data class StoredPrompts(val askedForNotifications: Boolean = false)

interface PromptsStorage {
    val prompts: Flow<StoredPrompts>

    suspend fun update(transform: (StoredPrompts) -> StoredPrompts)
}

internal class DataStorePromptsStorage @Inject constructor(private val dataStore: DataStore<StoredPrompts>) :
    PromptsStorage {
    override val prompts: Flow<StoredPrompts> = dataStore.data

    override suspend fun update(transform: (StoredPrompts) -> StoredPrompts) {
        dataStore.updateData(transform)
    }
}

internal object PromptsSerializer : Serializer<StoredPrompts> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = StoredPrompts()

    override suspend fun readFrom(input: InputStream): StoredPrompts = try {
        json.decodeFromString(input.readBytes().decodeToString())
    } catch (exception: SerializationException) {
        throw CorruptionException("The prompts cannot be read", exception)
    }

    override suspend fun writeTo(t: StoredPrompts, output: OutputStream) {
        output.write(json.encodeToString(t).encodeToByteArray())
    }
}
