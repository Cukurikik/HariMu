# 02 — Katalog Fitur HariMu

**Legenda engine:** `M3` Media3/MediaCodec · `FF` FFmpeg · `NAT` API Android native · `LIB` library pihak ketiga · `ML` model on-device (unduhan) · `CL` cloud via Cloudflare Worker
**Fase:** merujuk ke `03-PROMPT-BERTAHAP.md` (P0–P15)
**Status:** ⬜ belum · 🟨 sedang · ✅ selesai (agent wajib memperbarui kolom ini)
**Risiko:** 🟢 rendah · 🟡 sedang · 🔴 tinggi/perlu keputusan

---

## 1. Core Engines, AI, Perekam & Utilitas

| ID | Tool | Engine | Fase | Risiko | Status | Catatan |
|---|---|---|---|---|---|---|
| CORE-01 | Pro Video Editor | M3+FF | P5 | 🟡 | ⬜ | Timeline sederhana: trim, kecepatan, rotasi, gabung; gunakan ulang VID-01/02/05/09 |
| CORE-02 | Prompt Video Gen | CL | P11 | 🟡 | ⬜ | Job async, polling, biaya & kuota |
| CORE-03 | Generate Speech (TTS) | CL | P11 | 🟢 | ⬜ | 5 suara, output WAV |
| CORE-04 | Analyze Images | CL | P11 | 🟢 | ⬜ | Gambar + pertanyaan → jawaban |
| CORE-05 | Screen Recorder | NAT | P14 | 🟡 | ⬜ | MediaProjection; "jendela/tab" tidak berlaku di Android |
| CORE-06 | Voice Recorder | NAT | P14 | 🟢 | ⬜ | Spektrum audio live |
| CORE-07 | Key Vault | NAT/LIB | P14 | 🟡 | ⬜ | AES/RSA/ECDSA + Keystore |
| CORE-08 | Workspace Backup (.omni) | NAT | P14 | 🟢 | ⬜ | ZIP + manifest, opsi enkripsi |
| CORE-09 | Settings & Cloud Connections | LIB | P2 (UI) / P14 (cloud) | 🟡 | ⬜ | S3/R2, Drive, Dropbox, REST kustom |

## 2. Video Forge

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| VID-01 | Trim Video | M3 | P4 | 🟢 | ⬜ |
| VID-02 | Merge Video | M3 | P4 | 🟡 | ⬜ |
| VID-03 | Crop Video (1:1, 16:9, 9:16) | M3 | P4 | 🟢 | ⬜ |
| VID-04 | Resize Video (1080/720/480/360p) | M3 | P4 | 🟢 | ⬜ |
| VID-05 | Change Video Speed | M3/FF | P4 | 🟡 | ⬜ |
| VID-06 | Video to GIF | FF | P4 | 🟡 | ⬜ |
| VID-07 | Video Upscaler (1.5x–4x) | ML/FF | P11 | 🔴 | ⬜ |
| VID-08 | Video to Audio (MP3) | FF | P4 | 🟢 | ⬜ |
| VID-09 | Rotate Video | M3 | P4 | 🟢 | ⬜ |
| VID-10 | Flip Video | M3/FF | P4 | 🟢 | ⬜ |
| VID-11 | Stabilize Video | FF (vidstab) | P5 | 🔴 | ⬜ |
| VID-12 | Loop Video | FF | P4 | 🟢 | ⬜ |
| VID-13 | Change Video Volume | M3/FF | P4 | 🟢 | ⬜ |
| VID-14 | Remove Logo (blur/delogo) | FF | P5 | 🟡 | ⬜ |
| VID-15 | Add Text to Video | M3 | P5 | 🟡 | ⬜ |
| VID-16 | Add Audio to Video | FF/M3 | P5 | 🟢 | ⬜ |
| VID-17 | Add Image to Video | M3 | P5 | 🟡 | ⬜ |
| VID-18 | Video Converter (MP4/WebM/AVI/MKV) | FF | P4 | 🟡 | ⬜ |
| VID-19 | Reverse Video | FF | P4 | 🟡 | ⬜ |
| VID-20 | Video Compressor | M3/FF | P4 | 🟢 | ⬜ |
| VID-21 | Video Splitter | M3/FF | P4 | 🟢 | ⬜ |
| VID-22 | Extract Frames | NAT | P4 | 🟢 | ⬜ |
| VID-23 | Add Subtitles (SRT) | FF | P5 | 🟡 | ⬜ |
| VID-24 | Remove Audio | M3/FF | P4 | 🟢 | ⬜ |
| VID-25 | Blur Faces | ML | P5 | 🔴 | ⬜ |
| VID-26 | Color Grading | M3 (efek) | P5 | 🟡 | ⬜ |
| VID-27 | Video Transitions | M3/FF | P5 | 🟡 | ⬜ |
| VID-28 | Green Screen (Chroma Key) | FF/GL | P5 | 🟡 | ⬜ |
| VID-29 | Stop Motion | FF | P5 | 🟢 | ⬜ |
| VID-30 | Time Lapse | FF | P5 | 🟢 | ⬜ |

