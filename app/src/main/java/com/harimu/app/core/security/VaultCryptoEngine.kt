package com.harimu.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.harimu.app.core.data.VaultKeyEntity
import java.io.File
import java.io.FileOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Key Vault (FASE 14, CORE-07).
 *
 * Yang benar-benar dijamin:
 * - Kunci AES tinggal di **AndroidKeyStore** (non-ekspor; `publicKeyPem` hanya penanda).
 * - Pasangan RSA/EC dibuat di perangkat. Private key **disegel** dengan kunci master Keystore
 *   (`harimu_vault_master`, AES-256-GCM) sebelum disimpan di Room, jadi database saja tidak cukup
 *   untuk mencuri kunci. Ekspor PEM wajib melewati gerbang biometrik di UI.
 * - Enkripsi/dekripsi berkas memakai AES-GCM 128-bit tag dengan format `HMVLT1|iv|ciphertext`.
 * - Tanda tangan/verifikasi: `SHA256withRSA` atau `SHA256withECDSA`.
 *
 * Batas jujur:
 * - Gerbang biometrik adalah lapisan UI (`BiometricPrompt`), bukan `setUserAuthenticationRequired`
 *   pada kunci Keystore — kalau kunci diikat ke biometrik, setiap operasi AES (termasuk dekripsi
 *   berkas) akan meminta sidik jari dan banyak perangkat tidak punya sensor. Ini dicatat di DECISIONS.
 * - Enkripsi berkas **hanya untuk kunci AES**; RSA tidak dipakai untuk data besar (batas blok).
 * - Menghapus entri vault tidak menghapus kunci Keystore (butuh alias; UI menyebutkan).
 */
object VaultCryptoEngine {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val MASTER_ALIAS = "harimu_vault_master"
    private const val SEAL_PREFIX = "vault:v1:"
    private const val FILE_MAGIC = "HMVLT1"
    private const val GCM_TAG_BITS = 128

    val AES_SIZES = listOf(128, 256)
    val RSA_SIZES = listOf(2048, 3072, 4096)
    val EC_CURVES = listOf("P-256", "P-384", "P-521")

    // ---------------------------------------------------------------- generate

    fun generateKey(
        alias: String,
        algorithm: String,
        keySizeBits: Int = 256,
        keepInKeystore: Boolean = true
    ): VaultKeyEntity {
        val cleanAlias = alias.trim().ifBlank { "harimu_key_${System.currentTimeMillis() % 100000}" }
        return when {
            algorithm.startsWith("AES") -> generateAes(cleanAlias, keySizeBits.coerceIn(128, 256))
            algorithm.startsWith("RSA") -> generateRsa(cleanAlias, keySizeBits)
            algorithm.startsWith("ECDSA") -> generateEc(cleanAlias, algorithm, keySizeBits)
            else -> error("Algoritma tidak didukung: $algorithm")
        }
    }

    private fun generateAes(alias: String, bits: Int): VaultKeyEntity {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(if (bits == 128) 128 else 256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        keyGen.generateKey()
        return VaultKeyEntity(
            alias = alias,
            algorithm = "AES-${if (bits == 128) 128 else 256}-GCM",
            keySizeBits = if (bits == 128) 128 else 256,
            publicKeyPem = "(Non-exportable AndroidKeyStore key)",
            privateKeyPem = "(Protected in AndroidKeyStore)",
            fingerprintSha256 = sha256Hex("AES:$alias:$bits".toByteArray()),
            keystoreBacked = true
        )
    }

    private fun generateRsa(alias: String, bits: Int): VaultKeyEntity {
        val size = bits.takeIf { it in RSA_SIZES } ?: 2048
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(size)
        val pair = generator.generateKeyPair()
        return entityForPair(alias, "RSA-$size", size, pair.public, pair.private)
    }

    private fun generateEc(alias: String, algorithm: String, bits: Int): VaultKeyEntity {
        val (label, curveName, curveBits) = when {
            algorithm.contains("384") || bits == 384 -> Triple("P-384", "secp384r1", 384)
            algorithm.contains("521") || bits == 521 -> Triple("P-521", "secp521r1", 521)
            else -> Triple("P-256", "secp256r1", 256)
        }
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(curveName))
        val pair = generator.generateKeyPair()
        return entityForPair(alias, "ECDSA-$label", curveBits, pair.public, pair.private)
    }

    private fun entityForPair(
        alias: String,
        algorithm: String,
        bits: Int,
        public: PublicKey,
        private: PrivateKey
    ): VaultKeyEntity {
        val privatePem = toPem("PRIVATE KEY", private.encoded)
        return VaultKeyEntity(
            alias = alias,
            algorithm = algorithm,
            keySizeBits = bits,
            publicKeyPem = toPem("PUBLIC KEY", public.encoded),
            privateKeyPem = sealPrivate(privatePem),
            fingerprintSha256 = sha256Hex(public.encoded),
            keystoreBacked = false
        )
    }

    // -------------------------------------------------------------- export PEM

    /** Hanya dipanggil setelah gerbang biometrik di UI berhasil. */
    fun exportPrivatePem(entity: VaultKeyEntity): String {
        require(!entity.keystoreBacked) {
            "Kunci ini non-ekspor (AndroidKeyStore) dan tidak bisa diekspor"
        }
        return unsealPrivate(entity.privateKeyPem)
    }

