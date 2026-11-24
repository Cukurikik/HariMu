package com.harimu.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Penyegel rahasia aplikasi (kredensial cloud) dengan kunci AndroidKeyStore non-ekspor.
 *
 * Format: `SB1:` + base64(iv[12] + ciphertext). Kunci disimpan di Keystore dengan alias
 * [alias]; tidak pernah ditulis ke disk dalam bentuk mentah. Tidak ada biometrik di sini agar
 * antrean unggah latar belakang tetap bisa berjalan tanpa interaksi.
 */
object SecretBox {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val PREFIX = "SB1:"
    private const val TAG_BITS = 128

    fun seal(plainText: String, alias: String = "harimu_secretbox"): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(alias))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun unseal(stored: String, alias: String = "harimu_secretbox"): String {
        require(stored.startsWith(PREFIX)) { "Data bukan SecretBox" }
        val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.DEFAULT)
        require(raw.size > 12) { "Data SecretBox rusak" }
        val iv = raw.copyOfRange(0, 12)
        val cipherText = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(alias), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)

    private fun key(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
