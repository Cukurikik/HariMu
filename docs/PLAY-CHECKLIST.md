# Checklist Rilis Google Play & Data Safety — HariMu

Dokumen ini menjadi panduan konfigurasi dan pengisian **Google Play Console** (Data Safety, App Content, Foreground Services, Permissions, dan Privacy Policy) berdasarkan audit statis kode sumber dan `app/src/main/AndroidManifest.xml`.

> **Status:** Draf konfigurasi & dokumentasi sebelum rilis. Belum ada pengiriman ke Play Console atau uji perangkat akhir.

---

## 1. Identitas Aplikasi & Ringkasan Target SDK

| Parameter | Nilai Saat Ini (`app/build.gradle.kts`) | Catatan Kebijakan Play |
|---|---|---|
| `applicationId` | `com.harimu.app` | Unik, konsisten dengan `namespace` |
| `minSdk` | `26` (Android 8.0) | Aman & modern |
| `targetSdk` | `36` | Memenuhi syarat target API level Play terbaru |
| Nama Aplikasi (`app_name`) | `HariMu` (6 karakter) | Memenuhi batas ≤30 karakter, tanpa emoji, tanpa ALL CAPS/buzzword |
| ABI Splits / Bundle | `enableSplit = true` (ABI & density), `language.enableSplit = false` | Bahasa ID/EN bisa dipilih manual di UI tanpa restart Play Feature Delivery |
| Minify & Shrink | `isMinifyEnabled = true`, `isShrinkResources = true` di `release` | Pastikan aturan `proguard-rules.pro` diuji pada APK/AAB rilis |

---

## 2. Audit Izin `AndroidManifest.xml`

Daftar lengkap izin yang dideklarasikan di `app/src/main/AndroidManifest.xml`:

| Izin Manifest | Jenis | Tempat Dipakai | Keterangan Kepatuhan |
|---|---|---|---|
| `android.permission.RECORD_AUDIO` | Berbahaya (Runtime) | `CORE-06` Voice Recorder (`VoiceRecorderScreen.kt`), `CORE-05` Screen Recorder mode Mic/Both (`ScreenRecorderScreen.kt`) | Diminta *just-in-time* dengan rationale (`permission_audio_rationale`); hanya saat pengguna memulai rekaman audio/suara |
| `android.permission.POST_NOTIFICATIONS` | Berbahaya (Runtime, API 33+) | Kontrol notifikasi `VoiceRecordingService`, `ScreenRecorderService`, & progres `ToolJobWorker` (`SystemForegroundService`) | Diminta saat menjalankan perekaman atau job latar pada Android 13+ |
| `android.permission.FOREGROUND_SERVICE` | Normal | Semua foreground service | Wajib untuk FGS di API 28+ |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Normal (dideklarasi + deklarasi Play) | `.core.recording.VoiceRecordingService` (`CORE-06`) | Wajib di Android 14+ untuk perekaman suara latar |
| `android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION` | Normal (dideklarasi + deklarasi Play) | `.core.recording.ScreenRecorderService` (`CORE-05`) | Wajib di Android 14+ untuk rekam layar |
| `android.permission.FOREGROUND_SERVICE_DATA_SYNC` | Normal (dideklarasi + deklarasi Play) | `androidx.work.impl.foreground.SystemForegroundService` (`JobManager` / `ToolJobWorker`) | Lihat catatan evaluasi `dataSync` vs `mediaProcessing` di Bagian 4 |
| `android.permission.INTERNET` | Normal | AI Gateway (`AiClient`), unduh bahasa OCR (`PdfOcrEngine`), modul Play Services ML Kit (`VID-25`, `IMG-12`), unggah cloud opsional (`CORE-09`) | Aplikasi utama berjalan offline; jaringan hanya dipakai bila pengguna memilih tool berlabel `CL` / OCR / unggah cloud |
| `android.permission.SYSTEM_ALERT_WINDOW` | Spesial (Settings) | Overlay tombol mengambang opsional pada `CORE-05` (`ScreenRecorderScreen.kt` & `ScreenRecorderService.kt`) | **Izin sensitif** — lihat catatan risiko & opsi penghapusan di Bagian 5 |

