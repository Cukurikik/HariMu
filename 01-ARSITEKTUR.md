# 01 — Arsitektur HariMu

## 1. Prinsip

1. **Registry-driven.** Semua ±160 tool adalah entri di `ToolRegistry`. Beranda, pencarian, favorit, dan riwayat dibangun dari registry, bukan di-hardcode.
2. **Engine dipisah dari tool.** Tool hanya merangkai parameter; kerja berat ada di paket engine `core/media`, `core/engines`, `core/pdf`, dan `core/ai` di dalam satu modul `:app`.
3. **On-device dulu, cloud bila perlu.** Tiga tingkat: `LOCAL` (tanpa internet), `MODEL` (butuh model ML yang diunduh sekali), `CLOUD` (lewat Cloudflare Worker).
4. **Streaming, bukan memuat penuh.** Proses file lewat URI/stream/tile; jangan memuat media besar ke RAM.
5. **Job-based.** Pekerjaan panjang berjalan lewat WorkManager/foreground service, tahan terhadap rotasi layar dan proses ditutup.

## 2. Diagram alur

```
UI (Compose) ──► ToolScreen(params) ──► JobManager ──► WorkManager/FGS
                                            │
                    ┌───────────────────────┼───────────────────────┐
                    ▼                       ▼                       ▼
            core/media                core/engines, core/pdf     core/ai
          (FFmpeg + Media3)        (Bitmap, PdfRenderer,    (Cloudflare Worker)
                                    ML Kit, ONNX/LiteRT)           │
                                                                    ▼
                                                         Gemini/Veo/dll (key di Worker)
Hasil ──► files/ (temp → output) ──► Room (riwayat) ──► Share / SAF / Cloud upload
```

## 3. Engine media

**Dua jalur, pilih per tool:**

| Jalur | Dipakai untuk | Catatan |
|---|---|---|
| Media3 Transformer + MediaCodec | trim, merge, crop, resize, rotate, speed, text/gambar overlay, transisi sederhana | Hardware-accelerated, cepat, lisensi bersih |
| FFmpeg (`ffmpeg-kit-full:8.1.9` LGPL) | konversi format, GIF, reverse, loop, `deshake`, chromakey, filter audio (equalizer, loudnorm, afftdn, atempo, aecho), subtitle burn-in, concat | LGPL tanpa `libx264`/`libx265`/`vidstab`; ukuran APK besar, split per ABI |

Catatan penting:
- Repo `ffmpeg-kit` asli sudah diarsipkan. Pilih fork/wrapper yang aktif dipelihara, **verifikasi sendiri** statusnya, atau bangun FFmpeg sendiri dengan flag LGPL. Dokumentasikan pilihan di `docs/DECISIONS.md`.
- Encoder x264/x265 berlisensi GPL dan **tidak tersedia** di build ini: H.264 dikerjakan Media3/MediaCodec (mis. `VID-07`), sedangkan jalur FFmpeg untuk penajaman memakai encoder `mpeg4` dan dijaga guard `core/media/FfmpegCapabilities.kt`.
- Gunakan App Bundle + split ABI; pertimbangkan Play Feature Delivery untuk paket FFmpeg dan model ML.

`MediaJob` menyediakan: parsing progres (durasi total vs waktu terproses), batal, log, validasi codec, dan fallback otomatis (Media3 gagal → FFmpeg).

## 4. Engine gambar

- Dekode dengan `ImageDecoder`/Coil dengan `inSampleSize`/tile untuk gambar besar.
- Operasi geometri: `Matrix` + `Canvas`.
- Filter real-time: `RenderEffect`/AGSL (API 33+) atau GPUImage/OpenGL ES untuk API di bawahnya. Ekspor menerapkan filter yang sama pada resolusi penuh.
- Format: JPG/PNG/WebP/BMP native; AVIF/HEIC via `ImageDecoder` (tergantung API) atau library; **RAW/PSD/TIFF/SVG** perlu library pihak ketiga (cek lisensi), sebagian ditandai "terbatas".
- EXIF: `ExifInterface` untuk baca/tulis/hapus.

## 5. Engine PDF

- Render halaman: `PdfRenderer` (preview, PDF→gambar, Document Comparison visual).
- Manipulasi (merge, split, rotate, reorder, extract, watermark, page number, metadata, enkripsi, form): library PDF permisif yang berjalan di Android. Hindari library AGPL kecuali proyek memang open source AGPL.
- OCR: ML Kit (cepat, terbatas bahasa) dan/atau Tesseract (8 bahasa sesuai spesifikasi, traineddata diunduh).
- **PDF↔Word/Excel/PowerPoint**: konversi berkualitas tinggi tidak realistis murni on-device. Opsi: (a) layanan cloud/Container untuk LibreOffice, (b) versi on-device terbatas (teks + gambar saja). Cloudflare Workers biasa tidak bisa menjalankan LibreOffice; pilih dan catat keputusan.

## 6. Engine ML

| Kebutuhan | Pendekatan |
|---|---|
| Hapus latar, auto-crop objek | ML Kit Subject Segmentation atau model segmentasi (U²-Net/RMBG) via LiteRT/ONNX |
| Blur wajah | ML Kit Face Detection per frame (sampling + tracking) |
| Upscaler gambar/video | Model super-resolution kecil (Real-ESRGAN varian ringan) via ONNX/LiteRT; video diproses per frame dan sangat lambat → batasi resolusi/durasi |
| Noise reduction audio | FFmpeg `afftdn`/`arnndn` (ringan) atau model RNNoise |
| Vocal remover | Mode cepat: kanselasi kanal tengah via FFmpeg `pan`; mode AI: model Demucs/Spleeter dikonversi ke ONNX (berat) |
| AI Stem Splitter | Model Demucs ONNX (unduhan besar) atau cloud |
| Speech to Text | Whisper (whisper.cpp via JNI, model tiny/base) atau cloud |
| Style transfer, cartoonizer, colorizer, face restorer, object remover, sky replacement, denoiser | Model on-device kecil bila ada; selebihnya cloud |

