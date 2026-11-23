---
name: harimu-android-dev
description: Skill untuk agent yang membangun aplikasi Android multiguna "HariMu" (Kotlin + Jetpack Compose) berisi ±160 tool video, audio, gambar, PDF, konverter, AI, perekam, dan utilitas. Gunakan setiap kali tugas menyentuh arsitektur, tool baru, engine media, AI gateway, penyimpanan, UI, pengujian, atau rilis HariMu.
---

# SKILL: HariMu — Aplikasi Android Multiguna

## 1. Tujuan produk
HariMu adalah "pisau lipat digital" untuk kebutuhan sehari-hari: edit video, audio, gambar, PDF, konversi file, perekam, dan utilitas keamanan, dalam satu aplikasi Android. Semua tool dikelompokkan menjadi 6 grup:

1. **Core** — Pro Video Editor, Prompt Video Gen, TTS, Analyze Images, Screen Recorder, Voice Recorder, Key Vault, Workspace Backup (.omni), Settings & Cloud Connections
2. **Video Forge** (30 tool)
3. **Audio Forge & Studio** (28 tool)
4. **Image Forge & Matrix** (47 tool)
5. **PDF Vault** (21 tool)
6. **Universal Converter** (28 tool)

Daftar lengkap + engine + fase ada di `02-KATALOG-FITUR.md`. Arsitektur ada di `01-ARSITEKTUR.md`. Urutan kerja ada di `03-PROMPT-BERTAHAP.md`.

## 2. Aturan wajib agent (urutan prioritas)

1. **Audit dulu, klaim kemudian.** Sebelum menyatakan sesuatu tentang kode yang sudah ada (bug, fitur sudah jadi, dependency ada), buka dan baca file aslinya. Jangan menebak dari nama file atau ingatan.
2. **Perbaikan harus tertarget.** Setiap temuan/perbaikan ditulis dengan jalur file lengkap (contoh: `feature/video/src/main/kotlin/.../TrimTool.kt`) dan baris/fungsi yang diubah. Jangan menulis ulang file di luar lingkup tugas.
3. **Jangan ubah format file** (nama, ekstensi, struktur proyek, format data) tanpa instruksi eksplisit dari Andikaa.
4. **Satu fase = satu lingkup.** Jangan loncat ke fase berikutnya. Selesaikan, jalankan build + test, lalu laporkan.
5. **Build harus hijau** di akhir setiap fase: `./gradlew assembleDebug testDebugUnitTest lintDebug`.
6. **Jangan pernah menaruh API key di APK.** Semua panggilan AI cloud lewat Cloudflare Worker (AI Gateway). Backend memakai Cloudflare, bukan Firebase.
7. **Jujur soal batas.** Jika sebuah fitur tidak realistis on-device (mis. PDF→Word berkualitas tinggi, konversi font OTF↔TTF, Stem Splitter besar), tulis keputusan yang dipilih (cloud / model unduhan / versi terbatas) di `docs/DECISIONS.md`, jangan pura-pura selesai.
8. **Cek lisensi** sebelum menambah library (FFmpeg build GPL, iText AGPL, dll.). Catat di `docs/LICENSES.md`.
9. **Verifikasi versi dependency** di sumber resmi saat menambahkan; jangan mengandalkan angka versi dari ingatan.

## 3. Stack

| Area | Pilihan |
|---|---|
| Bahasa/UI | Kotlin, Jetpack Compose, Material 3 (tema terang/gelap/AMOLED) |
| DI | Hilt |
| Navigasi | Navigation Compose (type-safe routes) |
| Async | Coroutines + Flow, WorkManager untuk job panjang |
| Data | Room (riwayat, favorit), DataStore (pengaturan), file di app-specific storage + MediaStore/SAF untuk ekspor |
| Media | Media3 (Transformer, ExoPlayer), FFmpeg (wrapper terpelihara; cek status ffmpeg-kit karena repo aslinya sudah diarsipkan), MediaCodec |
| Gambar | Coil, Bitmap/Canvas, ExifInterface, GPUImage/OpenGL ES atau RenderEffect (API 31+) |
| PDF | `PdfRenderer` (render), PdfBox-Android atau library berlisensi permisif (manipulasi) |
| OCR | ML Kit Text Recognition dan/atau Tesseract (butuh 8 bahasa) |
| ML on-device | LiteRT/TFLite atau ONNX Runtime Mobile; model besar diunduh saat dibutuhkan |
| Kripto | Android Keystore, Tink/BouncyCastle |
| Arsip | Apache Commons Compress, zip4j (AES-256), junrar |
| Backend | Cloudflare Workers (+ R2, KV, D1 bila perlu) |
| Min SDK | 26 (turunkan/naikkan hanya dengan alasan tertulis) |
| Target SDK | Ikuti syarat Google Play terbaru (verifikasi saat rilis) |

## 4. Struktur modul

