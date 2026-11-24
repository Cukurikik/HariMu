package com.harimu.app.core.engines

/** Honest support level of a container/codec combination on stock Android (minSdk 26). */
enum class FormatSupportLevel(val labelId: String, val labelEn: String) {
    FULL("Didukung", "Supported"),
    LIMITED("Didukung terbatas", "Limited support"),
    NONE("Tidak didukung", "Not supported")
}

data class ImageFormatSupport(
    val id: String,
    val extensions: List<String>,
    val decode: FormatSupportLevel,
    val encode: FormatSupportLevel,
    val library: String,
    val noteId: String,
    val noteEn: String
) {
    val canEncode: Boolean get() = encode != FormatSupportLevel.NONE
    val canDecode: Boolean get() = decode != FormatSupportLevel.NONE
}

/**
 * The single source of truth for the format matrix documented in `docs/DECISIONS.md` §12.
 * The converter (IMG-23) refuses anything that is not [FULL]/[LIMITED] for encoding instead
 * of silently writing a mislabelled file.
 */
object ImageFormatMatrix {

    val entries: List<ImageFormatSupport> = listOf(
        ImageFormatSupport(
            id = "JPG",
            extensions = listOf("jpg", "jpeg"),
            decode = FormatSupportLevel.FULL,
            encode = FormatSupportLevel.FULL,
            library = "android.graphics.BitmapFactory / Bitmap.compress(JPEG)",
            noteId = "Baca/tulis penuh. Profil ICC JPEG disalin ke hasil, EXIF bisa dipertahankan.",
            noteEn = "Full read/write. JPEG ICC profiles are copied to output, EXIF can be preserved."
        ),
        ImageFormatSupport(
            id = "PNG",
            extensions = listOf("png"),
            decode = FormatSupportLevel.FULL,
            encode = FormatSupportLevel.FULL,
            library = "android.graphics.BitmapFactory / Bitmap.compress(PNG)",
            noteId = "Baca/tulis penuh, transparansi terjaga. Profil ICC PNG tidak dapat disuntikkan lewat API Android.",
            noteEn = "Full read/write, alpha preserved. PNG ICC profiles cannot be injected through the Android API."
        ),
        ImageFormatSupport(
            id = "HEIC",
            extensions = listOf("heic", "heif"),
            decode = FormatSupportLevel.LIMITED,
            encode = FormatSupportLevel.NONE,
            library = "Platform decoder (ImageDecoder/BitmapFactory, Android 9 / API 28+)",
            noteId = "Decode hanya API 28+ dan tergantung dukungan pabrikan; encode HEIC tidak ada di Android.",
            noteEn = "Decode only on API 28+ and device dependent; Android has no HEIC encoder."
        ),
        ImageFormatSupport(
            id = "WebP",
            extensions = listOf("webp"),
            decode = FormatSupportLevel.FULL,
            encode = FormatSupportLevel.FULL,
            library = "BitmapFactory / Bitmap.CompressFormat.WEBP_LOSSY",
            noteId = "Decode penuh (lossless API 28+). Encode lossy penuh; lossless butuh API 30+.",
            noteEn = "Full decode (lossless on API 28+). Lossy encode everywhere; lossless needs API 30+."
        ),
        ImageFormatSupport(
            id = "AVIF",
            extensions = listOf("avif", "avifs"),
            decode = FormatSupportLevel.LIMITED,
            encode = FormatSupportLevel.NONE,
            library = "Platform decoder (Android 12 / API 31+ only)",
            noteId = "Decode hanya API 31+ dan tergantung perangkat. Encoder AVIF tidak tersedia di Android.",
            noteEn = "Decode only on API 31+ and device dependent. No AVIF encoder on Android."
        ),
        ImageFormatSupport(
            id = "SVG",
            extensions = listOf("svg"),
            decode = FormatSupportLevel.NONE,
            encode = FormatSupportLevel.NONE,
            library = "Belum ada (butuh AndroidSVG/Compose vector parser)",
            noteId = "BitmapFactory tidak membaca SVG. Tool ini tidak memalsukan hasil; tambahkan AndroidSVG bila dibutuhkan.",
            noteEn = "BitmapFactory cannot read SVG. This tool does not fake output; add AndroidSVG if needed."
        ),
        ImageFormatSupport(
            id = "RAW",
            extensions = listOf("dng", "cr2", "nef", "arw", "orf", "rw2"),
            decode = FormatSupportLevel.LIMITED,
            encode = FormatSupportLevel.NONE,
            library = "Platform DNG decoder (sebagian perangkat) — tanpa libraw",
            noteId = "Hanya DNG dan hanya pada perangkat yang menyediakan decoder; CR2/NEF/ARW dll. tidak didukung.",
            noteEn = "DNG only, and only where the device ships a decoder; CR2/NEF/ARW etc. are unsupported."
        ),
        ImageFormatSupport(
            id = "TIFF",
            extensions = listOf("tif", "tiff"),
            decode = FormatSupportLevel.NONE,
            encode = FormatSupportLevel.NONE,
            library = "Belum ada (butuh libtiff/Apache Commons Imaging)",
            noteId = "Tidak dibaca maupun ditulis. Butuh library TIFF permisif, belum ditambahkan.",
            noteEn = "Neither read nor written. Needs a permissive TIFF library, not bundled."
        ),
        ImageFormatSupport(
            id = "BMP",
            extensions = listOf("bmp", "dib"),
            decode = FormatSupportLevel.FULL,
            encode = FormatSupportLevel.NONE,
            library = "BitmapFactory.decodeStream (BMP24/32)",
            noteId = "Decode BMP didukung platform; tidak ada encoder BMP di Android.",
            noteEn = "Platform decodes BMP; Android has no BMP encoder."
        ),
        ImageFormatSupport(
            id = "PSD",
            extensions = listOf("psd", "psb"),
            decode = FormatSupportLevel.NONE,
            encode = FormatSupportLevel.NONE,
            library = "Belum ada (butuh psd.java/library PSD)",
            noteId = "Layer PSD tidak dapat diparse Android. Tidak didukung pada batch ini.",
            noteEn = "Android cannot parse PSD layers. Unsupported in this batch."
        ),
        ImageFormatSupport(
            id = "PDF",
            extensions = listOf("pdf"),
            decode = FormatSupportLevel.NONE,
            encode = FormatSupportLevel.FULL,
            library = "android.graphics.pdf.PdfDocument (tulis), PdfRenderer di PDF Vault (baca)",
            noteId = "Encode gambar→PDF didukung. Baca PDF→gambar dilakukan di PDF Vault (PdfRenderer), bukan di sini.",
            noteEn = "Image→PDF encoding is supported. PDF→image lives in PDF Vault (PdfRenderer), not here."
        )
    )

