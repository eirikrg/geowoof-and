package com.geowoof.core.datastore

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/** Encrypts the session file. */
interface SessionCipher {
    fun encrypt(plaintext: ByteArray): ByteArray

    fun decrypt(ciphertext: ByteArray): ByteArray
}

/** AES-256-GCM through Tink, with the file name as associated data. */
class AeadSessionCipher(private val aead: Aead) : SessionCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray = aead.encrypt(plaintext, ASSOCIATED_DATA)

    override fun decrypt(ciphertext: ByteArray): ByteArray = aead.decrypt(ciphertext, ASSOCIATED_DATA)

    companion object {
        private val ASSOCIATED_DATA = "geowoof-session".toByteArray()

        /**
         * The Tink keyset lives in shared preferences, wrapped by a master key that never
         * leaves the Android Keystore (docs/decisions/0005).
         */
        fun withAndroidKeystore(context: Context): AeadSessionCipher {
            AeadConfig.register()
            val keysetHandle = AndroidKeysetManager.Builder()
                .withSharedPref(context, "geowoof_session_keyset", "geowoof_tink_keysets")
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri("android-keystore://geowoof_session_master_key")
                .build()
                .keysetHandle
            return AeadSessionCipher(keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java))
        }
    }
}
