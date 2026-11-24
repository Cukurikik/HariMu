# 02 — Katalog Fitur HariMu

**Legenda engine:** `M3` Media3/MediaCodec · `FF` FFmpeg · `NAT` API Android native · `LIB` library pihak ketiga · `ML` model on-device (unduhan) · `CL` cloud via Cloudflare Worker
**Fase:** merujuk ke `03-PROMPT-BERTAHAP.md` (P0–P15)
**Status:** ⬜ belum · 🟨 sedang · ✅ selesai (agent wajib memperbarui kolom ini)
**Risiko:** 🟢 rendah · 🟡 sedang · 🔴 tinggi/perlu keputusan

---

## 1. Core Engines, AI, Perekam & Utilitas

| ID | Tool | Engine | Fase | Risiko | Status | Catatan |
|---|---|---|---|---|---|---|
| CORE-01 | Pro Video Editor | M3+FF | P5 | 🟡 | ✅ | Timeline: susun klip, trim, kecepatan, rotasi, gabung; simpan ke Room |
| CORE-02 | Prompt Video Gen | CL | P11 | 🟡 | ✅ (cloud, butuh gateway+kunci) | Job async, polling, biaya & kuota |
| CORE-03 | Generate Speech (TTS) | CL | P11 | 🟢 | ✅ (perangkat + 5 suara cloud) | 5 suara, output WAV |
| CORE-04 | Analyze Images | CL | P11 | 🟢 | ✅ (cloud, butuh gateway+kunci) | Gambar + pertanyaan → jawaban |
| CORE-05 | Screen Recorder | NAT | P14 | 🟡 | ✅ | MediaProjection + FGS `mediaProjection`; resolusi/fps/bitrate; MP4(H.264/AAC) default, WebM(VP8/Opus) API 29+; audio mic/internal(AudioPlaybackCapture)/keduanya; notifikasi jeda/stop + overlay opsional; persetujuan per sesi (Android 14+). Batas: tanpa multipart/fallback codec; audio internal tergantung izin aplikasi sumber |
| CORE-06 | Voice Recorder | NAT | P14 | 🟢 | ✅ | AudioRecord 44,1/48 kHz + FFT 40 pita live; jeda/lanjut; WAV streaming, AAC(M4A), Opus(WebM API 29+); FGS `microphone`; fokus audio otomatis menjeda. Batas: deteksi panggilan lewat fokus audio (bukan READ_PHONE_STATE) |
| CORE-07 | Key Vault | NAT | P14 | 🟡 | ✅ | AES 128/256 AndroidKeyStore non-ekspor; RSA 2048/3072/4096 & ECDSA P-256/384/521 dengan private key disegel kunci master Keystore; ekspor PEM digerbangi kredensial perangkat; enkripsi/dekripsi berkas AES-GCM; tanda tangan SHA256withRSA/ECDSA + verifikasi. Batas: gerbang di UI, bukan `setUserAuthenticationRequired` |
| CORE-08 | Workspace Backup (.omni) | NAT | P14 | 🟢 | ✅ | ZIP + `manifest.json` skema 2; setelan/favorit/riwayat (+ metadata kunci); enkripsi `OMNIENC2` AES-GCM, PBKDF2-HMAC-SHA256 310k; impor menerima `OMNIENC1` & ZIP v1 lalu migrasi. Batas: private key tidak diekspor; maks 500 riwayat |
| CORE-09 | Settings & Cloud Connections | LIB | P2 (UI) / P14 (cloud) | 🟡 | ✅ | S3/R2 SigV4 PUT (path-style), Drive OAuth PKCE scope `drive.file`, Dropbox OAuth PKCE, REST kustom; kredensial disegel SecretBox (AES-256-GCM Keystore); tes koneksi; antrean unggah persisten + retry 2s/8s/30s (3×); tombol “Unggah ke cloud” di ResultCard. Batas: tanpa multipart >5 GB, tanpa resumable Drive, token refresh manual-trigger |