## 3. Audio Forge & Audio Studio

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| AUD-01 | Mastering Hub | FF | P7 | 🟡 | ⬜ |
| AUD-02 | Pro Equalizer 10-band (live + ekspor) | NAT/FF | P7 | 🟡 | ⬜ |
| AUD-03 | AI Noise Reduction | FF/ML | P7 | 🟡 | ⬜ |
| AUD-04 | Vocal Remover | FF / ML | P7 | 🟡 | ⬜ |
| AUD-05 | 8D Audio Engine | FF | P7 | 🟡 | ⬜ |
| AUD-06 | Change Audio Pitch | FF | P6 | 🟡 | ⬜ |
| AUD-07 | Change Audio Speed | FF | P6 | 🟢 | ⬜ |
| AUD-08 | Change Audio Volume | FF | P6 | 🟢 | ⬜ |
| AUD-09 | Trim Audio | FF/M3 | P6 | 🟢 | ⬜ |
| AUD-10 | Merge Audio (urut / mix) | FF | P6 | 🟢 | ⬜ |
| AUD-11 | Audio Compressor (32k–320k) | FF | P6 | 🟢 | ⬜ |
| AUD-12 | Loudness Normalizer (-16 LUFS) | FF (loudnorm 2-pass) | P6 | 🟢 | ⬜ |
| AUD-13 | Audio Converter (MP3/WAV/OGG/FLAC) | FF | P6 | 🟢 | ⬜ |
| AUD-14 | Audio Resampler (8–96 kHz) | FF | P6 | 🟢 | ⬜ |
| AUD-15 | Audio Channel Manipulator | FF | P6 | 🟢 | ⬜ |
| AUD-16 | Audio Silence Remover | FF | P6 | 🟢 | ⬜ |
| AUD-17 | Reverse Audio | FF | P6 | 🟢 | ⬜ |
| AUD-18 | Audio Metadata Editor (ID3) | LIB | P6 | 🟡 | ⬜ |
| AUD-19 | Audio Watermark | FF | P7 | 🟢 | ⬜ |
| AUD-20 | AI Stem Splitter (4 track) | ML/CL | P7 | 🔴 | ⬜ |
| AUD-21 | Audio Studio Pro (DAW multitrack) | NAT+FF | P8 | 🔴 | ⬜ |
| AUD-22 | Audio Enhancer | FF/ML | P7 | 🟡 | ⬜ |
| AUD-23 | Audio Splitter | FF | P6 | 🟢 | ⬜ |
| AUD-24 | Add Echo | FF | P7 | 🟢 | ⬜ |
| AUD-25 | Add Reverb | FF | P7 | 🟡 | ⬜ |
| AUD-26 | Audio Fade | FF | P6 | 🟢 | ⬜ |
| AUD-27 | Speech to Text | ML/CL | P11 | 🟡 | ⬜ |
| AUD-28 | Audio Visualizer (video gelombang) | FF/Canvas | P7 | 🟡 | ⬜ |

