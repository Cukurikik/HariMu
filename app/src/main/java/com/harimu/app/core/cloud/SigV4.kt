package com.harimu.app.core.cloud

import java.net.URI
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * AWS Signature Version 4 untuk S3/Cloudflare R2 (CORE-09).
 *
 * Hanya menandatangani permintaan **PUT objek** (single-part) karena itu yang dibutuhkan unggahan
 * hasil tool. Fungsi di sini murni (tanpa Android) sehingga bisa diuji unit; implementasi mengikuti
 * spesifikasi resmi "Signature Calculations for the Authorization Header".
 *
 * Batas: tidak ada multipart upload (berkas >5 GB harus dipotong), tidak ada presigned URL, dan
 * `UNSIGNED-PAYLOAD` tidak dipakai — payload selalu di-hash SHA-256.
 */
object SigV4 {

    data class SignedHeaders(
        val authorization: String,
        val amzDate: String,
        val contentSha256: String,
        val host: String
    )

    fun signPut(
        url: String,
        region: String,
        accessKeyId: String,
        secretAccessKey: String,
        payload: ByteArray,
        service: String = "s3",
        extraHeaders: Map<String, String> = emptyMap(),
        clock: () -> Long = { System.currentTimeMillis() }
    ): SignedHeaders {
        val uri = URI(url)
        require(uri.scheme == "https" || uri.scheme == "http") { "URL S3 harus http(s)" }
        val host = if (uri.port > 0 && uri.port != defaultPort(uri.scheme)) "${uri.host}:${uri.port}" else uri.host
        val now = clock()
        val amzDate = amzDate(now)
        val dateStamp = amzDate.substring(0, 8)
        val payloadHash = sha256Hex(payload)
        val canonicalUri = canonicalUri(uri.rawPath.orEmpty().ifBlank { "/" })
        val query = canonicalQuery(uri.rawQuery.orEmpty())

        val headers = sortedMapOf(
            "host" to host,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate
        )
        extraHeaders.forEach { (key, value) ->
            headers[key.lowercase()] = value.trim().replace(Regex("\\s+"), " ")
        }
        val signedHeaders = headers.keys.joinToString(";")
        val canonicalHeaders = headers.entries.joinToString("") { (key, value) -> "$key:$value\n" }
        val canonicalRequest = buildString {
            append("PUT\n")
            append(canonicalUri).append('\n')
            append(query).append('\n')
            append(canonicalHeaders).append('\n')
            append(signedHeaders).append('\n')
            append(payloadHash)
        }
        val scope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))}"
        val signingKey = signingKey(secretAccessKey, dateStamp, region, service)
        val signature = hex(hmacSha256(signingKey, stringToSign.toByteArray(Charsets.UTF_8)))
        val authorization = "AWS4-HMAC-SHA256 Credential=$accessKeyId/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
        return SignedHeaders(authorization, amzDate, payloadHash, host)
    }

    fun canonicalRequest(
        method: String,
        path: String,
        query: String,
        headers: Map<String, String>,
        payloadHash: String
    ): String {
        val sorted = headers.entries.sortedBy { it.key.lowercase() }
        val signed = sorted.joinToString(";") { it.key.lowercase() }
        val canonicalHeaders = sorted.joinToString("") { "${it.key.lowercase()}:${it.value.trim()}\n" }
        return "$method\n${canonicalUri(path)}\n${canonicalQuery(query)}\n$canonicalHeaders\n$signed\n$payloadHash"
    }

    fun stringToSign(amzDate: String, scope: String, canonicalRequest: String): String =
        "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8))}"

    fun signingKey(secretAccessKey: String, dateStamp: String, region: String, service: String): ByteArray {
        val kDate = hmacSha256(("AWS4$secretAccessKey").toByteArray(Charsets.UTF_8), dateStamp.toByteArray(Charsets.UTF_8))
        val kRegion = hmacSha256(kDate, region.toByteArray(Charsets.UTF_8))
        val kService = hmacSha256(kRegion, service.toByteArray(Charsets.UTF_8))
        return hmacSha256(kService, "aws4_request".toByteArray(Charsets.UTF_8))
    }

    /** URI-encode tiap segmen path; `/` dipertahankan. Spasi menjadi `%20` (bukan `+`). */
    fun canonicalUri(path: String): String {
        val trimmed = if (path.startsWith("/")) path else "/$path"
        return trimmed.split('/').joinToString("/") { encode(it) }
    }

    fun canonicalQuery(query: String): String {
        if (query.isBlank()) return ""
        return query.split('&')
            .filter { it.isNotBlank() }
            .map { pair ->
                val key = pair.substringBefore('=')
                val value = if (pair.contains('=')) pair.substringAfter('=') else ""
                encode(key) to encode(value)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .joinToString("&") { "${it.first}=${it.second}" }
    }

    fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val ch = byte.toInt() and 0xFF
            val isUnreserved = (ch in 'A'.code..'Z'.code) || (ch in 'a'.code..'z'.code) ||
                (ch in '0'.code..'9'.code) || ch == '-'.code || ch == '_'.code || ch == '.'.code || ch == '~'.code
            if (isUnreserved) append(ch.toChar())
            else append('%').append(HEX[ch shr 4]).append(HEX[ch and 0x0F])
        }
    }

    fun sha256Hex(data: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(data))

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun amzDate(epochMillis: Long): String {
        val format = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(java.util.Date(epochMillis))
    }

    private fun defaultPort(scheme: String?): Int = if (scheme == "http") 80 else 443

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
}