Manajer model: unduh sekali, cek checksum, simpan di `noBackupFilesDir`, tampilkan ukuran sebelum mengunduh, bisa dihapus dari Pengaturan.

## 7. AI Gateway (Cloudflare Worker)

```
POST /v1/text-to-speech      { text, voice: 1..5 }          → audio WAV
POST /v1/video-generate      { prompt, ... }                → jobId  (+ GET /v1/jobs/:id)
POST /v1/image-analyze       { image (multipart), question }→ teks
POST /v1/image-generate      { prompt, size }               → gambar
POST /v1/image-edit/:op      { image, params }              → gambar (colorize, restore, dll.)
POST /v1/speech-to-text      { audio }                      → transkrip
GET  /v1/health
```

Ketentuan:
- Secret provider AI disimpan sebagai Worker secret, tidak pernah di klien.
- Autentikasi klien: token perangkat/instal yang ditandatangani + rate limit per perangkat (Workers rate limiting / KV / Durable Object).
- Batas ukuran upload, timeout, dan error terstruktur `{code, message, retryable}`.
- Job panjang (video gen) memakai pola asynchronous: kirim → polling status → unduh hasil dari R2 (presigned URL berumur pendek).
- Jangan menyimpan konten pengguna lebih lama dari yang perlu; tulis kebijakan privasi sesuai.

## 8. Perekam

| Fitur | Implementasi |
|---|---|
| Screen Recorder | `MediaProjection` + `MediaRecorder`/`MediaCodec`, foreground service tipe `mediaProjection` (Android 14+ butuh izin sesi per rekaman). Spesifikasi menyebut `.webm`; `MediaRecorder.OutputFormat.WEBM` tersedia, tetapi default MP4 lebih kompatibel. Beri opsi. Audio internal hanya via `AudioPlaybackCapture` (API 29+, hanya app yang mengizinkan). "Jendela/tab browser" di web tidak ada padanannya di Android: ganti dengan "layar penuh" dan catat perbedaan ini. |
| Voice Recorder | `AudioRecord` untuk PCM + FFT (visualisasi spektrum) + encode via `MediaCodec` (AAC/Opus) atau `MediaRecorder` WEBM. Foreground service tipe `microphone`. |

## 9. Key Vault

- Pembuatan kunci AES / RSA / ECDSA lewat JCA + Android Keystore (kunci non-ekspor) atau kunci ekspor (PEM) untuk kebutuhan pengguna.
- Kunci yang disimpan dilindungi Keystore + autentikasi biometrik.
- Ekspor kunci privat hanya setelah autentikasi ulang dan dengan peringatan jelas.

## 10. Workspace Backup (.omni) dan Cloud

- `.omni` = ZIP dengan `manifest.json` (versi skema, tanggal, versi app), `settings.json`, `favorites.json`, `history.json`. Opsional enkripsi AES-GCM dengan kata sandi.
- Impor harus memvalidasi versi skema dan menawarkan migrasi.
- Cloud Connections: S3/R2 (SigV4), Google Drive (OAuth + scope `drive.file`), Dropbox (OAuth PKCE), REST kustom (URL + header + metode). Kredensial disimpan terenkripsi (Keystore/EncryptedFile).

## 11. Keamanan & privasi

- Semua proses file default **lokal**; tool CLOUD diberi lencana dan konfirmasi sebelum unggah pertama.
- Tidak ada analitik pihak ketiga tanpa persetujuan.
- Redact PDF harus benar-benar menghapus konten dasar (bukan sekadar kotak hitam); uji dengan ekstraksi teks.
- Encrypt/Decrypt PDF dan Archive: kata sandi tidak pernah disimpan atau dilog.
- Persiapkan formulir Data Safety Google Play dari daftar izin dan tool CLOUD.

## 12. Performa

- Batas memori per tool, `largeHeap` hanya bila terukur perlu.
- Antrean job: maksimal 1–2 job berat paralel; sisanya antre.
- Cache thumbnail; hapus cache otomatis saat ruang rendah.
- Baseline Profile + R8 full mode; ukur startup dan jank.

## 13. Pengujian

- Unit test: logika parameter, perhitungan (rasio crop, bitrate, sample rate), parser (SRT, ID3, KML, OBJ/STL).
- Instrumented test: set file sampel kecil di `androidTest/assets`.
- Golden test untuk filter gambar.
- Uji perangkat: minimal satu perangkat RAM 3–4 GB, satu API 26–28, satu API terbaru.
- Uji ketahanan: batal di tengah job, proses dimatikan, ruang disk penuh, file korup.
- **Status 25 Nov 2026**: tujuh berkas instrumented test sudah ada di `app/src/androidTest/java/com/harimu/app/` (media, audio, gambar, PDF, converter, keystore) tetapi **belum dijalankan**; eksekusinya hanya lewat job CI `instrumented` (`continue-on-error`). Unit test belum pernah dieksekusi sama sekali.
- **Batasan lingkungan**: pemilik tidak melakukan build lokal; verifikasi nyata hanya lewat CI `.github/workflows/android-ci.yml`.
