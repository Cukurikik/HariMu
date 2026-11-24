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
| Google ML Kit Face Detection (`com.google.mlkit:face-detection:16.1.7`) | Apache-2.0 / Google APIs ToS | Deteksi & pelacakan wajah on-device untuk `VID-25` (dukungan 16 KB page size) |
| Robolectric (`4.16.1`), AndroidX Test (`1.7.0` / `1.3.0`), `work-testing` (`2.10.1`) | MIT / Apache-2.0 | Pengujian JVM lokal (`testImplementation`, tidak masuk APK) |

## Catatan Kebijakan Lisensi (`01-ARSITEKTUR.md` & `SKILL.md`)
- Tidak ada library **GPL** (seperti build FFmpeg `-gpl` dengan `x264`/`x265`) atau **AGPL** (seperti iText AGPL) yang dibundel di dalam proyek.
- Varian FFmpeg yang dipakai adalah **`ffmpeg-kit-full` (LGPL v3.0)** dengan penautan dinamis pustaka native (`.so`) dalam App Bundle split ABI.
- Setiap penambahan library pihak ketiga baru di fase berikutnya wajib dicatat di berkas ini sebelum digabungkan ke cabang utama.
