package com.harimu.app.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.harimu.app.R
import com.harimu.app.core.design.MonoTextStyle

private data class LibraryLicenseEntry(
    val component: String,
    val license: String,
    val usageId: String,
    val usageEn: String
)

private val OPEN_SOURCE_LICENSES = listOf(
    LibraryLicenseEntry(
        component = "AndroidX (core-ktx, activity-compose, navigation-compose, room, datastore, exifinterface, lifecycle, work)",
        license = "Apache-2.0",
        usageId = "Komponen resmi Android Jetpack",
        usageEn = "Official Android Jetpack components"
    ),
    LibraryLicenseEntry(
        component = "Jetpack Compose & Material 3",
        license = "Apache-2.0",
        usageId = "UI & theming Material Design 3",
        usageEn = "Material Design 3 UI & theming"
    ),
    LibraryLicenseEntry(
        component = "Kotlin Standard Library, Coroutines, kotlinx-serialization",
        license = "Apache-2.0",
        usageId = "Bahasa, konkurensi, dan serialisasi JSON",
        usageEn = "Language runtime, concurrency, and JSON serialization"
    ),
    LibraryLicenseEntry(
        component = "Coil (io.coil-kt:coil-compose:2.7.0)",
        license = "Apache-2.0",
        usageId = "Pemuatan preview gambar hasil",
        usageEn = "Result and preview image loading"
    ),
    LibraryLicenseEntry(
        component = "OkHttp (com.squareup.okhttp3:okhttp:4.12.0)",
        license = "Apache-2.0",
        usageId = "Klien HTTP untuk AI Gateway, unduhan model/tessdata, dan koneksi cloud",
        usageEn = "HTTP client for AI Gateway, model/tessdata downloads, and cloud connections"
    ),
    LibraryLicenseEntry(
        component = "Plus Jakarta Sans & JetBrains Mono (res/font/)",
        license = "SIL OFL 1.1",
        usageId = "Bundel font lokal dari Google Fonts",
        usageEn = "Locally bundled fonts from Google Fonts"
    ),
    LibraryLicenseEntry(
        component = "Android Platform APIs (MediaExtractor, MediaMuxer, MediaCodec, PdfRenderer, PdfDocument, AudioRecord, TextToSpeech, AndroidKeyStore)",
        license = "Android SDK / Apache-2.0",
        usageId = "Engine pemrosesan lokal bawaan sistem",
        usageEn = "Built-in Android local processing APIs"
    ),
    LibraryLicenseEntry(
        component = "Jetpack Media3 (media3-transformer, media3-effect, media3-common 1.11.1)",
        license = "Apache-2.0",
        usageId = "Transcoding, remux, & efek media akselerasi perangkat keras",
        usageEn = "Hardware-accelerated media transcoding, remuxing, & effects"
    ),
    LibraryLicenseEntry(
        component = "ffmpeg-kit-full (dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9 + smart-exception-java:0.2.1)",
        license = "LGPL-3.0",
        usageId = "Wrapper FFmpeg/FFprobe terpelihara (non-GPL, dukungan 16 KB page size; split ABI via AAB)",
        usageEn = "Maintained FFmpeg/FFprobe wrapper (non-GPL, 16 KB page size support; ABI split via AAB)"
    ),
    LibraryLicenseEntry(
        component = "PDFBox-Android (com.tom-roush:pdfbox-android:2.0.27.0)",
        license = "Apache-2.0",
        usageId = "Operasi PDF pada objek asli: gabung, pisah, putar, ekstrak, watermark, metadata, enkripsi AES-256/128/RC4-128, AcroForm, dan ekstraksi teks",
        usageEn = "Native-object PDF manipulation: merge, split, rotate, extract, watermark, metadata, AES-256/128/RC4-128 encryption, AcroForm, and text extraction"
    ),
    LibraryLicenseEntry(
        component = "Tesseract4Android (tesseract4android-openmp:4.8.0) + tessdata_fast",
        license = "Apache-2.0",
        usageId = "OCR on-device (Tesseract 5 + OpenMP). Berkas tessdata_fast diunduh saat runtime ke filesDir/tessdata, tidak dibundel di APK",
        usageEn = "On-device OCR (Tesseract 5 + OpenMP). tessdata_fast language files are downloaded at runtime to filesDir/tessdata, not bundled in APK"
    ),
    LibraryLicenseEntry(
        component = "Google ML Kit Face Detection (com.google.mlkit:face-detection:16.1.7)",
        license = "Apache-2.0 / Google APIs ToS",
        usageId = "Deteksi wajah on-device untuk VID-25 (dukungan 16 KB page size)",
        usageEn = "On-device face detection for VID-25 (16 KB page size supported)"
    ),
    LibraryLicenseEntry(
        component = "Google ML Kit Subject Segmentation (play-services-mlkit-subject-segmentation)",
        license = "Apache-2.0 / Google APIs ToS",
        usageId = "Segmentasi latar penuh on-device via Google Play Services untuk IMG-12, IMG-43, dan IMG-47 dengan fallback otomatis ke flood-fill Dasar",
        usageEn = "On-device full background segmentation via Google Play Services for IMG-12, IMG-43, and IMG-47 with automatic fallback to Basic flood-fill"
    ),
    LibraryLicenseEntry(
        component = "AndroidSVG (com.caverock:androidsvg:1.4)",
        license = "Apache-2.0",
        usageId = "Rasterisasi SVG (CNV-10); filter efek, teks font sistem, dan animasi tidak dirender",
        usageEn = "SVG rasterization (CNV-10); filter effects, system-font text, and animations are not rendered"
    ),
    LibraryLicenseEntry(
        component = "Apache Commons Compress (org.apache.commons:commons-compress:1.27.1)",
        license = "Apache-2.0",
        usageId = "Baca arsip 7Z dan TAR.GZ (CNV-17); penulisan selalu ZIP",
        usageEn = "Read 7Z and TAR.GZ archives (CNV-17); outputs are always ZIP"
    ),
    LibraryLicenseEntry(
        component = "XZ for Java (org.tukaani:xz:1.10)",
        license = "Public Domain",
        usageId = "Dekoder LZMA2 untuk arsip 7Z (dibutuhkan commons-compress)",
        usageEn = "LZMA2 decoder for 7Z archives (required by commons-compress)"
    ),
    LibraryLicenseEntry(
        component = "junrar (com.github.junrar:junrar:7.5.5)",
        license = "UnRAR License (freeware, no-fee)",
        usageId = "Baca arsip RAR/CBR saja (CNV-17/CNV-20); tidak ada encoder RAR proprietary",
        usageEn = "Read-only RAR/CBR extraction (CNV-17/CNV-20); no proprietary RAR encoder bundled"
    ),
    LibraryLicenseEntry(
        component = "zip4j (net.lingala.zip4j:zip4j:2.11.5)",
        license = "Apache-2.0",
        usageId = "ZIP terenkripsi AES-256 standar (CNV-18); kata sandi tidak disimpan",
        usageEn = "Standard AES-256 encrypted ZIP archives (CNV-18); passwords are never stored"
    ),
    LibraryLicenseEntry(
        component = "JUnit 4, Robolectric, AndroidX Test, WorkManager Testing",
        license = "EPL-1.0 / MIT / Apache-2.0",
        usageId = "Pengujian unit JVM lokal saja (testImplementation, tidak masuk APK rilis)",
        usageEn = "Local JVM unit testing only (testImplementation, excluded from release APK)"
    )
)

