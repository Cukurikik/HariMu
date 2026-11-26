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
| `androidx.paging:paging-compose:3.3.2` | Apache-2.0 | Memuat bertahap daftar desa/kelurahan (`NUS-03`) agar daftar besar tidak dimuat sekaligus |
| Robolectric (`4.16.1`), AndroidX Test (`1.7.0` / `1.3.0`), `work-testing` (`2.10.1`) | MIT / Apache-2.0 | Pengujian JVM lokal (`testImplementation`, tidak masuk APK) |

## Sumber Data Pihak Ketiga (Fase 15)

Tidak ada library baru di APK. Data berikut diambil lewat `backend/ai-gateway` dan atribusinya ditampilkan di ringkasan hasil tool:

| Sumber | Lisensi / Ketentuan | Dipakai oleh | Catatan |
|---|---|---|---|
| Open-Meteo (forecast + geocoding) | Data CC BY 4.0; API gratis **hanya non-komersial** (< 10.000 panggilan/hari, 5.000/jam, 600/menit) | `CORE-12` | Atribusi wajib. Bila HariMu dipasarkan/monetisasi, wajib langganan Open-Meteo atau ganti provider |
| Frankfurter (kurs referensi bank sentral) | Gratis termasuk komersial, tanpa kunci, tanpa kuota; cache dianjurkan | `CORE-13` | Bukan kuotasi pasar langsung; tanggal kurs ditampilkan |
| Wiktionary via dictionaryapi.dev | Data CC BY-SA, API tanpa kunci, tanpa SLA | `CORE-14` | Kegagalan provider ditampilkan sebagai kegagalan, bukan definisi kosong |
| BMKG (`data.bmkg.go.id`, `api.bmkg.go.id`) | Data publik pemerintah, API tanpa kunci, tanpa SLA | `NUS-01`, `NUS-02` | Atribusi "Sumber: BMKG" ditampilkan; keputusan keselamatan tetap mengikuti kanal resmi BMKG |
| emsifa `api-wilayah-indonesia` | Data wilayah publik, API statis tanpa kunci (MIT pada repositori) | `NUS-02`, `NUS-03` | Kode hierarkis dipakai langsung sebagai `adm4` BMKG |
| yogski `indonesia-public-static-api` | Data referensi statis, tanpa kunci | `NUS-04`, `NUS-05` | Daftar pahlawan nasional & gunung berapi |
| doa-doa-api (Ahmad Ramadhan) | Dataset doa terbuka, tanpa kunci | `NUS-06` | Teks Arab/latin/arti; pengguna diminta mencocokkan dengan guru |
| api-sekolah-indonesia (wanrabbae) | Dataset sekolah publik, API tanpa kunci | `NUS-07` | Data referensi Kemendikdasmen; tidak ada SLA |
| hibersunda (Hiberin Labs) | Kamus Sunda terbuka, API tanpa kunci | `NUS-08` | Kamus undak usuk basa; hasil kosong bukan error |
| kunci-tts-api (nasrul21) | Bank jawaban TTS komunitas, API tanpa kunci | `NUS-09` | Jawaban komunitas, bukan kamus resmi |
| Quran API ID (renomureza) | Teks Kemenag + terjemahan, API tanpa kunci | `NUS-10` | Pengguna diminta mencocokkan bacaan dengan mushaf tercetak |
| football-standings-api (azharimm) | Data ESPN, API tanpa kunci | `NUS-11` | Atribusi sumber ditampilkan; bukan layanan resmi liga |
| api-pesantren-indonesia (nasrul21) | Data Kemenag, API tanpa kunci | `NUS-12` | Hasil scraper publik; verifikasi ke kanal Kemenag |
| LibreTranslate (bila `TRANSLATE_BASE_URL` diisi) | Server AGPL-3.0; instance publik butuh kunci berbayar | `CORE-11` | Dijalankan sebagai layanan terpisah di server, tidak ditautkan ke APK |

## Catatan Kebijakan Lisensi (`01-ARSITEKTUR.md` & `SKILL.md`)
- Tidak ada library **GPL** (seperti build FFmpeg `-gpl` dengan `x264`/`x265`) atau **AGPL** (seperti iText AGPL) yang dibundel di dalam proyek.
- Varian FFmpeg yang dipakai adalah **`ffmpeg-kit-full` (LGPL v3.0)** dengan penautan dinamis pustaka native (`.so`) dalam App Bundle split ABI.
- Setiap penambahan library pihak ketiga baru di fase berikutnya wajib dicatat di berkas ini sebelum digabungkan ke cabang utama.
- FASE 12 memakai **PDFBox-Android (Apache-2.0)** dan **Tesseract4Android (Apache-2.0)**. iText (AGPL) dan MuPDF (AGPL) tetap **tidak dipakai**.
- FASE 13 memakai **AndroidSVG (Apache-2.0)**, **Apache Commons Compress (Apache-2.0)**, **XZ for Java (Public Domain)**, **junrar (UnRAR License)** dan **zip4j (Apache-2.0)**. junrar hanya untuk membaca RAR; tidak ada encoder RAR proprietary yang dibundel. Semua dependensi berasal dari Maven Central.
- Data bahasa OCR diunduh dari repositori resmi `tesseract-ocr/tessdata_fast` (Apache-2.0) atas permintaan pengguna, bukan aset yang ikut didistribusikan.
- FASE 14 **tidak menambah library pihak ketiga**: Screen/Voice Recorder memakai MediaProjection, MediaCodec, MediaMuxer, AudioRecord, AudioPlaybackCapture (semua Android platform); Key Vault memakai AndroidKeyStore + `KeyguardManager` (tanpa `androidx.biometric`); Cloud memakai **OkHttp 4.12.0 (Apache-2.0)** yang sudah ada. Tidak ada SDK vendor cloud yang dibundel; OAuth/SigV4 diimplementasikan sendiri.
