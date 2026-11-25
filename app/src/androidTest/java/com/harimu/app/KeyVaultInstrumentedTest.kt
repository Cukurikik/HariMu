package com.harimu.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harimu.app.core.security.VaultCryptoEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CORE-07 (Key Vault) diuji lewat engine yang dipakai layar Key Vault: pembuatan kunci,
 * penyegelan private key, tanda tangan, dan verifikasi.
 */
@RunWith(AndroidJUnit4::class)
class KeyVaultInstrumentedTest {

    private val payload = "HariMu instrumented test payload".toByteArray()

    @Test
    fun ecKeySignsAndVerifiesAndPrivateKeyIsSealed() {
        val alias = "harimu_it_ec_${System.currentTimeMillis()}"
        val key = VaultCryptoEngine.generateKey(alias = alias, algorithm = "ECDSA", keySizeBits = 256)

        assertEquals(alias, key.alias)
        assertEquals("ECDSA-P-256", key.algorithm)
        assertTrue("private key harus disegel, dapat: ${key.privateKeyPem.take(12)}", key.privateKeyPem.startsWith("vault:v1:"))
        assertTrue(key.fingerprintSha256.isNotBlank())

        val signature = VaultCryptoEngine.signBytes(key, payload)
        assertTrue("signature harus valid untuk data asli", VaultCryptoEngine.verifyBytes(key, payload, signature))

        val tamperedSignature = signature.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(
            "signature yang diubah harus gagal diverifikasi",
            VaultCryptoEngine.verifyBytes(key, payload, tamperedSignature)
        )
        assertFalse(
            "data berbeda harus gagal diverifikasi",
            VaultCryptoEngine.verifyBytes(key, "data-lain".toByteArray(), signature)
        )

        val exported = VaultCryptoEngine.exportPrivatePem(key)
        assertTrue("PEM private key harus bisa diekspor", exported.contains("PRIVATE KEY"))
    }

    @Test
    fun rsaKeySignsAndVerifies() {
        val alias = "harimu_it_rsa_${System.currentTimeMillis()}"
        val key = VaultCryptoEngine.generateKey(alias = alias, algorithm = "RSA", keySizeBits = 2048)

        assertEquals("RSA-2048", key.algorithm)
        assertTrue(key.publicKeyPem.contains("PUBLIC KEY"))
        assertTrue(key.privateKeyPem.startsWith("vault:v1:"))

        val signature = VaultCryptoEngine.signBytes(key, payload)
        assertTrue(VaultCryptoEngine.verifyBytes(key, payload, signature))
    }

    @Test
    fun aesKeyIsKeystoreBackedAndNonExportable() {
        val alias = "harimu_it_aes_${System.currentTimeMillis()}"
        val key = VaultCryptoEngine.generateKey(alias = alias, algorithm = "AES", keySizeBits = 256)

        assertEquals("AES-256-GCM", key.algorithm)
        assertTrue("AES harus berada di AndroidKeyStore", key.keystoreBacked)
        assertTrue(
            "private key AES tidak boleh berupa PEM, dapat: ${key.privateKeyPem}",
            key.privateKeyPem.contains("AndroidKeyStore")
        )
    }
}