private val LICENSE_POLICY_NOTES_ID = listOf(
    "Tidak ada library GPL (seperti build FFmpeg -gpl dengan x264/x265) atau AGPL (seperti iText AGPL / MuPDF AGPL) yang dibundel di APK.",
    "Varian FFmpeg yang dipakai adalah ffmpeg-kit-full (LGPL-3.0) dengan penautan dinamis pustaka native (.so) melalui App Bundle split ABI.",
    "Data bahasa OCR diambil dari repositori resmi tesseract-ocr/tessdata_fast (Apache-2.0) hanya atas permintaan pengguna.",
    "Perekam layar/suara, Key Vault, dan koneksi cloud memakai Platform Android + OkHttp (Apache-2.0) tanpa SDK vendor tambahan."
)

private val LICENSE_POLICY_NOTES_EN = listOf(
    "No GPL (such as FFmpeg -gpl builds with x264/x265) or AGPL (such as iText AGPL / MuPDF AGPL) libraries are bundled in the APK.",
    "The bundled FFmpeg variant is ffmpeg-kit-full (LGPL-3.0) with dynamically linked native libraries (.so) distributed via App Bundle ABI splits.",
    "OCR language models are fetched from the official tesseract-ocr/tessdata_fast repository (Apache-2.0) only on explicit user action.",
    "Screen/Voice Recorder, Key Vault, and cloud uploads use standard Android Platform APIs + OkHttp (Apache-2.0) with no proprietary cloud vendor SDKs."
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    isEnglish: Boolean,
    onBack: () -> Unit
) {
    val policyNotes = if (isEnglish) LICENSE_POLICY_NOTES_EN else LICENSE_POLICY_NOTES_ID

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.licenses_screen_title)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("licenses_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("open_source_licenses_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Gavel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.licenses_intro),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                items(OPEN_SOURCE_LICENSES, key = { it.component }) { entry ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = entry.component,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = entry.license,
                                    style = MonoTextStyle,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = if (isEnglish) entry.usageEn else entry.usageId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.licenses_policy_title),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            policyNotes.forEach { note ->
                                Text(
                                    text = "• $note",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