    fun exportPublicPem(entity: VaultKeyEntity): String = entity.publicKeyPem

    internal fun sealPrivate(plainPem: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plainPem.toByteArray(Charsets.UTF_8))
        return SEAL_PREFIX + Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    internal fun unsealPrivate(stored: String): String {
        if (stored.trimStart().startsWith("-----BEGIN")) return stored // data lama Fase 2
        require(stored.startsWith(SEAL_PREFIX)) { "Format private key tidak dikenali" }
        val raw = Base64.decode(stored.removePrefix(SEAL_PREFIX), Base64.DEFAULT)
        require(raw.size > 12) { "Private key terenkripsi rusak" }
        val iv = raw.copyOfRange(0, 12)
        val cipherText = raw.copyOfRange(12, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun masterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(MASTER_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    // ------------------------------------------------------------ file encrypt

    fun encryptFile(entity: VaultKeyEntity, input: File, dest: File): Long {
        require(entity.keystoreBacked) { "Enkripsi berkas hanya untuk kunci AES (AndroidKeyStore)" }
        val key = aesKey(entity.alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        FileOutputStream(dest).use { out ->
            out.write(FILE_MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(iv)
            input.inputStream().use { raw ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = raw.read(buffer)
                    if (read <= 0) break
                    out.write(cipher.update(buffer, 0, read))
                }
                out.write(cipher.doFinal())
            }
        }
        return dest.length()
    }

    fun decryptFile(entity: VaultKeyEntity, input: File, dest: File): Long {
        require(entity.keystoreBacked) { "Dekripsi berkas hanya untuk kunci AES (AndroidKeyStore)" }
        val bytes = input.inputStream().use { stream ->
            val magic = ByteArray(FILE_MAGIC.length)
            var read = 0
            while (read < magic.size) {
                val n = stream.read(magic, read, magic.size - read)
                if (n <= 0) break
                read += n
            }
            require(read == magic.size && magic.toString(Charsets.US_ASCII) == FILE_MAGIC) {
                "Berkas bukan arsip terenkripsi HariMu"
            }
            val iv = ByteArray(12)
            var ivRead = 0
            while (ivRead < iv.size) {
                val n = stream.read(iv, ivRead, iv.size - ivRead)
                if (n <= 0) break
                ivRead += n
            }
            require(ivRead == 12) { "Header IV terpotong" }
            val cipherText = stream.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey(entity.alias), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(cipherText)
        }
        dest.writeBytes(bytes)
        return dest.length()
    }

    private fun aesKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(alias, null) as? SecretKey
            ?: error("Kunci Keystore '$alias' tidak ditemukan (mungkin sudah dihapus)")
    }

    // ------------------------------------------------------------- sign/verify

    fun signBytes(entity: VaultKeyEntity, data: ByteArray): ByteArray {
        val algorithm = if (entity.algorithm.startsWith("RSA")) "SHA256withRSA" else "SHA256withECDSA"
        val signature = Signature.getInstance(algorithm)
        signature.initSign(privateKeyOf(entity))
        signature.update(data)
        return signature.sign()
    }

    fun verifyBytes(entity: VaultKeyEntity, data: ByteArray, signatureBytes: ByteArray): Boolean {
        val algorithm = if (entity.algorithm.startsWith("RSA")) "SHA256withRSA" else "SHA256withECDSA"
        return runCatching {
            val signature = Signature.getInstance(algorithm)
            signature.initVerify(publicKeyOf(entity))
            signature.update(data)
            signature.verify(signatureBytes)
        }.getOrDefault(false)
    }

    private fun privateKeyOf(entity: VaultKeyEntity): PrivateKey {
        if (entity.keystoreBacked) {
            // Hanya kunci AES yang berada di Keystore; tidak dipakai untuk tanda tangan.
            error("Kunci AES tidak bisa dipakai untuk tanda tangan; gunakan RSA/ECDSA")
        }
        val pem = unsealPrivate(entity.privateKeyPem)
        val der = pemToDer(pem)
        val factory = KeyFactory.getInstance(if (entity.algorithm.startsWith("RSA")) "RSA" else "EC")
        return factory.generatePrivate(PKCS8EncodedKeySpec(der))
    }

    private fun publicKeyOf(entity: VaultKeyEntity): PublicKey {
        val der = pemToDer(entity.publicKeyPem)
        val factory = KeyFactory.getInstance(if (entity.algorithm.startsWith("RSA")) "RSA" else "EC")
        return factory.generatePublic(X509EncodedKeySpec(der))
    }

    fun pemToDer(pem: String): ByteArray {
        val body = pem
            .replace(Regex("-----BEGIN [^-]+-----"), "")
            .replace(Regex("-----END [^-]+-----"), "")
            .replace(Regex("\\s"), "")
        require(body.isNotEmpty()) { "PEM kosong/tidak valid" }
        return Base64.decode(body, Base64.DEFAULT)
    }

    fun toPem(label: String, der: ByteArray): String {
        val base64 = Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$base64\n-----END $label-----"
    }

    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(":") { "%02X".format(it) }

    fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
}