### Izin Penyimpanan & Media (Nol Izin Luas)
- Aplikasi **TIDAK** mendeklarasikan `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO`, `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, `QUERY_ALL_PACKAGES`, maupun `READ_PHONE_STATE`.
- Pemilihan foto/video memakai **Android Photo Picker** (`ActivityResultContracts.PickVisualMedia` / `PickMultipleVisualMedia`).
- Pemilihan dokumen/audio/ZIP dan folder output memakai **Storage Access Framework (SAF)** (`OpenDocument`, `OpenMultipleDocuments`, `OpenDocumentTree`, `CreateDocument`).

---

## 3. Draf Formulir Play Console: Data Safety

### A. Peta Fitur yang Mengirim Data Keluar Perangkat

Aplikasi bekerja **on-device secara default**, kecuali pada tiga jalur eksplisit yang dipicu pengguna:

1. **Cloudflare AI Gateway (`AiClient`, tool berlabel `CL` / `requiresNetwork = true`)**
   - **Gerbang persetujuan:** Meminta persetujuan eksplisit di UI sebelum unggahan pertama (`ToolScreen` dialog *"Izinkan unggah"* + toggle pencabutan di `SettingsScreen`). Tanpa URL Worker dan persetujuan, tidak ada byte yang dikirim.
   - **Autentikasi klien:** Mengirim `installId` (UUID acak per instalasi, disimpan lokal di DataStore) ke `POST /v1/install` untuk mendapatkan token sesi singkat & menghitung kuota harian. Tidak mengirim nama, email, atau Google Advertising ID.
   - **Daftar tool & data yang dikirim:**
     - `CORE-02` Prompt Video Gen: prompt teks, durasi, rasio aspek → Worker (`REPLICATE_*` jika dikonfigurasi).
     - `CORE-03` Generate Speech (TTS — hanya jika memilih *Engine Gateway*): teks masukan (≤4.000 karakter), ID suara, bahasa → Worker (`@cf/deepgram/aura-1`).
     - `CORE-04` Analyze Images: gambar di-downsample ≤2048 px (JPEG 92%) + teks pertanyaan → Worker (`@cf/meta/llama-3.2-11b-vision-instruct`).
     - `IMG-13` Object Remover, `IMG-14` Photo Enhancer, `IMG-15` Face Restorer, `IMG-16` Style Transfer, `IMG-17` Image Colorizer, `IMG-18` Sky Replacement, `IMG-20` Cartoonizer: gambar di-downsample ≤2048 px (JPEG 92%) + prompt instruksi/gaya → Worker (`@cf/runwayml/stable-diffusion-v1-5-img2img`).
     - `IMG-21` AI Image Generator: prompt teks, ukuran, gaya → Worker (`@cf/black-forest-labs/flux-1-schnell`).
     - `AUD-27` Speech to Text & `CNV-07` Voice to Text Converter (alias): berkas audio + bahasa → Worker (`@cf/openai/whisper`).
     - `PDF-17` AI Document Recognition: hingga 5 halaman PDF dirender sebagai gambar JPEG (130 DPI) + prompt ekstraksi JSON → Worker (`@cf/meta/llama-3.2-11b-vision-instruct`).
2. **Koneksi Cloud Pilihan Pengguna (`CORE-09` / `CloudConnectionsCard` / `CloudUploader`)**
   - Aktif hanya bila pengguna mengetuk **"Unggah ke cloud"** pada `ResultCard` dan memasukkan kredensialnya sendiri (S3/Cloudflare R2, Google Drive via OAuth PKCE `drive.file`, Dropbox via OAuth PKCE, atau endpoint REST kustom).
   - Data yang dikirim langsung ke penyedia milik pengguna sendiri: berkas hasil yang dipilih pengguna dan token OAuth/kunci akses yang disegel lokal via AndroidKeyStore (`SecretBox`).
3. **Unduhan Model & Data Bahasa On-Demand (Read-Only)**
   - **OCR (`PDF-15`, `PDF-16`, `CNV-15`)**: Mengunduh berkas model bahasa `*.traineddata` dari `https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/main` ke `filesDir/tessdata` saat pertama kali dipakai (tidak mengunggah dokumen pengguna).
   - **Google Play Services ML Kit (`VID-25`, `IMG-12`/`IMG-43`/`IMG-47`)**: `ModuleInstallClient` mengunduh modul `face-detection` & `subject_segment` on-device lewat Google Play Services (tidak mengirim foto pengguna ke cloud).

### B. Ringkasan Jawaban Draf Data Safety Play Console

| Pertanyaan Data Safety | Jawaban Disarankan | Penjelasan Teknis |
|---|---|---|
| Does your app collect or share any required user data types? | **Yes** (atau bersyarat — lihat catatan di bawah) | Meskipun fitur lokal 100% offline, tool AI Gateway (`CL`) mengirimkan berkas/teks yang dipilih pengguna ke server AI Gateway Anda, dan `installId` dikirim untuk kuota/token. Jika Play Console menganggap pemrosesan sementara via server developer sebagai *"Collected"*, deklarasikan tipe di bawah. |
| Is all data encrypted in transit? | **Yes** (Wajib pastikan di produksi) | `AiClient` menolak URL non-`https://` kecuali `10.0.2.2` untuk emulator debug; Drive/Dropbox/R2 menggunakan HTTPS. **Catatan rilis:** Nonaktifkan cleartext `10.0.2.2` di build rilis atau tambahkan Network Security Config yang hanya mengizinkan `https://` pada release. |
| Do you provide a way for users to request that their data be deleted? | **Yes / Tidak menyimpan permanen** | Jelaskan bahwa gateway tidak menyimpan berkas media melampaui masa proses request/job singkat, pengguna dapat mencabut izin unggah dan menghapus token (`Forget token`) di Pengaturan, serta menghapus seluruh riwayat/kunci di perangkat. |
| Data types — **Photos and videos** | Collected (Opsional oleh pengguna, Ephemeral) | Dikirim hanya saat pengguna memicu `CORE-04`, `IMG-13..18`, `IMG-20`, `CORE-02`, atau `PDF-17` (halaman PDF sebagai gambar); digunakan untuk *App functionality*. |
| Data types — **Audio** | Collected (Opsional oleh pengguna, Ephemeral) | Dikirim hanya saat pengguna memicu `AUD-27`/`CNV-07` Speech-to-Text; digunakan untuk *App functionality*. |
| Data types — **Files and docs** | Collected (Opsional oleh pengguna, Ephemeral) | Halaman dokumen `PDF-17` dan unggahan ke tujuan cloud yang dikonfigurasi pengguna sendiri (`CORE-09`). |
| Data types — **App interactions / User prompts** | Collected (Opsional oleh pengguna) | Teks prompt untuk `CORE-02`, `CORE-03` (Gateway), `CORE-04`, `IMG-13..18/20/21`. |
| Data types — **Device or other IDs** | Collected (App functionality, Fraud prevention / Rate-limiting) | `installId` UUID acak dibuat oleh aplikasi untuk manajemen kuota AI Gateway; tidak dipakai untuk pelacakan iklan. |
| Advertising / Analytics / Crash SDKs | **None** | Tidak ada AdMob, Firebase Analytics, Crashlytics, maupun pelacak pihak ketiga. |