## 2. Video Forge

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| VID-01 | Trim Video | M3 | P4 | 🟢 | ✅ |
| VID-02 | Merge Video | M3 | P4 | 🟡 | ✅ |
| VID-03 | Crop Video (1:1, 16:9, 9:16) | M3 | P4 | 🟢 | ✅ |
| VID-04 | Resize Video (1080/720/480/360p) | M3 | P4 | 🟢 | ✅ |
| VID-05 | Change Video Speed | M3/FF | P4 | 🟡 | ✅ |
| VID-06 | Video to GIF | FF | P4 | 🟡 | ✅ |
| VID-07 | Video Upscaler (1.5x–4x) | ML/FF | P11 | 🔴 | ✅ (FFmpeg lanczos+unsharp, maks 60s/1080p) |
| VID-08 | Video to Audio (MP3) | FF | P4 | 🟢 | ✅ |
| VID-09 | Rotate Video | M3 | P4 | 🟢 | ✅ |
| VID-10 | Flip Video | M3/FF | P4 | 🟢 | ✅ |
| VID-11 | Stabilize Video | FF (deshake LGPL) | P5 | 🔴 | ✅ |
| VID-12 | Loop Video | FF | P4 | 🟢 | ✅ |
| VID-13 | Change Video Volume | M3/FF | P4 | 🟢 | ✅ |
| VID-14 | Remove Logo (blur) | FF | P5 | 🟡 | ✅ |
| VID-15 | Add Text to Video | M3/FF | P5 | 🟡 | ✅ |
| VID-16 | Add Audio to Video | FF | P5 | 🟢 | ✅ |
| VID-17 | Add Image to Video | M3/FF | P5 | 🟡 | ✅ |
| VID-18 | Video Converter (MP4/WebM/AVI/MKV) | FF | P4 | 🟡 | ✅ |
| VID-19 | Reverse Video | FF | P4 | 🟡 | ✅ |
| VID-20 | Video Compressor | M3/FF | P4 | 🟢 | ✅ |
| VID-21 | Video Splitter | M3/FF | P4 | 🟢 | ✅ |
| VID-22 | Extract Frames | NAT | P4 | 🟢 | ✅ |
| VID-23 | Add Subtitles (SRT) | FF | P5 | 🟡 | ✅ |
| VID-24 | Remove Audio | M3/FF | P4 | 🟢 | ✅ |
| VID-25 | Blur Faces | ML | P5 | 🔴 | ✅ |
| VID-26 | Color Grading | M3 (efek) | P5 | 🟡 | ✅ |
| VID-27 | Video Transitions | M3/FF | P5 | 🟡 | ✅ |
| VID-28 | Green Screen (Chroma Key) | FF/GL | P5 | 🟡 | ✅ |
| VID-29 | Stop Motion | FF | P5 | 🟢 | ✅ |
| VID-30 | Time Lapse | FF | P5 | 🟢 | ✅ |

## 3. Audio Forge & Audio Studio

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| AUD-01 | Mastering Hub | FF | P7 | 🟡 | ✅ |
| AUD-02 | Pro Equalizer 10-band (live + ekspor) | NAT/FF | P7 | 🟡 | ✅ |
| AUD-03 | AI Noise Reduction | FF/ML | P7 | 🟡 | ✅ |
| AUD-04 | Vocal Remover | FF / ML | P7 | 🟡 | ✅ (FFmpeg + mode DSP lokal) |
| AUD-05 | 8D Audio Engine | FF | P7 | 🟡 | ✅ |
| AUD-06 | Change Audio Pitch | FF | P6 | 🟡 | ✅ |
| AUD-07 | Change Audio Speed | FF | P6 | 🟢 | ✅ |
| AUD-08 | Change Audio Volume | FF | P6 | 🟢 | ✅ |
| AUD-09 | Trim Audio | FF/M3 | P6 | 🟢 | ✅ |
| AUD-10 | Merge Audio (urut / mix) | FF | P6 | 🟢 | ✅ |
| AUD-11 | Audio Compressor (32k–320k) | FF | P6 | 🟢 | ✅ |
| AUD-12 | Loudness Normalizer (-16 LUFS) | FF (loudnorm 2-pass) | P6 | 🟢 | ✅ |
| AUD-13 | Audio Converter (MP3/WAV/OGG/FLAC) | FF | P6 | 🟢 | ✅ |
| AUD-14 | Audio Resampler (8–96 kHz) | FF | P6 | 🟢 | ✅ |
| AUD-15 | Audio Channel Manipulator | FF | P6 | 🟢 | ✅ |
| AUD-16 | Audio Silence Remover | FF | P6 | 🟢 | ✅ |
| AUD-17 | Reverse Audio | FF | P6 | 🟢 | ✅ |
| AUD-18 | Audio Metadata Editor (ID3) | LIB | P6 | 🟡 | ✅ |
| AUD-19 | Audio Watermark | FF | P7 | 🟢 | ✅ |
| AUD-20 | AI Stem Splitter (4 track) | ML/CL | P7 (kontrak) / P11 (inferensi) | 🔴 | ✅ (2 jalur mid/side DSP, bukan Demucs) |
| AUD-21 | Audio Studio Pro (DAW multitrack) | NAT+FF | P8 | 🔴 | ✅ |
| AUD-22 | Audio Enhancer | FF/ML | P7 | 🟡 | ✅ |
| AUD-23 | Audio Splitter | FF | P6 | 🟢 | ✅ |
| AUD-24 | Add Echo | FF | P7 | 🟢 | ✅ |
| AUD-25 | Add Reverb | FF | P7 | 🟡 | ✅ |
| AUD-26 | Audio Fade | FF | P6 | 🟢 | ✅ |
| AUD-27 | Speech to Text | ML/CL | P11 | 🟡 | ✅ (cloud Whisper-compatible, butuh gateway) |
| AUD-28 | Audio Visualizer (video gelombang) | FF/Canvas | P7 | 🟡 | ✅ |