## 4. Image Forge & Image Matrix

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| IMG-01 | Image Cropper Pro | NAT | P9 | 🟢 | ⬜ |
| IMG-02 | Smart Image Resizer | NAT | P9 | 🟢 | ⬜ |
| IMG-03 | Image Rotator | NAT | P9 | 🟢 | ⬜ |
| IMG-04 | Image Flipper | NAT | P9 | 🟢 | ⬜ |
| IMG-05 | Canvas Expander | NAT | P9 | 🟢 | ⬜ |
| IMG-06 | Perspective Corrector | NAT (Matrix.setPolyToPoly) | P9 | 🟡 | ⬜ |
| IMG-07 | Image Straightener | NAT | P9 | 🟢 | ⬜ |
| IMG-08 | Smart Auto-Crop | ML | P9 | 🟡 | ⬜ |
| IMG-09 | Image Alignment | NAT | P9 | 🟢 | ⬜ |
| IMG-10 | Multi-layer Crop | NAT | P9 | 🟡 | ⬜ |
| IMG-11 | AI Image Upscaler / Super Resolution | ML | P11 | 🔴 | ⬜ |
| IMG-12 | AI Background Remover | ML | P11 | 🟡 | ⬜ |
| IMG-13 | AI Object Remover | ML/CL | P11 | 🔴 | ⬜ |
| IMG-14 | AI Photo Enhancer | ML/CL | P11 | 🟡 | ⬜ |
| IMG-15 | AI Face Restorer | ML/CL | P11 | 🔴 | ⬜ |
| IMG-16 | AI Style Transfer | ML/CL | P11 | 🟡 | ⬜ |
| IMG-17 | AI Image Colorizer | ML/CL | P11 | 🟡 | ⬜ |
| IMG-18 | AI Sky Replacement | ML/CL | P11 | 🔴 | ⬜ |
| IMG-19 | AI Portrait Bokeh | ML | P11 | 🟡 | ⬜ |
| IMG-20 | AI Cartoonizer | ML/CL | P11 | 🟡 | ⬜ |
| IMG-21 | AI Image Generator | CL | P11 | 🟢 | ⬜ |
| IMG-22 | AI Image Denoiser | ML/LIB | P11 | 🟡 | ⬜ |
| IMG-23 | Image Format Converter (JPG/PNG/HEIC/WebP/AVIF/SVG/RAW/TIFF/BMP/PSD/PDF) | NAT+LIB | P9 | 🔴 | ⬜ |
| IMG-24 | Icon Generator | NAT | P9 | 🟢 | ⬜ |
| IMG-25 | Base64 Encoder/Decoder | NAT | P9 | 🟢 | ⬜ |
| IMG-26 | Smart Image Compressor | NAT | P9 | 🟢 | ⬜ |
| IMG-27 | Web Image Optimizer | NAT+LIB | P9 | 🟡 | ⬜ |
| IMG-28 | Color Palette Extractor (K-Means) | NAT | P9 | 🟢 | ⬜ |
| IMG-29 | Image Metadata Stripper & EXIF Editor | NAT | P9 | 🟢 | ⬜ |
| IMG-30 | Image File Size Analyzer | NAT | P9 | 🟢 | ⬜ |
| IMG-31 | Watermark Inserter | NAT | P10 | 🟢 | ⬜ |
| IMG-32 | Image Overlay / Blender | NAT | P10 | 🟢 | ⬜ |
| IMG-33 | Advanced Filters (Grayscale/Sepia/Invert/Blur/Contrast) | GL/NAT | P10 | 🟡 | ⬜ |
| IMG-34 | Border & Frame Generator | NAT | P10 | 🟢 | ⬜ |
| IMG-35 | Text-to-Image Typography | NAT | P10 | 🟢 | ⬜ |
| IMG-36 | Gradient Overlay Generator | NAT | P10 | 🟢 | ⬜ |
| IMG-37 | Shadow & Glow Effects | NAT | P10 | 🟡 | ⬜ |
| IMG-38 | Sticker / Emoji Overlay | NAT | P10 | 🟢 | ⬜ |
| IMG-39 | LUT Color Filter Import (.cube/.png) | GL | P10 | 🟡 | ⬜ |
| IMG-40 | Vintage / Film Simulation | GL/NAT | P10 | 🟢 | ⬜ |
| IMG-41 | Lens Flare Generator | NAT | P10 | 🟡 | ⬜ |
| IMG-42 | Reflection / Mirror Effects | NAT | P10 | 🟢 | ⬜ |
| IMG-43 | Batch Processing | Job | P10 | 🟡 | ⬜ |
| IMG-44 | Sprite Sheet Generator | NAT | P10 | 🟢 | ⬜ |
| IMG-45 | Animated GIF Maker | FF/LIB | P10 | 🟡 | ⬜ |
| IMG-46 | Video Frame Extractor | NAT | P10 | 🟢 | ⬜ | (alias VID-22) |
| IMG-47 | Image Matrix AI (kanvas editor multifungsi) | NAT | P10 | 🔴 | ⬜ |

## 5. PDF Vault

