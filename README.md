# HariMu — Aplikasi Utilitas Serbaguna Android

[![HariMu CI](https://github.com/Cukurikik/HariMu/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Cukurikik/HariMu/actions/workflows/android-ci.yml)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![targetSdk](https://img.shields.io/badge/targetSdk-36-green)

Aplikasi Android (Kotlin + Jetpack Compose + Material 3) berbasis arsitektur **Registry-Driven** untuk kebutuhan utilitas harian secara lokal (on-device). Semua tool berjalan tanpa akun; AI dan koneksi cloud bersifat opsional dan memakai gateway/akun milik pengguna.

## Fitur Utama (6 Grup Tool)

1. **Core & Perekam** — Screen Recorder MediaProjection (`CORE-05`), Voice Recorder FFT dengan WAV/AAC/Opus (`CORE-06`), Text-to-Speech ke WAV (`CORE-03`), Key Vault Keystore + ekspor PEM bergerbang biometrik (`CORE-07`), Backup Workspace `.omni` skema 2 (`CORE-08`), dan Cloud Connections S3/R2·Drive·Dropbox·REST (`CORE-09`).
2. **Video Forge** — Potong video (`VID-01`), gabung MP4 (`VID-02`), ekstrak audio M4A (`VID-08`), mux audio ke video (`VID-16`), pecah video ke ZIP (`VID-21`), ekstrak frame PNG (`VID-22`), dan bisukan video (`VID-24`).
3. **Audio Studio** — Live 5-Band Equalizer (`AUD-02`), ubah volume (`AUD-08`), potong audio (`AUD-09`), gabung/mix audio (`AUD-10`), manipulasi kanal (`AUD-15`), hapus bagian hening (`AUD-16`), putar balik (`AUD-17`), efek gema (`AUD-24`), fade in/out (`AUD-26`), dan poster gelombang waveform (`AUD-28`).
4. **Image Matrix** — Editor kanvas interaktif (`IMG-47`), crop, resize, putar, balik, perluas kanvas, pelurus horizon, konverter PNG/JPG/WebP/PDF, generator paket ikon Android, Base64 encoder, kompresor cerdas, ekstraktor palet warna K-Means, inspeksi & hapus EXIF, watermark, filter warna, bingkai Polaroid, poster tipografi, gradien, refleksi, dan sprite sheet + atlas JSON.
5. **PDF Vault** — Gabung PDF (`PDF-01`), pisah ke ZIP (`PDF-02`), putar halaman (`PDF-03`), ekstrak rentang halaman (`PDF-05`), gambar ke PDF (`PDF-08`), watermark (`PDF-12`), penomoran halaman (`PDF-18`), dan perbandingan visual antar PDF (`PDF-20`).
6. **Universal Converter** — PDF ke gambar ZIP (`CNV-14`), inspeksi & kemas ulang ZIP (`CNV-16`), arsip terenkripsi AES-256-GCM `.hzm` (`CNV-18`), JSON ⇄ XML (`CNV-23`), CSV ⇄ TSV (`CNV-24`), formatter kode & JSON (`CNV-25`), Markdown ke HTML (`CNV-26`), GeoJSON ke KML (`CNV-27`), dan konverter mesh 3D STL → OBJ (`CNV-28`).

## Dokumentasi Proyek

- `01-ARSITEKTUR.md` — Cetak biru arsitektur
- `02-KATALOG-FITUR.md` — Katalog fitur lengkap
- `docs/AUDIT.md` — Audit struktur proyek, dependency, & temuan Fase 0
- `docs/DECISIONS.md` — Keputusan teknis
- `docs/LICENSES.md` — Daftar lisensi library & aset

## Verifikasi Build, Test & Lint

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```
