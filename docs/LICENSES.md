# Lisensi Library & Aset HariMu

Semua library dan aset di proyek ini diverifikasi memakai lisensi permisif (tanpa dependensi GPL/AGPL di APK).

| Komponen | Lisensi | Catatan Penggunaan |
|---|---|---|
| AndroidX (`core-ktx`, `activity-compose`, `navigation-compose`, `room`, `datastore`, `exifinterface`, `lifecycle`) | Apache-2.0 | Komponen resmi Android Jetpack |
| Jetpack Compose & Material 3 | Apache-2.0 | UI & theming Material Design 3 |
| Kotlin Standard Library, Coroutines, `kotlinx-serialization` | Apache-2.0 | Bahasa, konkurensi, dan serialisasi JSON |
| Coil (`io.coil-kt:coil-compose:2.7.0`) | Apache-2.0 | Pemuatan preview gambar hasil |
| JUnit 4 (`junit:junit:4.13.2`) | EPL-1.0 | Pengujian unit JVM (`testImplementation` saja, tidak masuk APK) |
| Font `Plus Jakarta Sans` & `JetBrains Mono` (`app/src/main/res/font/`) | SIL OFL 1.1 | Bundel lokal dari Google Fonts |
| Android Platform APIs (`MediaExtractor`, `MediaMuxer`, `MediaCodec`, `PdfRenderer`, `PdfDocument`, `AudioRecord`, `TextToSpeech`, `AndroidKeyStore`) | Android SDK / Apache-2.0 | Engine pemrosesan lokal bawaan sistem |
| Jetpack Media3 (`media3-transformer`, `media3-effect`, `media3-common` `1.11.1`) | Apache-2.0 | Transcoding, remux, & efek media akselerasi perangkat keras |
| `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9` (`+ smart-exception-java:0.2.1`) | LGPL-3.0 | Wrapper FFmpeg/FFprobe terpelihara (non-GPL, dukungan 16 KB page size; AAR 30 MB, split ABI via AAB) |
| `com.tom-roush:pdfbox-android:2.0.27.0` | Apache-2.0 | Operasi PDF pada objek asli: gabung, pisah, putar, susun ulang, ekstrak, watermark teks/gambar, metadata, enkripsi AES-256/AES-128/RC4-128, AcroForm, dan ekstraksi teks |
| `com.github.adaptech-cz.Tesseract4Android:tesseract4android-openmp:4.8.0` (JitPack) | Apache-2.0 | OCR on-device (Tesseract 5 + OpenMP). Data bahasa `tessdata_fast` resmi (Apache-2.0) diunduh saat runtime ke `filesDir/tessdata`, **tidak** dibundel di APK. Artefak hanya tersedia via repositori JitPack (`settings.gradle.kts`) |
| Google ML Kit Face Detection (`com.google.mlkit:face-detection:16.1.7`) | Apache-2.0 / Google APIs ToS | Deteksi & pelacakan wajah on-device untuk `VID-25` (dukungan 16 KB page size) |
| Google ML Kit Subject Segmentation (`com.google.android.gms:play-services-mlkit-subject-segmentation`, versi belum diverifikasi — bergantung hasil CI) | Apache-2.0 / Google APIs ToS | Segmentasi latar penuh on-device via Google Play Services untuk `IMG-12`, `IMG-43`, dan `IMG-47` (modul diunduh Play Services, tidak dibundel di APK; fallback otomatis ke flood-fill Dasar) |
| `com.caverock:androidsvg:1.4` | Apache-2.0 | Rasterisasi SVG (CNV-10). Filter efek, teks berfont sistem, dan animasi tidak dirender |
| `org.apache.commons:commons-compress:1.27.1` | Apache-2.0 | Baca 7Z dan TAR.GZ (CNV-17). Hanya baca; penulisan selalu ZIP |
| `org.tukaani:xz:1.10` | Public Domain | Dekoder LZMA2 untuk 7Z (dibutuhkan commons-compress) |
| `com.github.junrar:junrar:7.5.5` | UnRAR License (freeware, no-fee) | **Baca** arsip RAR/CBR saja (CNV-17/CNV-20). Encoder RAR proprietary tidak dipakai |
| `net.lingala.zip4j:zip4j:2.11.5` | Apache-2.0 | ZIP AES-256 standar (CNV-18): enkripsi/dekripsi, kata sandi tidak disimpan |
| Robolectric (`4.16.1`), AndroidX Test (`1.7.0` / `1.3.0`), `work-testing` (`2.10.1`) | MIT / Apache-2.0 | Pengujian JVM lokal (`testImplementation`, tidak masuk APK) |

## Catatan Kebijakan Lisensi (`01-ARSITEKTUR.md` & `SKILL.md`)
- Tidak ada library **GPL** (seperti build FFmpeg `-gpl` dengan `x264`/`x265`) atau **AGPL** (seperti iText AGPL) yang dibundel di dalam proyek.
- Varian FFmpeg yang dipakai adalah **`ffmpeg-kit-full` (LGPL v3.0)** dengan penautan dinamis pustaka native (`.so`) dalam App Bundle split ABI.
- Setiap penambahan library pihak ketiga baru di fase berikutnya wajib dicatat di berkas ini sebelum digabungkan ke cabang utama.
- FASE 12 memakai **PDFBox-Android (Apache-2.0)** dan **Tesseract4Android (Apache-2.0)**. iText (AGPL) dan MuPDF (AGPL) tetap **tidak dipakai**.
- FASE 13 memakai **AndroidSVG (Apache-2.0)**, **Apache Commons Compress (Apache-2.0)**, **XZ for Java (Public Domain)**, **junrar (UnRAR License)** dan **zip4j (Apache-2.0)**. junrar hanya untuk membaca RAR; tidak ada encoder RAR proprietary yang dibundel. Semua dependensi berasal dari Maven Central.
- Data bahasa OCR diunduh dari repositori resmi `tesseract-ocr/tessdata_fast` (Apache-2.0) atas permintaan pengguna, bukan aset yang ikut didistribusikan.
- FASE 14 **tidak menambah library pihak ketiga**: Screen/Voice Recorder memakai MediaProjection, MediaCodec, MediaMuxer, AudioRecord, AudioPlaybackCapture (semua Android platform); Key Vault memakai AndroidKeyStore + `KeyguardManager` (tanpa `androidx.biometric`); Cloud memakai **OkHttp 4.12.0 (Apache-2.0)** yang sudah ada. Tidak ada SDK vendor cloud yang dibundel; OAuth/SigV4 diimplementasikan sendiri.