| ID | Tool | Engine | Fase | Risiko | Status |
|---|---|---|---|---|---|
| PDF-01 | Merge PDF | LIB | P12 | 🟢 | ⬜ |
| PDF-02 | Split PDF (.zip) | LIB | P12 | 🟢 | ⬜ |
| PDF-03 | Rotate PDF | LIB | P12 | 🟢 | ⬜ |
| PDF-04 | Reorder Pages | LIB | P12 | 🟢 | ⬜ |
| PDF-05 | Extract Pages | LIB | P12 | 🟢 | ⬜ |
| PDF-06 | PDF to Word/Excel/PowerPoint | CL / LIB terbatas | P12 | 🔴 | ⬜ |
| PDF-07 | Word to PDF | CL | P12 | 🔴 | ⬜ |
| PDF-08 | Image to PDF | LIB | P12 | 🟢 | ⬜ |
| PDF-09 | Compress PDF | LIB | P12 | 🟡 | ⬜ |
| PDF-10 | Encrypt PDF (AES-256/128, RC4-128) | LIB | P12 | 🟡 | ⬜ |
| PDF-11 | Decrypt PDF | LIB | P12 | 🟢 | ⬜ |
| PDF-12 | Add Watermark | LIB | P12 | 🟢 | ⬜ |
| PDF-13 | Remove Watermark | LIB | P12 | 🔴 | ⬜ |
| PDF-14 | Redact Content (permanen) | LIB | P12 | 🔴 | ⬜ |
| PDF-15 | Scan PDF to Text (OCR) | ML/LIB | P12 | 🟡 | ⬜ |
| PDF-16 | Image to Searchable PDF (OCR 8 bahasa) | LIB | P12 | 🟡 | ⬜ |
| PDF-17 | AI Document Recognition | CL/ML | P12 | 🔴 | ⬜ |
| PDF-18 | Page Numbering | LIB | P12 | 🟢 | ⬜ |
| PDF-19 | Metadata Editor | LIB | P12 | 🟢 | ⬜ |
| PDF-20 | Document Comparison | LIB+NAT | P12 | 🟡 | ⬜ |
| PDF-21 | Form Filler (AcroForm) | LIB | P12 | 🟡 | ⬜ |

## 6. Universal Converter

*Beberapa entri adalah alias dari tool grup lain (satu implementasi, dua pintu masuk).*

| ID | Tool | Engine | Fase | Alias / Catatan |
|---|---|---|---|---|
| CNV-01 | Universal Video Transcoder | FF | P13 | alias VID-18 |
| CNV-02 | Video to Animated GIF | FF | P13 | alias VID-06 |
| CNV-03 | Video to Audio Extractor (MP3/WAV/FLAC/AAC) | FF | P13 | perluasan VID-08 |
| CNV-04 | Video Upscaler (AI) 2x | ML | P13 | alias VID-07 |
| CNV-05 | Universal Audio Transcoder | FF | P13 | alias AUD-13 |
| CNV-06 | Hi-Res Lossless Converter | FF | P13 | FLAC/WAV |
| CNV-07 | Voice to Text Converter | ML/CL | P13 | alias AUD-27 |
| CNV-08 | 8D / Spatial Audio Generator | FF | P13 | alias AUD-05 |
| CNV-09 | Universal Image Converter (PNG/JPG/WebP/AVIF) | NAT | P13 | alias IMG-23 |
| CNV-10 | SVG to Raster (PNG/JPG) | LIB | P13 | |
| CNV-11 | Icon Asset Generator (+favicon) | NAT | P13 | alias IMG-24 |
| CNV-12 | Base64 Image Encoder | NAT | P13 | alias IMG-25 |
| CNV-13 | PDF to Word / Word to PDF | CL | P13 | alias PDF-06/07 |
| CNV-14 | PDF to Image Matrix | NAT | P13 | PdfRenderer |
| CNV-15 | OCR Document Scanner → .txt | ML/LIB | P13 | alias PDF-15 |
| CNV-16 | Universal Archive Extractor (.zip) | LIB | P13 | |
| CNV-17 | Cross-Archive Converter (ZIP/RAR/7Z/TAR.GZ) | LIB | P13 | RAR hanya baca; tulis RAR tidak didukung → catat |
| CNV-18 | AES-256 Encrypted Archive | LIB | P13 | zip4j |
| CNV-19 | EPUB Converter | LIB | P13 | 🔴 kualitas konversi |
| CNV-20 | Comic Book Converter (CBZ/CBR → PDF/EPUB) | LIB | P13 | |
| CNV-21 | Universal Font Converter (TTF/OTF/WOFF/WOFF2) | LIB/CL | P13 | 🔴 OTF↔TTF sulit; WOFF/WOFF2 lebih mudah |
| CNV-22 | Web-Font Optimizer (subsetting) | LIB/CL | P13 | 🔴 |
| CNV-23 | JSON ⇄ XML | NAT | P13 | |
| CNV-24 | CSV ⇄ TSV | NAT | P13 | |
| CNV-25 | Code Formatter (JSON/kode) | LIB | P13 | |
| CNV-26 | Markdown to HTML | LIB | P13 | |
| CNV-27 | GeoJSON to KML | NAT | P13 | |
| CNV-28 | STL to OBJ | NAT | P13 | parser sendiri (ASCII + binary) |

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
