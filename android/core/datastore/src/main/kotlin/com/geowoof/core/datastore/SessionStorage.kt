package com.geowoof.core.datastore

import android.util.Log
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import javax.inject.Inject

/**
 * Persistent session of the signed-in user. Storage failures (for example, an Android Keystore
 * that stops working) never reach the caller: a session that cannot be read counts as signed out,
 * and one that cannot be written stays valid in memory until the app closes.
 */
interface SessionStorage {
    val session: Flow<StoredSession?>

    suspend fun read(): StoredSession?

    suspend fun write(session: StoredSession?)
}

internal class EncryptedSessionStorage @Inject constructor(private val dataStore: DataStore<SessionFile>) :
    SessionStorage {
    override val session: Flow<StoredSession?> = dataStore.data.map { it.session }

    override suspend fun read(): StoredSession? = logFailures(fallback = null) { session.first() }

    override suspend fun write(session: StoredSession?) = logFailures(fallback = Unit) {
        dataStore.updateData { SessionFile(session) }
        Unit
    }

    private inline fun <T> logFailures(fallback: T, action: () -> T): T = try {
        action()
    } catch (exception: IOException) {
        Log.w(TAG, "Session storage failed", exception)
        fallback
    } catch (exception: GeneralSecurityException) {
        Log.w(TAG, "Session encryption failed", exception)
        fallback
    }

    private companion object {
        const val TAG = "SessionStorage"
    }
}

/**
 * Serialises the session as encrypted JSON. A file that cannot be decrypted (for example, the
 * Keystore key is gone after restoring a backup) is treated as "no session".
 */
internal class SessionFileSerializer(private val cipher: SessionCipher) : Serializer<SessionFile> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue: SessionFile = SessionFile()

    override suspend fun readFrom(input: InputStream): SessionFile {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) {
            return defaultValue
        }

        return try {
            json.decodeFromString(SessionFile.serializer(), cipher.decrypt(bytes).decodeToString())
        } catch (@Suppress("SwallowedException") exception: GeneralSecurityException) {
            defaultValue
        } catch (exception: SerializationException) {
            throw CorruptionException("The session file is not valid.", exception)
        }
    }

    override suspend fun writeTo(t: SessionFile, output: OutputStream) {
        output.write(cipher.encrypt(json.encodeToString(SessionFile.serializer(), t).encodeToByteArray()))
    }
}
