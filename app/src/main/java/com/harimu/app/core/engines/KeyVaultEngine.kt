package com.harimu.app.core.engines

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.harimu.app.core.data.VaultKeyEntity
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyGenerator

object KeyVaultEngine {

    fun generateKey(alias: String, algorithmChoice: String): VaultKeyEntity {
        val cleanAlias = alias.trim().ifBlank { "harimu_key_${System.currentTimeMillis() % 10000}" }
        return when (algorithmChoice) {
            "AES-256 (AndroidKeyStore)" -> {
                val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    cleanAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
                keyGen.init(spec)
                val secretKey = keyGen.generateKey()
                val fp = sha256Hex(("AES256:$cleanAlias:${secretKey.algorithm}").toByteArray())
                VaultKeyEntity(
                    alias = cleanAlias,
                    algorithm = "AES-256-GCM",
                    keySizeBits = 256,
                    publicKeyPem = "(Non-exportable hardware-backed AndroidKeyStore key)",
                    privateKeyPem = "(Protected in AndroidKeyStore)",
                    fingerprintSha256 = fp,
                    keystoreBacked = true
                )
            }
            "ECDSA P-256 (PEM)" -> {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(ECGenParameterSpec("secp256r1"))
                val pair = kpg.generateKeyPair()
                val pubPem = toPem("PUBLIC KEY", pair.public.encoded)
                val privPem = toPem("PRIVATE KEY", pair.private.encoded)
                val fp = sha256Hex(pair.public.encoded)
                VaultKeyEntity(
                    alias = cleanAlias,
                    algorithm = "ECDSA P-256",
                    keySizeBits = 256,
                    publicKeyPem = pubPem,
                    privateKeyPem = privPem,
                    fingerprintSha256 = fp,
                    keystoreBacked = false
                )
            }
            else -> {
                val kpg = KeyPairGenerator.getInstance("RSA")
                kpg.initialize(2048)
                val pair = kpg.generateKeyPair()
                val pubPem = toPem("PUBLIC KEY", pair.public.encoded)
                val privPem = toPem("PRIVATE KEY", pair.private.encoded)
                val fp = sha256Hex(pair.public.encoded)
                VaultKeyEntity(
                    alias = cleanAlias,
                    algorithm = "RSA-2048",
                    keySizeBits = 2048,
                    publicKeyPem = pubPem,
                    privateKeyPem = privPem,
                    fingerprintSha256 = fp,
                    keystoreBacked = false
                )
            }
        }
    }

    private fun toPem(label: String, der: ByteArray): String {
        val b64 = Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$b64\n-----END $label-----"
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