---

## 4. Deklarasi Foreground Service Type (`targetSdk 36`)

Di Google Play Console (**App Content → Foreground service permissions**), pengembang wajib mendeklarasikan setiap `foregroundServiceType` di manifes beserta video demonstrasi singkat.

| Service Class | `foregroundServiceType` | Fitur Terkait | Materi Verifikasi Play Console yang Harus Disiapkan |
|---|---|---|---|
| `com.harimu.app.core.recording.VoiceRecordingService` | `microphone` | `CORE-06` Voice Recorder | Video pendek menunjukkan pengguna mengetuk Rekam → dialog izin `RECORD_AUDIO` → perekaman berlanjut saat layar dikunci / pindah aplikasi → notifikasi tetap tampil dengan tombol Jeda & Stop. |
| `com.harimu.app.core.recording.ScreenRecorderService` | `mediaProjection` | `CORE-05` Screen Recorder | Video pendek menunjukkan pengguna memulai rekam layar → prompt sistem `MediaProjection` per sesi → indikator/notifikasi rekaman aktif dengan tombol Jeda & Stop saat keluar ke aplikasi lain → video hasil diputar. |
| `androidx.work.impl.foreground.SystemForegroundService` | `dataSync` (`tools:node="merge"`) | `JobManager` / `ToolJobWorker` (job Video, Audio, PDF, Converter yang panjang) | **Catatan Kritis Play Policy:** Google Play mengetatkan penggunaan `dataSync` pada Android 14/15+ untuk tugas yang bukan sinkronisasi data cloud. Untuk tool render video/audio/PDF lokal yang berdurasi panjang, **pertimbangkan di rilis mendatang untuk mengevaluasi migrasi worker lokal ke `mediaProcessing`** (atau tetap `dataSync` hanya jika menyertakan unggah/sinkronisasi cloud dengan justifikasi jelas). Saat ini manifes memakai `dataSync` untuk seluruh `ToolJobWorker`. |

---

## 5. Daftar Izin Sensitif & Risiko Peninjauan Play

