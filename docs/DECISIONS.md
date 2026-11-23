# Keputusan Teknis HariMu (Android)

1. **Arsitektur Registry-Driven (`ToolRegistry` & `ToolContract`)**
   Semua tool terdaftar terpusat di `ToolRegistry` dan dirender secara dinamis oleh `ToolScreen` berdasarkan `InputSpec` dan `ToolParam`, sedangkan tool interaktif kompleks (Voice Recorder, Image Matrix, Equalizer 5-Band, Key Vault, Backup `.omni`) memakai layar studio khusus.

2. **Engine On-Device Tanpa Biner GPL**
   - **Media/Video**: Memakai Android native `MediaExtractor`, `MediaMuxer`, dan `MediaMetadataRetriever` (tanpa dependensi FFmpeg GPL).
   - **Audio DSP**: Memakai `MediaCodec` PCM decoder + DSP Kotlin langsung (gain, trim, mix/concat, manipulasi kanal, silence remover, reverse, echo, fade, waveform PNG) serta `android.media.audiofx.Equalizer` dan `AudioRecord` / `TextToSpeech`.
   - **Gambar**: Memakai `Bitmap`, `Canvas`, `ColorMatrix`, `Matrix`, `ExifInterface`, dan klasterisasi warna `K-Means`.
   - **PDF**: Memakai Android native `PdfRenderer` dan `PdfDocument` (merge, split, rotate, extract, image-to-PDF, PDF-to-image, watermark, page numbering, pixel-diff comparison).
   - **Konverter & Kripto**: Memakai `java.util.zip`, PBKDF2 + AES-256-GCM (`.hzm` & `.omni`), `AndroidKeyStore` + JCA (AES-256, ECDSA P-256, RSA-2048), serta parser native `kotlinx.serialization.json` (JSON/XML, CSV/TSV, Markdown/HTML, GeoJSON/KML, ASCII/Binary STL→OBJ).

3. **Toolchain Build & Analisis Statis (Fase 0)**
   - **AGP `9.1.1` + Kotlin `2.2.10` + KSP `2.3.5`**: Memakai dukungan Kotlin bawaan AGP 9 tanpa plugin terpisah `org.jetbrains.kotlin.android`.
   - **Style & CI**: `.editorconfig` dan `config/detekt/detekt.yml` disiapkan di repo; GitHub Actions (`.github/workflows/android-ci.yml`) menjalankan `ktlint`, `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, dan `./gradlew lintDebug` tanpa menambahkan plugin Gradle Detekt 1.x yang berpotensi bentrok dengan Kotlin 2.2.

4. **Fitur Cloud & Model Besar**
   Tool yang bergantung pada backend Cloudflare Worker atau model ML unduhan besar (seperti `CORE-02` Prompt Video Gen, `IMG-21` AI Image Gen, `PDF-06` PDF↔Office LibreOffice) tidak di-stub dengan respons palsu; konfigurasi endpoint S3/R2/Worker tersedia di Setelan saat backend disambungkan.

5. **Infrastruktur Inti & Antrean Job (Fase 2)**
   - **Katalog Penuh 163 Tool**: `ToolRegistry` mendaftarkan 59 tool aktif + 104 `StubTool` (`"Segera hadir"`) sehingga seluruh 163 ID di `02-KATALOG-FITUR.md` dapat dicari, difavoritkan, dan dibuka di `ToolScreen`.
   - **Antrean & Pemulihan WorkManager**: `JobManager` membatasi maks. 2 job berat paralel lewat `JobQueuePolicy` (FIFO) yang disinkronkan dengan tabel Room `jobs` (`JobEntity`). Saat `HariMuApp.onCreate()` berjalan, `recoverPendingJobs()` menyelaraskan status `WorkManager` atau menjadwalkan ulang job yang terputus setelah proses dimatikan.
   - **File, SAF & Model ML**: `FileStorageManager` menamai keluaran `HariMu_<tool>_<timestamp>.<ext>`, membersihkan direktori temp saat startup dan setelah tiap job, memeriksa ruang disk bebas sebelum eksekusi, serta mendukung ekspor ke folder default SAF (`OpenDocumentTree`), `MediaStore`, atau `CreateDocument` dan berbagi via `FileProvider`. `ModelManager` menyimpan model lokal di `noBackupFilesDir/models`.
   - **Cloud Connections**: Bagian Cloud Connections di `SettingsScreen` hadir sebagai UI kosong terdaftar yang siap dihubungkan pada P14.