## 4. Image Forge & Image Matrix

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| IMG-01 | Image Cropper Pro | NAT | P9 | 🟢 | ✅ |
| IMG-02 | Smart Image Resizer | NAT | P9 | 🟢 | ✅ |
| IMG-03 | Image Rotator | NAT | P9 | 🟢 | ✅ |
| IMG-04 | Image Flipper | NAT | P9 | 🟢 | ✅ |
| IMG-05 | Canvas Expander | NAT | P9 | 🟢 | ✅ |
| IMG-06 | Perspective Corrector | NAT (Matrix.setPolyToPoly) | P9 | 🟡 | ✅ |
| IMG-07 | Image Straightener | NAT | P9 | 🟢 | ✅ |
| IMG-08 | Smart Auto-Crop | ML | P9 | 🟡 | ✅ |
| IMG-09 | Image Alignment | NAT | P9 | 🟢 | ✅ |
| IMG-10 | Multi-layer Crop | NAT | P9 | 🟡 | ✅ |
| IMG-11 | AI Image Upscaler / Super Resolution | ML | P11 | 🔴 | ✅ (heuristik unsharp, bukan model) |
| IMG-12 | AI Background Remover | ML | P11 | 🟡 | ✅ (heuristik flood fill, bukan U²-Net) |
| IMG-13 | AI Object Remover | ML/CL | P11 | 🔴 | ✅ (cloud, butuh gateway+kunci) |
| IMG-14 | AI Photo Enhancer | ML/CL | P11 | 🟡 | ✅ (cloud, butuh gateway+kunci) |
| IMG-15 | AI Face Restorer | ML/CL | P11 | 🔴 | ✅ (cloud, butuh gateway+kunci) |
| IMG-16 | AI Style Transfer | ML/CL | P11 | 🟡 | ✅ (cloud, butuh gateway+kunci) |
| IMG-17 | AI Image Colorizer | ML/CL | P11 | 🟡 | ✅ (cloud, butuh gateway+kunci) |
| IMG-18 | AI Sky Replacement | ML/CL | P11 | 🔴 | ✅ (cloud, butuh gateway+kunci) |
| IMG-19 | AI Portrait Bokeh | ML | P11 | 🟡 | ✅ (bokeh radial, bukan depth map) |
| IMG-20 | AI Cartoonizer | ML/CL | P11 | 🟡 | ✅ (cloud, butuh gateway+kunci) |
| IMG-21 | AI Image Generator | CL | P11 | 🟢 | ✅ (cloud, butuh gateway+kunci) |
| IMG-22 | AI Image Denoiser | ML/LIB | P11 | 🟡 | ✅ (smart blur, bukan denoiser neural) |
| IMG-23 | Image Format Converter (JPG/PNG/HEIC/WebP/AVIF/SVG/RAW/TIFF/BMP/PSD/PDF) | NAT+LIB | P9 | 🔴 | ✅ |
| IMG-24 | Icon Generator | NAT | P9 | 🟢 | ✅ |
| IMG-25 | Base64 Encoder/Decoder | NAT | P9 | 🟢 | ✅ |
| IMG-26 | Smart Image Compressor | NAT | P9 | 🟢 | ✅ |
| IMG-27 | Web Image Optimizer | NAT+LIB | P9 | 🟡 | ✅ |
| IMG-28 | Color Palette Extractor (K-Means) | NAT | P9 | 🟢 | ✅ |
| IMG-29 | Image Metadata Stripper & EXIF Editor | NAT | P9 | 🟢 | ✅ |
| IMG-30 | Image File Size Analyzer | NAT | P9 | 🟢 | ✅ |
| IMG-31 | Watermark Inserter | NAT | P10 | 🟢 | ✅ |
| IMG-32 | Image Overlay / Blender | NAT | P10 | 🟢 | ✅ |
| IMG-33 | Advanced Filters (Grayscale/Sepia/Invert/Blur/Contrast) | GL/NAT | P10 | 🟡 | ✅ |
| IMG-34 | Border & Frame Generator | NAT | P10 | 🟢 | ✅ |
| IMG-35 | Text-to-Image Typography | NAT | P10 | 🟢 | ✅ |
| IMG-36 | Gradient Overlay Generator | NAT | P10 | 🟢 | ✅ |
| IMG-37 | Shadow & Glow Effects | NAT | P10 | 🟡 | ✅ |
| IMG-38 | Sticker / Emoji Overlay | NAT | P10 | 🟢 | ✅ |
| IMG-39 | LUT Color Filter Import (.cube/.png) | NAT/LUT | P10 | 🟡 | ✅ |
| IMG-40 | Vintage / Film Simulation | GL/NAT | P10 | 🟢 | ✅ |
| IMG-41 | Lens Flare Generator | NAT | P10 | 🟡 | ✅ |
| IMG-42 | Reflection / Mirror Effects | NAT | P10 | 🟢 | ✅ |
| IMG-43 | Batch Processing | NAT/BATCH | P10 | 🟡 | ✅ |
| IMG-44 | Sprite Sheet Generator | NAT | P10 | 🟢 | ✅ |
| IMG-45 | Animated GIF Maker | NAT/GIF | P10 | 🟡 | ✅ |
| IMG-46 | Video Frame Extractor | NAT | P10 | 🟢 | ✅ | (alias VID-22) |
| IMG-47 | Image Matrix AI (kanvas editor multifungsi) | NAT | P10 | 🔴 | ✅ (AI di P11) |