1. **`android.permission.SYSTEM_ALERT_WINDOW` ("Display over other apps")**
   - **Dipakai di:** `CORE-05` Screen Recorder untuk tombol kontrol mengambang opsional (`ScreenRecorderScreen.kt:299-303`, `ScreenRecorderService.kt`).
   - **Risiko Play Store:** Google Play sangat selektif menyetujui `SYSTEM_ALERT_WINDOW` kecuali aplikasi membutuhkannya secara mutlak dan tidak ada alternatif yang cukup. Notifikasi persisten (`Pause` / `Resume` / `Stop`) di notification shade sebenarnya sudah memenuhi kebutuhan kontrol saat rekam layar.
   - **Tindakan Sebelum Submit:**
     - **Opsi A (Pertahankan):** Siapkan video demo & alasan kuat bahwa overlay mengambang opsional dibutuhkan untuk jeda/stop instan tanpa menurunkan shade notifikasi saat merekam tutorial/game.
     - **Opsi B (Paling aman untuk Review):** Hapus `SYSTEM_ALERT_WINDOW` dan kode `RecordingOverlay` bila reviewer menolak izin ini; kontrol notifikasi tetap berjalan penuh.
2. **`android.permission.RECORD_AUDIO`**
   - Diterima selama dipicu langsung oleh pengguna di `CORE-05` (opsi audio Mic/Both) dan `CORE-06`, disertai notifikasi foreground aktif.
3. **`android.permission.POST_NOTIFICATIONS`**
   - Hanya dipakai untuk status perekaman aktif dan progres antrean pemrosesan file (tanpa notifikasi promosi/spam).
4. **Kepatuhan Metadata & Kebijakan Konten**
   - Nama aplikasi: `HariMu` (bebas dari kata terlarang seperti "Free", "#1", "Best", tanpa emoji).
   - Tidak ada Dynamic Code Loading (DCL) `.dex`/`.jar`/`.so` dari internet; pustaka native FFmpeg dan Tesseract sudah dibundel di AAB dari Maven/JitPack, sedangkan unduhan `tessdata_fast` hanya berupa file data statistik (`.traineddata`), bukan kode biner eksekusi.

---

## 6. Kebutuhan Kebijakan Privasi (Privacy Policy)

Sebelum menerbitkan di Google Play, host halaman **Kebijakan Privasi (HTTPS publik)** dan tautkan URL-nya di Play Console serta idealnya di layar Pengaturan. Kebijakan Privasi wajib mencakup poin berikut:

1. **Identitas Pengembang & Kontak:** Nama legal pengembang/organisasi dan alamat email dukungan aktif.
2. **Pemrosesan Lokal Secara Default:** Penjelasan bahwa pemrosesan video, audio, gambar, PDF, arsip, serta penyimpanan `Key Vault` (AndroidKeyStore) dan backup `.omni` terenkripsi dilakukan **secara lokal di perangkat**.
3. **Pemrosesan Cloud Opsional (AI Gateway):**
   - Penjelasan kapan data dikirim (hanya saat pengguna menjalankan tool `CL` / TTS mode Gateway dan menyetujui dialog konfirmasi unggah).
   - Jenis data yang dikirim (teks prompt, berkas audio, gambar ≤2048 px / halaman PDF terender, serta `installId` anonim untuk pembatasan kuota harian).
   - Penyedia pemroses hilir yang dipakai Worker (mis. **Cloudflare Workers AI** / **Replicate** jika diaktifkan oleh operator server) serta kebijakan retensi (diproses sementara untuk menyelesaikan permintaan dan tidak disimpan permanen di Worker).
4. **Koneksi Cloud Milik Pengguna (`CORE-09`):** Penjelasan bahwa kredensial S3/R2, Google Drive (`drive.file`), Dropbox, atau REST kustom dikirim langsung ke akun penyedia pihak ketiga pilihan pengguna, sementara token disimpan terenkripsi di perangkat memakai AndroidKeyStore (`SecretBox`).
5. **Unduhan Model & Data Bahasa:** Penjelasan pengunduhan `tessdata_fast` dari GitHub (untuk OCR PDF) dan modul on-device Google ML Kit lewat Google Play Services.
6. **Penjelasan Izin Perangkat:** Mengapa `RECORD_AUDIO`, `MediaProjection`, `POST_NOTIFICATIONS`, dan `SYSTEM_ALERT_WINDOW` (jika dipertahankan) diminta serta cara pengguna mencabutnya di Pengaturan Android.
7. **Hak Penghapusan & Penyimpanan:** Cara menghapus riwayat, cache temp, model terunduh, token gateway, dan kunci Key Vault langsung dari aplikasi.
