package com.harimu.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.harimu.app.core.data.VaultKeyEntity
import java.io.File
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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Engine kriptografi untuk Key Vault (CORE-07):
 * - AES 128/256 di AndroidKeyStore (non-ekspor).
 * - RSA 2048/3072/4096 & ECDSA P-256/P-384/P-521 dengan private key disegel [SecretBox] (`vault:v1:`).
 * - Tanda tangan SHA256withRSA / SHA256withECDSA + verifikasi.
 * - Enkripsi / dekripsi berkas AES-GCM (dengan kunci vault Keystore atau kata sandi PBKDF2).
 */
object VaultCryptoEngine {

    private const val VAULT_PREFIX = "vault:v1:"
    private const val KEYSTORE_PREFIX = "AndroidKeyStore://"
    private const val FILE_MAGIC = "HMVLT1"
    private const val KDF_ITERATIONS = 210_000
    private const val GCM_TAG_BITS = 128

    fun generateKey(alias: String, algorithm: String, keySizeBits: Int): VaultKeyEntity {
        val cleanAlias = alias.trim().ifBlank { "key_${System.currentTimeMillis()}" }
        return when (algorithm.uppercase()) {
            "ECDSA", "EC" -> generateEcKey(cleanAlias, keySizeBits)
            "RSA" -> generateRsaKey(cleanAlias, keySizeBits)
            "AES" -> generateAesKey(cleanAlias, keySizeBits)
            else -> error("Algoritma tidak didukung: $algorithm")
        }
    }