## 5. PDF Vault

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| PDF-01 | Merge PDF | LIB | P12 | 🟢 | ✅ (objek asli via PDFBox, kualitas teks utuh) |
| PDF-02 | Split PDF (.zip) | LIB | P12 | 🟢 | ✅ (ZIP per N halaman, objek asli) |
| PDF-03 | Rotate PDF | LIB | P12 | 🟢 | ✅ (atribut rotasi halaman, rentang opsional) |
| PDF-04 | Reorder Pages | LIB | P12 | 🟢 | ✅ (seret thumbnail di kartu pratinjau + kolom urutan) |
| PDF-05 | Extract Pages | LIB | P12 | 🟢 | ✅ (rentang halaman, objek asli) |
| PDF-06 | PDF to Word/Excel/PowerPoint | CL / LIB terbatas | P12 | 🔴 | ✅ Dasar (teks DOCX/XLSX; PPTX gambar/halaman; tata letak asli tidak dijaga; antarmuka siap cloud) |
| PDF-07 | Word to PDF | CL | P12 | 🔴 | ✅ Dasar (paragraf .docx → PDF; gambar/tabel bergaya/header tidak ikut) |
| PDF-08 | Image to PDF | LIB | P12 | 🟢 | ✅ (ukuran gambar, A4, Letter) |
| PDF-09 | Compress PDF | LIB | P12 | 🟡 | ✅ (rasterisasi + kualitas JPEG; teks menjadi gambar; ukuran sebelum/sesudah ditampilkan) |
| PDF-10 | Encrypt PDF (AES-256/128, RC4-128) | LIB | P12 | 🟡 | ✅ (AES-256/AES-128/RC4-128 via PDFBox; RC4-40 tidak ditawarkan) |
| PDF-11 | Decrypt PDF | LIB | P12 | 🟢 | ✅ (password pengguna/pemilik) |
| PDF-12 | Add Watermark | LIB | P12 | 🟢 | ✅ (teks & gambar; gambar dipilih di kolom kedua) |
| PDF-13 | Remove Watermark | LIB | P12 | 🔴 | ✅ Dasar (hanya anotasi/OCG terpisah; watermark menyatu ke konten TIDAK bisa dihapus) |
| PDF-14 | Redact Content (permanen) | LIB | P12 | 🔴 | ✅ (halaman diratakan + kotak hitam; verifikasi ekstraksi teks pada hasil) |
| PDF-15 | Scan PDF to Text (OCR) | ML/LIB | P12 | 🟡 | ✅ (OCR on-device 8 bahasa; traineddata diunduh saat pertama dipakai) |
| PDF-16 | Image to Searchable PDF (OCR 8 bahasa) | LIB | P12 | 🟡 | ✅ (lapisan teks tak terlihat; posisi baris perkiraan, bukan per kata) |
| PDF-17 | AI Document Recognition | CL/ML | P12 | 🔴 | ✅ (via AI Gateway; tanpa kredensial gagal dengan pesan jelas; hasil mock ditandai) |
| PDF-18 | Page Numbering | LIB | P12 | 🟢 | ✅ (pola {n}/{total}, 4 posisi) |
| PDF-19 | Metadata Editor | LIB | P12 | 🟢 | ✅ (judul/penulis/subjek/kata kunci) |
| PDF-20 | Document Comparison | LIB+NAT | P12 | 🟡 | ✅ (diff baris + gambar diff visual per halaman) |
| PDF-21 | Form Filler (AcroForm) | LIB | P12 | 🟡 | ✅ (AcroForm teks; XFA tidak didukung) |