```
harimu/
├── app/                      # entry point, nav host, Hilt root
├── core/
│   ├── design/               # tema, komponen Compose bersama
│   ├── tool-api/             # interface Tool, ToolRegistry, ToolParam, ToolResult
│   ├── engine-media/         # wrapper FFmpeg + Media3, job runner
│   ├── engine-image/         # operasi Bitmap, filter, EXIF
│   ├── engine-pdf/
│   ├── engine-ml/            # loader model, delegate GPU/NNAPI
│   ├── files/                # SAF, MediaStore, temp manager, hasil
│   ├── data/                 # Room, DataStore
│   ├── cloud/                # klien S3/R2, Drive, Dropbox, REST kustom
│   └── ai-client/            # klien ke Cloudflare AI Gateway
├── feature/
│   ├── home/ settings/ history/
│   ├── core-tools/ video/ audio/ image/ pdf/ converter/
└── backend/ai-gateway/       # Cloudflare Worker (TypeScript)
```

Aturan dependensi: `feature/*` boleh bergantung ke `core/*`, tidak boleh ke `feature/*` lain. Tool yang muncul di dua grup (mis. Video to GIF di Video Forge dan Universal Converter) memakai **satu implementasi** dan dua entri registry.

## 5. Kontrak Tool (wajib diikuti setiap tool)

```kotlin
interface Tool {
    val id: String                 // "VID-01"
    val group: ToolGroup           // CORE, VIDEO, AUDIO, IMAGE, PDF, CONVERTER
    val titleRes: Int
    val descriptionRes: Int
    val inputSpec: InputSpec       // jenis & jumlah file, mime yang diterima
    val params: List<ToolParam>    // slider, dropdown, toggle, teks
    val requiresNetwork: Boolean
    val requiresModel: ModelSpec?  // model ML yang harus diunduh
    suspend fun run(ctx: ToolContext, input: ToolInput): Flow<ToolProgress>
}
```

- `ToolProgress` = `Queued | Running(percent, message) | Done(outputs) | Failed(error)`.
- Setiap tool dapat dibatalkan (`CancellationException` dihormati, file sementara dibersihkan).
- Hasil selalu masuk ke riwayat (Room) dan bisa: dibagikan, disimpan ke galeri/folder SAF, diunggah ke cloud.
- Satu layar generik (`ToolScreen`) dirender dari `params`; layar khusus hanya untuk tool kompleks (editor video, Audio Studio Pro, Image Matrix, Screen/Voice Recorder).

## 6. Konvensi

- Semua string UI di `strings.xml` (Bahasa Indonesia default, Inggris sebagai terjemahan).
- Tidak ada operasi berat di main thread. Tool panjang (>3 detik) jalan sebagai WorkManager job dengan notifikasi progres dan tombol batal.
- Izin diminta saat dipakai (just-in-time), dengan penjelasan singkat. Pakai Photo Picker/SAF; hindari izin penyimpanan luas.
- Foreground service wajib memakai tipe yang benar (`mediaProjection` untuk Screen Recorder, `microphone` untuk Voice Recorder, `dataSync`/`mediaProcessing` untuk job panjang).
- Penanganan error: pesan ramah untuk pengguna + detail teknis di log/riwayat. Jangan menelan exception.
- Penamaan file hasil: `HariMu_<tool>_<yyyyMMdd_HHmmss>.<ext>`.
- Ruang disk: cek ruang kosong sebelum job; bersihkan folder temp saat start dan setelah job.

## 7. Definition of Done per tool

- [ ] Terdaftar di `ToolRegistry`, muncul di grup yang benar dan di pencarian
- [ ] Parameter tervalidasi (rentang, file kosong, format tidak didukung)
- [ ] Progres, batal, dan pembersihan temp berfungsi
- [ ] Unit test untuk logika murni + minimal 1 instrumented test dengan file sampel kecil
- [ ] Diuji pada file besar (video 1080p ≥ 2 menit / gambar ≥ 12 MP) tanpa OOM
- [ ] Hasil bisa dibuka, dibagikan, dan tercatat di riwayat
- [ ] String ID/EN, ikon, dan deskripsi satu kalimat
- [ ] Baris katalog di `02-KATALOG-FITUR.md` diperbarui (status ✅)

## 8. Anti-pattern (jangan lakukan)

- Memuat seluruh video/gambar besar ke memori sebagai Bitmap/ByteArray.
- Menyisipkan API key atau secret di kode/resource/BuildConfig.
- Membuat tiga implementasi berbeda untuk fitur yang sama di grup berbeda.
- Mengklaim fitur AI "selesai" padahal hanya stub/mock.
- Menambah library besar tanpa mengukur dampaknya pada ukuran APK.
- Mengubah struktur proyek/format file tanpa diminta.

## 9. Format laporan agent di akhir tiap fase

```
## Laporan Fase N
- Selesai: <daftar tool/ID>
- File yang diubah/dibuat: <jalur lengkap per item>
- Build/test: <perintah + hasil>
- Keputusan teknis: <ringkas, rujuk docs/DECISIONS.md>
- Belum selesai / risiko: <jujur>
- Rekomendasi fase berikutnya
```

## 10. Cara kerja saat menerima tugas

1. Baca `SKILL.md` ini, lalu `01-ARSITEKTUR.md` dan baris terkait di `02-KATALOG-FITUR.md`.
2. Audit kode yang ada pada area tugas (baca file aslinya).
3. Rencanakan perubahan dalam daftar kecil bertuliskan jalur file.
4. Implementasi, build, test.
5. Laporkan dengan format di bagian 9.