    val encodeLabels: List<String> get() = entries.filter { it.canEncode }.map { it.id }

    val allLabels: List<String> get() = entries.map { it.id }

    fun byLabel(label: String): ImageFormatSupport? {
        val clean = label.trim()
        return entries.firstOrNull { it.id.equals(clean, ignoreCase = true) }
            ?: entries.firstOrNull { entry -> entry.extensions.any { it.equals(clean, ignoreCase = true) } }
    }

    fun forExtension(extension: String): ImageFormatSupport? {
        val clean = extension.trim().trimStart('.').lowercase()
        if (clean.isEmpty()) return null
        return entries.firstOrNull { entry -> entry.extensions.any { it == clean } }
    }

    /** Throws with a user-facing reason when the requested encode target is impossible. */
    fun requireEncodable(label: String): ImageFormatSupport {
        val entry = byLabel(label)
            ?: throw IllegalArgumentException("Format '$label' tidak dikenal. Pilihan: ${allLabels.joinToString(", ")}")
        if (!entry.canEncode) {
            val available = encodeLabels.joinToString(", ")
            throw IllegalStateException(
                "${entry.id} tidak dapat ditulis di Android (${entry.library}). " +
                    "Pilih salah satu: $available. Atau gunakan PDF Vault untuk membaca PDF."
            )
        }
        return entry
    }

    fun mimeTypeFor(label: String): String = when (label.trim().uppercase()) {
        "JPG", "JPEG" -> "image/jpeg"
        "PNG" -> "image/png"
        "WEBP" -> "image/webp"
        "HEIC", "HEIF" -> "image/heic"
        "AVIF" -> "image/avif"
        "BMP" -> "image/bmp"
        "TIFF" -> "image/tiff"
        "PDF" -> "application/pdf"
        "SVG" -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    fun extensionFor(label: String): String = when (label.trim().uppercase()) {
        "JPG", "JPEG" -> "jpg"
        "PDF" -> "pdf"
        else -> label.trim().lowercase()
    }
}