## 6. Universal Converter

*Beberapa entri adalah alias dari tool grup lain (satu implementasi, dua pintu masuk).*

| ID | Tool | Engine | Fase | Alias / Catatan |
|---|---|---|---|---|
| CNV-01 | Universal Video Transcoder | FF | P13 | ✅ alias VID-18 (MP4/WebM/AVI/MKV) |
| CNV-02 | Video to Animated GIF | FF | P13 | ✅ alias VID-06 (palet 2-pass) |
| CNV-03 | Video to Audio Extractor (MP3/WAV/FLAC/AAC) | FF | P13 | ✅ perluasan VID-08 (4 format; WAV/FLAC hanya wadah lossless dari audio terdekode) |
| CNV-04 | Video Upscaler (AI) 2x | ML | P13 | ✅ alias VID-07 (FFmpeg lanczos + unsharp, bukan model neural) |
| CNV-05 | Universal Audio Transcoder | FF | P13 | ✅ alias AUD-13 (MP3/WAV/OGG/FLAC/M4A/OPUS) |
| CNV-06 | Hi-Res Lossless Converter | FF | P13 | ✅ FLAC/WAV 24-bit hingga 96 kHz (upsampling tidak menambah detail) |
| CNV-07 | Voice to Text Converter | ML/CL | P13 | ✅ alias AUD-27 (butuh AI Gateway; tanpa kredensial gagal jelas) |
| CNV-08 | 8D / Spatial Audio Generator | FF | P13 | ✅ alias AUD-05 |
| CNV-09 | Universal Image Converter (PNG/JPG/WebP/AVIF) | NAT | P13 | ✅ alias IMG-23 (AVIF/HEIC tergantung dukungan perangkat) |
| CNV-10 | SVG to Raster (PNG/JPG) | SVG | P13 | ✅ AndroidSVG 1.4; filter efek/teks font sistem/animasi tidak dirender; maks 4096 px |
| CNV-11 | Icon Asset Generator (+favicon) | NAT | P13 | ✅ alias IMG-24 |
| CNV-12 | Base64 Image Encoder | NAT | P13 | ✅ alias IMG-25 |
| CNV-13 | PDF to Word / Word to PDF | LIB | P13 | ✅ router ke PDF-06 (.pdf) / PDF-07 (.docx); konversi "Dasar" |
| CNV-14 | PDF to Image Matrix | NAT | P13 | ✅ PdfRenderer → ZIP PNG/JPG (skala 1–3x) |
| CNV-15 | OCR Document Scanner → .txt | ML/LIB | P13 | ✅ alias PDF-15 (OCR 8 bahasa, data bahasa diunduh saat dipakai) |
| CNV-16 | Universal Archive Extractor (.zip) | LIB | P13 | ✅ daftar isi + ekstrak; nama entri disanitasi (zip-slip), batas 128 MB/entri & 512 MB total, rasio 200x (zip bomb) |
| CNV-17 | Cross-Archive Converter (ZIP/RAR/7Z/TAR.GZ) | LIB | P13 | ✅ ZIP/TAR.GZ/7Z/RAR → ZIP; **RAR hanya dibaca**, tulis RAR tidak didukung |
| CNV-18 | AES-256 Encrypted Archive | LIB | P13 | ✅ ZIP AES-256 standar via zip4j (kompatibel 7-Zip/WinRAR); kata sandi tidak disimpan; .hzm lama masih bisa dibuka |
| CNV-19 | EPUB Converter | LIB | P13 | ✅ EPUB → TXT/HTML/PDF berdasarkan spine; teks saja, gambar/CSS/DRM tidak didukung |
| CNV-20 | Comic Book Converter (CBZ/CBR → PDF/EPUB) | LIB | P13 | ✅ CBZ (ZIP) & CBR (RAR baca-saja); urutan halaman natural; EPUB keluaran minimal |
| CNV-21 | Universal Font Converter (TTF/OTF/WOFF/WOFF2) | LIB | P13 | ✅ TTF/OTF ⇄ WOFF 1.0. **WOFF2 ditolak** (butuh Brotli + transformasi glyf). OTF(CFF) ⇄ TTF ditolak (konversi outline tidak tersedia) |
| CNV-22 | Web-Font Optimizer (subsetting) | LIB | P13 | ✅ subset TrueType per karakter + ekspor WOFF; tabel hinting/tata letak dibuang; font CFF & WOFF2 ditolak |
| CNV-23 | JSON ⇄ XML | NAT | P13 | ✅ dua arah; nama elemen disanitasi, array/null didukung |
| CNV-24 | CSV ⇄ TSV | NAT | P13 | ✅ RFC-4180 (kutip ganda, `""`, baris baru dalam sel, CRLF) |
| CNV-25 | Code Formatter (JSON/kode) | NAT | P13 | ✅ pretty/minify JSON + pretty CSS |
| CNV-26 | Markdown to HTML | NAT | P13 | ✅ heading, list, blockquote, hr, kode berpagar, tautan; URL `javascript:`/`data:` ditolak |
| CNV-27 | GeoJSON to KML | NAT | P13 | ✅ Point/LineString/Polygon (termasuk lubang), Multi*, GeometryCollection |
| CNV-28 | STL to OBJ | NAT | P13 | ✅ ASCII + binary (deteksi ukuran `84+50n`), normal diabaikan |

---

## Ringkasan beban

| Grup | Jumlah tool | Fase |
|---|---|---|
| Core | 9 | P2, P5, P11, P14 |
| Video Forge | 30 | P4, P5, P11 |
| Audio Forge/Studio | 28 | P6, P7, P8, P11 |
| Image Forge/Matrix | 47 | P9, P10, P11 |
| PDF Vault | 21 | P12 |
| Universal Converter | 28 | P13 |
| **Total entri** | **163** (±145 implementasi unik setelah alias digabung) | |

## Item yang butuh keputusan Andikaa sebelum dikerjakan

1. **PDF↔Word/Excel/PPT, Word→PDF** — pakai layanan cloud/Container (LibreOffice) atau versi on-device terbatas?
2. **Provider AI** untuk TTS, video generation, analisis gambar, dan gambar generatif (kunci disimpan di Worker).
3. **FFmpeg** — wrapper mana yang dipakai dan apakah menerima lisensi GPL (atau tetap LGPL + MediaCodec)?
4. **Stem Splitter & Upscaler video** — unduh model besar di perangkat, atau jalankan via cloud?
5. **Font converter** — cukup WOFF/WOFF2 saja, atau wajib OTF↔TTF?
6. **Screen Recorder** — default MP4 atau WebM?
