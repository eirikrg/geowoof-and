package com.geowoof.core.datastore

import androidx.datastore.core.DataStoreFactory
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

class EncryptedSessionStorageTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val session = StoredSession(
        accessToken = "access",
        accessTokenExpiresAtEpochMs = 1,
        refreshToken = "refresh-secret",
        refreshTokenExpiresAtEpochMs = 2,
        user = StoredUser("u1", "ana@example.com", true, "ana", "Ana", true, listOf("Password"), "es", 0),
    )

    @Test
    fun the_session_is_stored_encrypted_and_read_back() = runTest {
        val file = folder.newFile("session.bin")
        val storage = storage(file, cipher())

        storage.write(session)

        assertEquals(session, storage.read())
        assertFalse(file.readBytes().decodeToString().contains("refresh-secret"))
    }

    @Test
    fun signing_out_clears_the_session() = runTest {
        val storage = storage(folder.newFile("session.bin"), cipher())
        storage.write(session)

        storage.write(null)

        assertNull(storage.read())
    }

    @Test
    fun a_session_encrypted_with_a_lost_key_reads_as_signed_out() = runTest {
        val bytes = ByteArrayOutputStream().also {
            SessionFileSerializer(cipher()).writeTo(SessionFile(session), it)
        }.toByteArray()

        val restored = SessionFileSerializer(cipher()).readFrom(ByteArrayInputStream(bytes))

        assertNull(restored.session)
    }

    @Test
    fun a_keystore_failure_while_reading_counts_as_signed_out() = runTest {
        val file = folder.newFile("session.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertNull(storage(file, FailingCipher(IOException("Keystore unavailable"))).read())
    }

    @Test
    fun a_keystore_failure_while_writing_does_not_reach_the_caller() = runTest {
        val failure = GeneralSecurityException("Keystore unavailable")
        val storage = storage(folder.newFile("session.bin"), FailingCipher(failure))

        storage.write(session)

        assertNull(storage.read())
    }

    private class FailingCipher(private val failure: Exception) : SessionCipher {
        override fun encrypt(plaintext: ByteArray): ByteArray = throw failure

        override fun decrypt(ciphertext: ByteArray): ByteArray = throw failure
    }

    private fun TestScope.storage(file: File, cipher: SessionCipher) = EncryptedSessionStorage(
        DataStoreFactory.create(
            serializer = SessionFileSerializer(cipher),
            scope = backgroundScope,
            produceFile = { file },
        ),
    )

    private fun cipher(): SessionCipher {
        AeadConfig.register()
        val keysetHandle = KeysetHandle.newBuilder()
            .addEntry(
                KeysetHandle.generateEntryFromParameters(
                    PredefinedAeadParameters.AES256_GCM,
                ).withRandomId().makePrimary(),
            )
            .build()
        return AeadSessionCipher(keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
    }
}