    private fun generateEcKey(alias: String, bits: Int): VaultKeyEntity {
        val curve = when (bits) {
            256 -> "secp256r1"
            384 -> "secp384r1"
            521 -> "secp521r1"
            else -> error("Ukuran kunci ECDSA harus 256, 384, atau 521 bit")
        }
        val gen = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec(curve), SecureRandom())
        }
        val pair = gen.generateKeyPair()
        val pubPem = toPem("PUBLIC KEY", pair.public.encoded)
        val privPem = toPem("PRIVATE KEY", pair.private.encoded)
        val sealedPriv = VAULT_PREFIX + SecretBox.seal(privPem)
        return VaultKeyEntity(
            alias = alias,
            algorithm = "ECDSA-P-$bits",
            keySizeBits = bits,
            publicKeyPem = pubPem,
            privateKeyPem = sealedPriv,
            fingerprintSha256 = sha256Fingerprint(pair.public.encoded),
            keystoreBacked = false
        )
    }

    private fun generateRsaKey(alias: String, bits: Int): VaultKeyEntity {
        require(bits in listOf(2048, 3072, 4096)) { "Ukuran kunci RSA harus 2048, 3072, atau 4096 bit" }
        val gen = KeyPairGenerator.getInstance("RSA").apply {
            initialize(bits, SecureRandom())
        }
        val pair = gen.generateKeyPair()
        val pubPem = toPem("PUBLIC KEY", pair.public.encoded)
        val privPem = toPem("PRIVATE KEY", pair.private.encoded)
        val sealedPriv = VAULT_PREFIX + SecretBox.seal(privPem)
        return VaultKeyEntity(
            alias = alias,
            algorithm = "RSA-$bits",
            keySizeBits = bits,
            publicKeyPem = pubPem,
            privateKeyPem = sealedPriv,
            fingerprintSha256 = sha256Fingerprint(pair.public.encoded),
            keystoreBacked = false
        )
    }

    private fun generateAesKey(alias: String, bits: Int): VaultKeyEntity {
        require(bits in listOf(128, 192, 256)) { "Ukuran kunci AES harus 128, 192, atau 256 bit" }
        val ksAlias = "harimu_aes_${alias.replace(Regex("[^A-Za-z0-9_-]"), "_").take(32)}_${System.currentTimeMillis()}"
        runCatching {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(
                KeyGenParameterSpec.Builder(
                    ksAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(bits)
                    .build()
            )
            kg.generateKey()
        }
        return VaultKeyEntity(
            alias = alias,
            algorithm = "AES-$bits-GCM",
            keySizeBits = bits,
            publicKeyPem = "",
            privateKeyPem = "${KEYSTORE_PREFIX}$ksAlias (non-exportable)",
            fingerprintSha256 = sha256Fingerprint(ksAlias.toByteArray(Charsets.UTF_8)),
            keystoreBacked = true
        )
    }

    fun exportPrivatePem(key: VaultKeyEntity): String {
        require(!key.keystoreBacked && key.privateKeyPem.startsWith(VAULT_PREFIX)) {
            "Kunci ini tersimpan di AndroidKeyStore dan tidak dapat diekspor"
        }
        return SecretBox.unseal(key.privateKeyPem.removePrefix(VAULT_PREFIX))
    }

    fun signBytes(key: VaultKeyEntity, data: ByteArray): ByteArray {
        val privPem = exportPrivatePem(key)
        val jcaAlg = when {
            key.algorithm.startsWith("ECDSA") -> "EC"
            key.algorithm.startsWith("RSA") -> "RSA"
            else -> error("Algoritma ${key.algorithm} tidak mendukung tanda tangan asimetris")
        }
        val sigAlg = if (jcaAlg == "EC") "SHA256withECDSA" else "SHA256withRSA"
        val privateKey: PrivateKey = KeyFactory.getInstance(jcaAlg)
            .generatePrivate(PKCS8EncodedKeySpec(fromPem(privPem)))
        return Signature.getInstance(sigAlg).run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }

    fun verifyBytes(key: VaultKeyEntity, data: ByteArray, signature: ByteArray): Boolean = runCatching {
        val jcaAlg = when {
            key.algorithm.startsWith("ECDSA") -> "EC"
            key.algorithm.startsWith("RSA") -> "RSA"
            else -> return@runCatching false
        }
        val sigAlg = if (jcaAlg == "EC") "SHA256withECDSA" else "SHA256withRSA"
        val publicKey: PublicKey = KeyFactory.getInstance(jcaAlg)
            .generatePublic(X509EncodedKeySpec(fromPem(key.publicKeyPem)))
        Signature.getInstance(sigAlg).run {
            initVerify(publicKey)
            update(data)
            verify(signature)
        }
    }.getOrDefault(false)

    fun encryptFileWithKeystore(source: File, dest: File, key: VaultKeyEntity) {
        require(key.keystoreBacked && key.privateKeyPem.startsWith(KEYSTORE_PREFIX)) {
            "Pilih kunci AES berbasis AndroidKeyStore untuk enkripsi berkas langsung"
        }
        val ksAlias = key.privateKeyPem.removePrefix(KEYSTORE_PREFIX).substringBefore(" ")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = ks.getKey(ksAlias, null) as? SecretKey
            ?: error("Kunci $ksAlias tidak ditemukan di AndroidKeyStore")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret) }
        val iv = cipher.iv
        val plain = source.readBytes()
        val ct = cipher.doFinal(plain)
        dest.outputStream().use { out ->
            out.write(FILE_MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(1, iv.size.toByte()))
            out.write(iv)
            out.write(ct)
        }
    }

    fun decryptFileWithKeystore(source: File, dest: File, key: VaultKeyEntity) {
        val raw = source.readBytes()
        require(raw.size > 16 && raw.copyOfRange(0, 6).contentEquals(FILE_MAGIC.toByteArray(Charsets.US_ASCII))) {
            "Format berkas terenkripsi HariMu tidak dikenali"
        }
        val mode = raw[6].toInt()
        require(mode == 1) { "Berkas ini dienkripsi dengan kata sandi, bukan kunci Keystore" }
        val ivLen = raw[7].toInt() and 0xFF
        val iv = raw.copyOfRange(8, 8 + ivLen)
        val ct = raw.copyOfRange(8 + ivLen, raw.size)
        val ksAlias = key.privateKeyPem.removePrefix(KEYSTORE_PREFIX).substringBefore(" ")
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val secret = ks.getKey(ksAlias, null) as? SecretKey
            ?: error("Kunci $ksAlias tidak ditemukan di AndroidKeyStore")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        dest.writeBytes(cipher.doFinal(ct))
    }

    fun encryptFileWithPassword(source: File, dest: File, password: String) {
        require(password.isNotEmpty()) { "Kata sandi wajib diisi" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val secret = deriveAesKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val ct = cipher.doFinal(source.readBytes())
        dest.outputStream().use { out ->
            out.write(FILE_MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(byteArrayOf(2, iv.size.toByte()))
            out.write(salt)
            out.write(iv)
            out.write(ct)
        }
    }

    fun decryptFileWithPassword(source: File, dest: File, password: String) {
        require(password.isNotEmpty()) { "Kata sandi wajib diisi" }
        val raw = source.readBytes()
        require(raw.size > 36 && raw.copyOfRange(0, 6).contentEquals(FILE_MAGIC.toByteArray(Charsets.US_ASCII))) {
            "Format berkas terenkripsi HariMu tidak dikenali"
        }
        val mode = raw[6].toInt()
        require(mode == 2) { "Berkas ini dienkripsi dengan kunci Keystore perangkat" }
        val ivLen = raw[7].toInt() and 0xFF
        val salt = raw.copyOfRange(8, 24)
        val iv = raw.copyOfRange(24, 24 + ivLen)
        val ct = raw.copyOfRange(24 + ivLen, raw.size)
        val secret = deriveAesKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        dest.writeBytes(cipher.doFinal(ct))
    }

    private fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, KDF_ITERATIONS, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun toPem(label: String, der: ByteArray): String {
        val b64 = Base64.encodeToString(der, Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "-----BEGIN $label-----\n$b64\n-----END $label-----"
    }

    private fun fromPem(pem: String): ByteArray {
        val clean = pem.lines()
            .filter { !it.startsWith("-----") && it.isNotBlank() }
            .joinToString("")
        return Base64.decode(clean, Base64.DEFAULT)
    }

    private fun sha256Fingerprint(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(":") { "%02X".format(it) }
}
