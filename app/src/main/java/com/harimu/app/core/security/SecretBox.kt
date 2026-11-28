package com.harimu.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Enkripsi AES-256-GCM dengan master key di AndroidKeyStore untuk menyegel kredensial cloud
 * (CORE-09) dan private key di Key Vault (CORE-07).
 *
 * Pada lingkungan unit-test JVM tanpa AndroidKeyStore, tersedia fallback kunci sesi lokal agar
 * logika seal/unseal tetap teruji deterministik tanpa membocorkan plaintext.
 */
object SecretBox {

    private const val PREFIX = "sb1:"
    private const val KEYSTORE_TYPE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "harimu_secretbox_master_v1"
    private const val GCM_IV_LEN = 12
    private const val GCM_TAG_BITS = 128

    @Volatile
    private var jvmFallbackKey: SecretKey? = null

    fun isSealed(raw: String): Boolean = raw.startsWith(PREFIX)

    fun seal(plain: String): String {
        val encrypted = sealBytes(plain.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun unseal(sealed: String): String {
        val payload = sealed.removePrefix(PREFIX)
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        return String(unsealBytes(raw), Charsets.UTF_8)
    }

    fun sealBytes(plain: ByteArray): ByteArray {
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv ?: ByteArray(GCM_IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipherText = cipher.doFinal(plain)
        return ByteArray(1 + iv.size + cipherText.size).apply {
            this[0] = iv.size.toByte()
            System.arraycopy(iv, 0, this, 1, iv.size)
            System.arraycopy(cipherText, 0, this, 1 + iv.size, cipherText.size)
        }
    }

    fun unsealBytes(sealed: ByteArray): ByteArray {
        require(sealed.size > 1 + GCM_IV_LEN) { "Data tersegel rusak atau terpotong" }
        val ivLen = sealed[0].toInt() and 0xFF
        require(ivLen in 8..32 && sealed.size > 1 + ivLen) { "Header IV tidak valid" }
        val iv = sealed.copyOfRange(1, 1 + ivLen)
        val cipherText = sealed.copyOfRange(1 + ivLen, sealed.size)
        val key = getOrCreateMasterKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText)
    }

    private fun getOrCreateMasterKey(): SecretKey {
        val ksKey = runCatching {
            val ks = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null) }
            val existing = ks.getKey(MASTER_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_TYPE)
            kg.init(
                KeyGenParameterSpec.Builder(
                    MASTER_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            kg.generateKey()
        }.getOrNull()

        if (ksKey != null) return ksKey

        return jvmFallbackKey ?: synchronized(this) {
            jvmFallbackKey ?: ByteArray(32).also { SecureRandom().nextBytes(it) }.let { raw ->
                SecretKeySpec(raw, "AES").also { jvmFallbackKey = it }
            }
        }
    }
}
