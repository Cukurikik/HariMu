# HariMu — Aplikasi Utilitas Serbaguna Android

[![HariMu CI](https://github.com/Cukurikik/HariMu/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Cukurikik/HariMu/actions/workflows/android-ci.yml)
![minSdk](https://img.shields.io/badge/minSdk-26-green)
![targetSdk](https://img.shields.io/badge/targetSdk-36-green)

Aplikasi Android (Kotlin + Jetpack Compose + Material 3) berbasis arsitektur **Registry-Driven** untuk kebutuhan utilitas harian secara lokal (on-device). Semua tool berjalan tanpa akun; AI dan koneksi cloud bersifat opsional dan memakai gateway/akun milik pengguna.

## Fitur Utama (170 Tool, 6 Grup)

`ToolRegistry` mendaftarkan **170 tool unik** dalam 6 grup (16/30/28/47/21/28); judul, deskripsi, dan parameter tiap tool berasal dari kode di `app/src/main/java/com/harimu/app/feature/tools/*.kt`. Daftar lengkap 170 judul ada di `02-KATALOG-FITUR.md`.

| Grup | Jumlah | Contoh tool (ID terverifikasi di registry) |
|---|---|---|
| Core | 16 | Pro Video Editor (`CORE-01`), Screen Recorder (`CORE-05`), Voice Recorder (`CORE-06`), Crypto Key Vault (`CORE-07`), AI Chat (`CORE-10`), Terjemah AI (`CORE-11`), Cuaca AI (`CORE-12`), Konversi Mata Uang (`CORE-13`), Kamus (`CORE-14`), Jadwal Sholat (`CORE-15`), Cari Kode Pos & Wilayah (`CORE-16`) |
| Video Forge | 30 | Trim (`VID-01`), gabung MP4 (`VID-02`), upscaler 1.5–2x lewat Media3 H.264 (`VID-07`), ekstrak audio MP3 (`VID-08`), mux audio (`VID-16`), pecah video (`VID-21`), ekstrak frame (`VID-22`), bisukan (`VID-24`) |
| Audio Forge & Studio | 28 | Pro 10-Band Equalizer live + ekspor (`AUD-02`), Audio Studio Pro multitrack (`AUD-21`), potong audio (`AUD-09`), hapus bagian hening (`AUD-16`), fade in/out (`AUD-26`), visualizer audio (`AUD-28`) |
| Image Forge & Matrix | 47 | Image Matrix Studio (`IMG-47`), crop (`IMG-01`), konversi format (`IMG-23`), hapus EXIF (`IMG-29`), AI enhancer (`IMG-14`), AI object remover (`IMG-13`) |
| PDF Vault | 21 | Gabung (`PDF-01`), pisah ke ZIP (`PDF-02`), gambar ke PDF (`PDF-08`), enkripsi (`PDF-10`), dekripsi (`PDF-11`), watermark (`PDF-12`), redaksi permanen (`PDF-14`), penomoran halaman (`PDF-18`), editor metadata (`PDF-19`), perbandingan dokumen (`PDF-20`) |
| Universal Converter | 28 | ZIP AES-256 (`CNV-18`), ekstraksi aman zip-slip (`CNV-16`), JSON ⇄ XML (`CNV-23`), CSV ⇄ TSV (`CNV-24`), formatter kode/JSON (`CNV-25`), Markdown → HTML (`CNV-26`), GeoJSON → KML (`CNV-27`), STL → OBJ (`CNV-28`) |

Catatan status: seluruh 170 ID teregistrasi dan dapat dicari; tidak ada `StubTool` yang diinstansiasi (`stubVideoTools()`/`stubAudioTools()`/`stubImageTools()` mengembalikan daftar kosong). VID-07 menghasilkan MP4/H.264 lewat Media3 (jalur FFmpeg hanya untuk penajaman, encoder `mpeg4`). Fitur PDF→Word/Excel/PowerPoint dan OCR tetap versi "Basic" seperti tertulis di katalog.

## Dokumentasi Proyek

- `01-ARSITEKTUR.md` — Cetak biru arsitektur
- `02-KATALOG-FITUR.md` — Katalog fitur lengkap
- `docs/AUDIT.md` — Arsip audit Fase 0, status Audit v3, & ringkasan Audit v4 (statis)
- `docs/DECISIONS.md` — Keputusan teknis
- `docs/LICENSES.md` — Daftar lisensi library & aset

## Verifikasi Build, Test & Lint

Repo ini **belum menyertakan Gradle Wrapper** (`gradlew`, `gradle/wrapper/`), jadi perintah memakai
Gradle yang terpasang di mesin/CI:

```bash
gradle assembleDebug testDebugUnitTest lintDebug
```

CI (`.github/workflows/android-ci.yml`) tidak memakai `./gradlew`. Workflow memasang Gradle 9.3.1 lewat
`gradle/actions/setup-gradle` (versi minimum untuk AGP 9.1.1), lalu menjalankan `gradle assembleDebug`,
`gradle testDebugUnitTest`, `gradle lintDebug`, dan `gradle assembleRelease`.

Wrapper bisa dibuat nanti dengan menjalankan `gradle wrapper` **sekali** di komputer yang punya Gradle;
perintah itu menghasilkan `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, dan
`gradle-wrapper.properties`. Setelah wrapper ada, perintah `./gradlew ...` dapat dipakai kembali dan
langkah setup Gradle di CI tidak lagi diperlukan.
