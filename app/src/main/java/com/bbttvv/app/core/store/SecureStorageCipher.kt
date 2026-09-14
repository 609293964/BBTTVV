package com.bbttvv.app.core.store

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Small Android Keystore-backed codec for reusable account credentials.
 *
 * Existing plaintext values are accepted only so callers can migrate them in place.
 * New writes are always encrypted and encryption failures are allowed to surface rather
 * than silently falling back to plaintext storage.
 */
internal object SecureStorageCipher {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "bbttvv.secure.storage.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFIX = "enc:v1:"
    private const val GCM_TAG_LENGTH_BITS = 128

    fun encrypt(value: String): String {
        if (value.isEmpty()) return value
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val ciphertext = Base64.encodeToString(
            cipher.doFinal(value.toByteArray(Charsets.UTF_8)),
            Base64.NO_WRAP
        )
        return "$PREFIX$iv:$ciphertext"
    }

    fun decryptOrPlaintext(value: String?): String? {
        if (value.isNullOrEmpty() || !isEncrypted(value)) return value
        val payload = value.removePrefix(PREFIX)
        val separator = payload.indexOf(':')
        require(separator > 0 && separator < payload.lastIndex) {
            "Malformed encrypted credential payload"
        }

        val iv = Base64.decode(payload.substring(0, separator), Base64.NO_WRAP)
        val ciphertext = Base64.decode(payload.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun isEncrypted(value: String?): Boolean = value?.startsWith(PREFIX) == true

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
