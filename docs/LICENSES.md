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

## Catatan Kebijakan Lisensi (`01-ARSITEKTUR.md` & `SKILL.md`)
- Tidak ada library **GPL** (seperti build FFmpeg GPL/x264/x265) atau **AGPL** (seperti iText AGPL) yang dibundel di dalam proyek.
- Setiap penambahan library pihak ketiga baru di fase berikutnya wajib dicatat di berkas ini sebelum digabungkan ke cabang utama.
