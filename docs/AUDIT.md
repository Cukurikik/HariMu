# Audit Proyek HariMu (Fase 0)

Dokumen ini disusun setelah membaca langsung struktur folder dan file konfigurasi aktual: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`, dan seluruh berkas sumber di `app/src/`.

## 1. Struktur Modul Saat Ini

Berdasarkan `settings.gradle.kts` (baris 22–23):
- `rootProject.name = "HariMu"`
- Modul Gradle aktif: `:app` (single-module)

Organisasi paket di dalam `app/src/main/java/com/harimu/app/` sudah memisahkan tanggung jawab menjadi paket `core/*` dan `feature/*`:
- `core/design/` (`Theme.kt`, `Components.kt`)
- `core/toolapi/` (`ToolContract.kt`, `ToolRegistry.kt`)
- `core/data/` (`AppDatabase.kt`, `Daos.kt`, `Entities.kt`, `SettingsRepository.kt`, `WorkspaceRepository.kt`)
- `core/files/` (`FileStorageManager.kt`)
- `core/engines/` (`AudioEngine.kt`, `ConverterEngine.kt`, `ImageEngine.kt`, `KeyVaultEngine.kt`, `PdfEngine.kt`, `VideoEngine.kt`)
- `feature/home/`, `feature/catalog/`, `feature/toolscreen/`, `feature/studios/`, `feature/tools/`, `feature/history/`, `feature/settings/`

## 2. Versi Toolchain, SDK, & Dependency

Dari `gradle/libs.versions.toml` dan `app/build.gradle.kts`:
- **AGP**: `9.1.1` (`com.android.application`, built-in Kotlin support)
- **Kotlin**: `2.2.10` (`org.jetbrains.kotlin.plugin.compose`, `org.jetbrains.kotlin.plugin.serialization`)
- **KSP**: `2.3.5` (`com.google.devtools.ksp`)
- **Compose BOM**: `2024.09.00`
- **SDK** (`app/build.gradle.kts` baris 10, 14–15): `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36`
- **Java Target** (`app/build.gradle.kts` baris 45–48): Java 17

### Daftar Dependency (`app/build.gradle.kts` baris 56–84)
- UI & Compose: `androidx-core-ktx` (`1.18.0`), `androidx-activity-compose` (`1.10.1`), `androidx-lifecycle-runtime-ktx` / `runtime-compose` / `viewmodel-compose` (`2.8.7`), Compose UI / Material 3 / WindowSizeClass / `material-icons-extended` (BOM `2024.09.00`), `coil-compose` (`2.7.0`)
- Navigasi & Serialisasi: `androidx-navigation-compose` (`2.8.9`), `kotlinx-serialization-json` (`1.7.3`)
- Data & Async: `androidx-room-runtime` / `room-ktx` / `room-compiler` KSP (`2.7.0`), `androidx-datastore-preferences` (`1.1.7`), `kotlinx-coroutines-android` (`1.10.2`)
- Media/Metadata: `androidx-exifinterface` (`1.3.7`)
- Unit Test: `junit` (`4.13.2`)

## 3. Audit `AndroidManifest.xml`

Dari `app/src/main/AndroidManifest.xml`:
- Izin: hanya `android.permission.RECORD_AUDIO` (baris 4), tidak ada izin penyimpanan luas (`READ_EXTERNAL_STORAGE`/`READ_MEDIA_*`) karena pemilihan media memakai Photo Picker & SAF.
- Application class: `.HariMuApp`, `android:allowBackup="false"`, launcher icon `@mipmap/ic_launcher` & `@mipmap/ic_launcher_round`.
- `FileProvider` terkonfigurasi (`${applicationId}.fileprovider` dengan `res/xml/file_paths.xml`) untuk berbagi output secara aman.

## 4. Kesesuaian terhadap `01-ARSITEKTUR.md` & `SKILL.md`

### Sudah Sesuai
1. **Registry-driven & Kontrak Tool**: `ToolRegistry` (`core/toolapi/ToolRegistry.kt`) dan `Tool` (`core/toolapi/ToolContract.kt`) menjadi sumber tunggal untuk beranda, pencarian, katalog, favorit, dan layar generik `ToolScreen.kt`.
2. **Engine Terpisah dari Tool**: Logika pemrosesan berada di `core/engines/*` (Video, Audio, Image, PDF, Converter, KeyVault).
3. **On-device First & Tanpa API Key di APK**: Semua 54 tool saat ini berjalan lokal di perangkat tanpa rahasia/API key di kode.
4. **Penyimpanan & Backup `.omni`**: Room (`AppDatabase.kt`) menyimpan riwayat, favorit, dan Key Vault; DataStore (`SettingsRepository.kt`) menyimpan tema (termasuk AMOLED), bahasa, dan setelan; `WorkspaceRepository.kt` mengemas/memulihkan `.omni` dengan opsi AES-256-GCM.

### Temuan Masalah & Celah Arsitektur (Perlu Tindak Lanjut di Fase Berikutnya)
1. **Struktur Multi-Modul Gradle (`settings.gradle.kts` baris 23)**: Proyek masih berbentuk single-module `:app`. Pemisahan menjadi submodul fisik (`core/*`, `feature/*`) sesuai `SKILL.md` bagian 4 belum dilakukan.
2. **Dependency Injection (`app/src/main/java/com/harimu/app/HariMuApp.kt`)**: Masih memakai `AppContainer` manual, belum memakai Hilt (`SKILL.md` bagian 3).
3. **Job Manager / WorkManager (`feature/toolscreen/ToolViewModel.kt`)**: Eksekusi tool berjalan pada `viewModelScope` dengan pembatalan korutin; antrean `WorkManager` + foreground service untuk job berat (>3 detik) tahan rotasi/process death (`01-ARSITEKTUR.md` bagian 1.5) belum terpasang.
4. **Engine Tambahan yang Belum Diintegrasikan**: `Media3 Transformer`, wrapper `FFmpeg` (LGPL), model ML unduhan (`ONNX`/`LiteRT` + manajer model di `noBackupFilesDir`), `Screen Recorder` (`MediaProjection`), dan `ai-client` ke Cloudflare Worker (`01-ARSITEKTUR.md` bagian 3–8).
5. **Pengujian (`app/src/test/java/com/harimu/app/ConverterAndRegistryTest.kt`)**: Baru tersedia 5 unit test JVM; instrumented test dengan aset sampel dan golden test filter gambar (`01-ARSITEKTUR.md` bagian 13) belum dibuat.
6. **Kualitas Kode & CI**: Sebelum fase ini belum ada `.editorconfig`, konfigurasi `detekt`/`ktlint`, `docs/LICENSES.md`, dan workflow GitHub Actions.

## 5. Status setelah Audit v3 (24 November 2026)

Audit v3 menilai `origin/main` `8ad0a93`. Bagian di atas adalah catatan Fase 0 dan sebagian sudah usang
(mis. WorkManager, Screen Recorder, AI gateway, dan 100+ unit test kini ada). Tabel ini mencatat
tindak lanjut pada commit audit-fix.

| Temuan | Status | Bukti / catatan |
|---|---|---|
| C1 `INTERNET` hilang | **Selesai** | `AndroidManifest.xml` (izin sudah ditambahkan di Fase 14). |
| C2 Rilis memakai debug keystore / tanpa R8 | **Selesai** | `app/build.gradle.kts`: signing rilis opsional via `keystore.properties`/env, R8 + `shrinkResources` aktif, rules di `app/proguard-rules.pro`. Clone bersih memakai debug keystore bawaan Gradle. |
| H1 `/v1/install` tanpa batas | **Sebagian** | Rate limit per IP (`INSTALL_RATE_PER_MIN`, `INSTALL_DAILY_PER_IP`) + `INSTALL_REGISTRATION_KEY` opsional (header `x-install-key`, waktu-konstan). Attestation Play Integrity belum (butuh akun/kunci). |
| H2 private key plaintext | **Selesai** | `KeyVaultEngine.kt` lama dihapus; private key RSA/EC disegel kunci master AndroidKeyStore (`VaultCryptoEngine`). SQLCipher tetap tidak dipakai (didokumentasikan). |
| H3 XXE | **Selesai** | `ConverterEngine.xmlToJson` mematikan DTD/entitas eksternal/XInclude; ada test regresi. |
| H4 destructive migration | **Selesai (kebijakan)** | `fallbackToDestructiveMigrationOnDowngrade()` + `exportSchema`/`room.schemaLocation`. Upgrade tanpa `Migration` akan gagal keras, bukan menghapus data. |
| H5 tool berat di `viewModelScope` | **Selesai** | `ToolJobWorker` menjalankan tool nyata (input lewat `ToolInputCodec`), progres ke Room, foreground `dataSync`; `ToolViewModel` merutekan grup VIDEO/AUDIO/PDF/CONVERTER + tool AI. |
| H6 kebocoran bitmap Image Matrix | **Selesai** | Bitmap tak terpakai dilepas, batas 24 cache, `recycle()` di `onCleared`. |
| H7 OOM / zip-bomb | **Selesai (batas keras)** | AI biner 96 MB / unduhan 512 MB; arsip 128 MB per entri & 512 MB total; `.omni` 32 MB per entri. Backup .omni masih dibangun di memori dengan batas tersebut. |
| H8 rantai pasok CI | **Sebagian** | Action di-pin SHA, ktlint dari Maven Central + verifikasi SHA-1, job backend (`npm ci`/typecheck/vitest), build release R8. Detekt belum diaktifkan; instrumented test belum ada. |
| M1 XSS markdown | **Selesai** | `safeUrl` menolak `javascript:`/`data:`/`file:`/`//host`/skema tak dikenal; ada test. |
| M2 integritas unduhan | **Sebagian** | Model: SHA-256 saat unduh + cek ukuran manifes. `traineddata` OCR tetap hanya ukuran minimum (tidak ada checksum resmi per bahasa). |
| M3 batas FGS `dataSync` | **Diterima** | Job panjang tetap bisa diputus Android 15+ setelah ~6 jam; dicatat di DECISIONS. |
| M4 impor non-transaksional | **Selesai** | `importOmniBackup` dalam `withTransaction`, favorit `REPLACE`. |
| M5 kata sandi arsip 4 karakter | **Selesai** | Minimum 8 karakter (`ArchiveEngine`). |
| M6 CORS `*` | **Selesai (dapat dibatasi)** | `ALLOWED_ORIGIN`; default `*` tetap aman untuk Bearer token tanpa cookie. |
| M7 job video di memori Worker | **Terbuka** | Belum diubah; perlu streaming provider/queue. Dicatat sebagai batas. |
| M8 escape filter FFmpeg | **Terbuka** | Nilai masih berasal dari dropdown; hardening penuh belum dilakukan. |
| M9 skew versi AGP/Kotlin vs Compose BOM | **Terbuka** | Tidak diubah tanpa toolchain untuk memverifikasi; dicatat sebagai risiko build. |
| M10 instrumented test / dokumen usang | **Sebagian** | AUDIT.md, README, metadata.json, dan CONTRIBUTING.md disinkronkan; instrumented test belum ada. |
| LOW: `fromBase64url` 500 | **Selesai** | Token → 401, unduhan → 403; ada test. |
| LOW: GCM tanpa AAD | **Diterima** | Format lama harus tetap terbaca; AAD akan ditambahkan bersama versi format baru. |
| LOW: TTS tanpa cap | **Terbuka** | Cap panjang klien lokal belum ditambahkan (server sudah membatasi 4000 karakter). |

**Verifikasi**: statis saja (tidak ada JDK/Gradle/Android SDK di lingkungan audit-fix). Test baru
(`AuditV3HardeningTest`, test install-rate-limit Worker) **belum dieksekusi**.
