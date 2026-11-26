# Audit Proyek HariMu (Fase 0)

Dokumen ini disusun setelah membaca langsung struktur folder dan file konfigurasi aktual: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`, dan seluruh berkas sumber di `app/src/`.

## Arsip Fase 0 (historis — sebagian klaim sudah usang)

> Bagian §1–§4 adalah potret Fase 0 dan **tidak** menggambarkan kode saat ini. Klaim seperti
> "54 tool", "hanya `RECORD_AUDIO`", "belum ada WorkManager/Screen Recorder/AI gateway", "single-module
> belum dipisah", dan "belum Hilt" sengaja dipertahankan sebagai catatan sejarah. Status terkini ada di
> §5–§7. Arsitektur resmi (diputuskan pemilik di Fase K4): **single-module `:app` + `AppContainer` manual**.

### 1. Struktur Modul Saat Ini

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

### 2. Versi Toolchain, SDK, & Dependency

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
- Daftar besar: `androidx-paging-compose` (`3.3.2`) untuk daftar desa grup Nusantara (`NUS-03`)
- Unit Test: `junit` (`4.13.2`)

### 3. Audit `AndroidManifest.xml`

Dari `app/src/main/AndroidManifest.xml`:
- Izin: hanya `android.permission.RECORD_AUDIO` (baris 4), tidak ada izin penyimpanan luas (`READ_EXTERNAL_STORAGE`/`READ_MEDIA_*`) karena pemilihan media memakai Photo Picker & SAF.
- Application class: `.HariMuApp`, `android:allowBackup="false"`, launcher icon `@mipmap/ic_launcher` & `@mipmap/ic_launcher_round`.
- `FileProvider` terkonfigurasi (`${applicationId}.fileprovider` dengan `res/xml/file_paths.xml`) untuk berbagi output secara aman.

### 4. Kesesuaian terhadap `01-ARSITEKTUR.md` & `SKILL.md`

#### Sudah Sesuai
1. **Registry-driven & Kontrak Tool**: `ToolRegistry` (`core/toolapi/ToolRegistry.kt`) dan `Tool` (`core/toolapi/ToolContract.kt`) menjadi sumber tunggal untuk beranda, pencarian, katalog, favorit, dan layar generik `ToolScreen.kt`.
2. **Engine Terpisah dari Tool**: Logika pemrosesan berada di `core/engines/*` (Video, Audio, Image, PDF, Converter, KeyVault).
3. **On-device First & Tanpa API Key di APK**: Semua 54 tool saat ini berjalan lokal di perangkat tanpa rahasia/API key di kode.
4. **Penyimpanan & Backup `.omni`**: Room (`AppDatabase.kt`) menyimpan riwayat, favorit, dan Key Vault; DataStore (`SettingsRepository.kt`) menyimpan tema (termasuk AMOLED), bahasa, dan setelan; `WorkspaceRepository.kt` mengemas/memulihkan `.omni` dengan opsi AES-256-GCM.

#### Temuan Masalah & Celah Arsitektur (Perlu Tindak Lanjut di Fase Berikutnya)
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
| M7 job video di memori Worker | **Selesai (statis)** | Status job di KV (`job:<id>`) dan hasil di R2 (`jobs/<id>/result`) — `src/jobs.ts`; tes K6 membaca keduanya langsung. |
| M8 escape filter FFmpeg | **Terbuka** | Nilai masih berasal dari dropdown; hardening penuh belum dilakukan. |
| M9 skew versi AGP/Kotlin vs Compose BOM | **Terbuka** | Tidak diubah tanpa toolchain untuk memverifikasi; dicatat sebagai risiko build. |
| M10 instrumented test / dokumen usang | **Sebagian** | AUDIT.md, README, metadata.json, dan CONTRIBUTING.md disinkronkan; instrumented test belum ada. |
| LOW: `fromBase64url` 500 | **Selesai** | Token → 401, unduhan → 403; ada test. |
| LOW: GCM tanpa AAD | **Diterima** | Format lama harus tetap terbaca; AAD akan ditambahkan bersama versi format baru. |
| LOW: TTS tanpa cap | **Selesai (statis)** | Klien menolak >4000 karakter sebelum memanggil gateway (`CoreAndVideoTools.kt`, CORE-03); server tetap membatasi 4000 (`requireString`) dengan tes batas. |

**Verifikasi**: statis saja (tidak ada JDK/Gradle/Android SDK di lingkungan audit-fix). Test baru
(`AuditV3HardeningTest`, test install-rate-limit Worker) **belum dieksekusi**.

## 6. Tinjauan statis v4 — desk-check tes terhadap kode (tanpa eksekusi)

Ditinjau dengan membaca tes + seluruh kode yang dipanggil (bukan hasil `gradle test`).
Label: **konsisten** = signature & nilai sesuai kode; **mencurigakan** = bergantung pada
perilaku/urutan yang tidak dijamin; **pasti salah** = asersi bertentangan dengan kode.

### 6.1 `app/src/test/java/com/harimu/app/AuditV3HardeningTest.kt`

| Tes (baris) | Hasil | Alasan / bukti |
|---|---|---|
| `xmlToJsonRejectsDoctypeDeclaration` (21) | konsisten | `ConverterEngine.xmlToJson` memasang `disallow-doctype-decl` dan `EntityResolver` yang melempar `SAXException` (`ConverterEngine.kt:107-129`); DOCTYPE dengan entitas eksternal pasti gagal → `runCatching` menangkap. |
| `xmlToJsonStillParsesPlainDocuments` (30) | konsisten | `elementToJson` mengelompokkan anak per tag (`ConverterEngine.kt:131-156`), hasil pretty JSON memuat `"b"` dan `ok`. |
| `markdownDropsDangerousSchemes` (37) | konsisten | `safeUrl` menolak `//`, `javascript:`, `data:`, `file:`, skema asing, dan spasi (`ConverterEngine.kt:355-370`); `https`/`mailto` tanpa spasi lolos apa adanya. |
| `markdownRendersUnsafeLinkAsPlainText` (49) | konsisten | Regex tautan `ConverterEngine.kt:332` menangkap `javascript:alert(1` (berhenti di `)`), `safeUrl` → null, sehingga label saja yang keluar; sisa `)` tidak memengaruhi asersi. |
| `toolInputRoundTripPreservesParamsAndUris` (56) | konsisten | `ToolInputCodec.encode` menulis `uris`, `secondary`, `text`, `params` (`ToolInputCodec.kt:30-46`); decode mengembalikan `80.0` sebagai `floatOrNull` → `getFloat` = 80f, `true` → Boolean, `"MP4"` → String, `3` → `intOrNull`. |
| `toolInputDecodeToleratesGarbage` (74) | konsisten | `decode` mengembalikan `ToolInput()` saat JSON gagal di-parse (`ToolInputCodec.kt:49-56`). |
| `outputCodecRoundTrip` (81) | konsisten | `Entry(path, mimeType, displayName)` cocok dengan `ToolOutputCodec.Entry` (`ToolInputCodec.kt:77`), key `path`/`mime`/`name` bolak-balik (`:79-105`). |

Ketergantungan test: `RobolectricTestRunner` + `android.net.Uri` tersedia karena
`testImplementation(libs.robolectric)` (`app/build.gradle.kts:156`) dan
`unitTests.isIncludeAndroidResources = true` (`:95`).

### 6.2 `app/src/test/java/com/harimu/app/ConverterAndRegistryTest.kt`

| Tes (baris) | Hasil | Alasan / bukti |
|---|---|---|
| `registryContainsAllSixGroups` (15) | konsisten | `ToolGroup.entries` (Kotlin 1.9+, proyek 2.2.10), `countByGroup` ada (`ToolRegistry.kt:42`). |
| `jsonXmlRoundTripWorks` (25) | konsisten | `jsonToXml` menghasilkan `<app>HariMu</app>` (`ConverterEngine.kt:61-70`); `xmlToJson` mempertahankan teks. |
| `csvTsvConversionPreservesQuotedCommas` (34) | konsisten | Parser RFC-4180 (`:159-201`) + `rowsToDelimited` memberi `id\tname\n1\tHello, World`, dan baliknya mengutip ulang sel ber-koma. |
| `markdownAndGeoJsonConvertCleanly` (43) | **pasti salah (sudah diperbaiki)** | Asersi `"<coordinates>106.82,-6.17,0</coordinates>"` bertentangan dengan `coordinate()` yang menginterpolasi `Double` (`:469-475`) → keluaran nyata `...,0.0`; tes lain di repo menegaskan `0.0` (`ConverterCoreTest.kt:111`). Diubah ke `0.0`. Bagian markdown (`<h1>`, `<strong>`) konsisten. |
| `binaryStlConvertsToObj` (55) | konsisten | Buffer 84+50 byte dengan `triangles=1` lolos `isBinaryStl` (`:511-517`); `stlToObj` mencetak `v 1.0 2.0 3.0` dan `f 1 2 3` (`:489-509`). |

### 6.3 `app/src/test/java/com/harimu/app/ToolRegistryTest.kt`

| Tes (baris) | Hasil | Alasan / bukti |
|---|---|---|
| `registryContainsAll163CatalogEntriesAcrossSixGroups` (13) | konsisten | ID unik per prefiks di `feature/tools/`: CORE 9, VID 30, AUD 28, IMG 47, PDF 21, CNV 28 = 163; tidak ada prefiks lain. `distinctBy` + `sortedWith` (`ToolRegistry.kt:19-35`). |
| `everyCatalogIdFrom02KatalogFiturIsResolvable` (27) | konsisten | ID 01..N per grup semuanya ada (AUD-01..28 terverifikasi); `findById` (`:37`) dan `title`/`description` diimplementasi string literal pada tool, bukan resource. |
| `searchFindsBothImplementedAndStubToolsInIndonesianAndEnglish` (48) | konsisten | `search` mencocokkan id/judul/deskripsi/engineLabel (`:44-54`). CORE-02 hanya muncul 3× (2 komentar + 1 id) → hasil 1. Judul terverifikasi: VID-05 "Change Video Speed", VID-07 "Video Upscaler 1.5–2×", VID-08 "Video to Audio (MP3)", IMG-02 "Ubah Ukuran Gambar", IMG-01 "Crop Gambar Pro", PDF-01 "Gabung PDF", PDF-10 "Enkripsi PDF", PDF-14 "Redaksi Permanen", CNV-12 (alias, `AliasTool.isStub=false`), CNV-17 "Cross-Archive Converter", CNV-22 "Web-Font Optimizer (Subsetting)". Tidak ada tool CONVERTER yang `isStub=true` (satu-satunya `StubTool` dipakai untuk grup media, `CatalogStubs.kt:34`). |

### 6.4 `app/src/test/java/com/harimu/app/JobQueueAndManagerTest.kt`

| Tes (baris) | Hasil | Alasan / bukti |
|---|---|---|
| `queuePolicyEnforcesMaxTwoParallelJobsInFifoOrder` (81) | konsisten | Dihitung manual terhadap `JobQueuePolicy`: enqueue j1/j2 START_NOW, j3/j4 QUEUED; `completeAndDequeueNext(j1)`→"j3"; `cancel(j2)`→`(true,"j4")`; `restore([r1,r2,r3],[q1])`→`[r1,r2]` dengan sisa `[r3,q1]` (`JobQueuePolicy.kt:27-76`). |
| `dummyWorkerRunsCompletesWritesHistoryAndCleansJobTemp` (112) | konsisten | `doWork()` dummy menulis progres 20/50/80/100, `createOutputFile(toolId,"txt")` → `HariMu_VID-05_<stamp>.txt` (`FileStorageManager.kt:32` + `:192-206`), `cleanupJobTemp` di `finally`, satu baris `HistoryEntity`. `TestListenableWorkerBuilder`, `SynchronousExecutor`, `ShadowStatFs` tersedia (`work-testing` di `build.gradle.kts:159`). |
| `cancelJobMarksCancelledClearsTempAndPromotesQueuedJob` (158) | mencurigakan | Worker j1/j2 sudah di-enqueue saat `enqueueDummyJob`; dengan `SynchronousExecutor` eksekusinya bisa selesai (status SUCCEEDED) sebelum `cancelJob(j1)`. `cancelJob` memang menulis CANCELLED sesudahnya (`JobManager.kt:140-165`), tetapi ada jendela balapan dengan `updateCompletion` worker. Juga `onWorkerFinished` di worker memakai `app.container.jobManager` (policy berbeda), sehingga asersi policy tes bergantung pada detail itu. Bukan pasti salah. |
| `recoverPendingJobsRestoresActiveAndQueuedLimitsAfterRestart` (179) | mencurigakan | Urutan & kuota sesuai: `getPendingOrRunning` ASC, rec-1 RUNNING → `runningIds`, rec-2/3 QUEUED → `queuedIds`, `restore` → aktif `{rec-1,rec-2}`, sisa `[rec-3]`; `dispatchQueuedJob` menulis `workRequestId` sebelum enqueue sehingga `rec-1` non-blank dan `rec-3` tetap `""`. Risiko: `HariMuApp.onCreate` juga menjalankan `recoverPendingJobs()` di `appScope` (`HariMuApp.kt:56-60`) sehingga bisa berjalan bersamaan dengan `db.clearAllTables()` di `setUp` (`JobQueueAndManagerTest.kt:66`). Bukan pasti salah, tetapi flaky-prone. |

Catatan dependency: `androidx.work.testing` (`build.gradle.kts:159`), `androidx.test.core` (`:157`),
`robolectric` (`:156`) — semua yang dipakai tes ini tersedia; tidak ada import yang menggantung.

### 6.5 Tinjauan kompilasi statis tiga berkas engine berisiko

| Berkas | Hasil | Catatan |
|---|---|---|
| `core/media/MediaJob.kt` | konsisten | Simbol eksternal cocok: `FFmpegKit.executeWithArgumentsAsync(String[], ExecuteCallback, LogCallback, StatisticsCallback)`, `ReturnCode.isSuccess/isCancel`, `stats.time`; helper `MediaProbe.probeFile`, `BoundedLogCollector.append/allLines/tail`, `FfmpegProgressParser.*`, `MediaErrorMapper.mapFfmpegFailure(returnCodeValue,isCancelled,logs,isEnglish)`→`userMessage/technicalDetail`, `MediaUriUtils.cleanupQuietly(vararg File?)`. `currentCoroutineContext()` di dalam `use {}` sah karena `use` inline. `when` pada `ToolProgress` di worker punya `else`. |
| `core/jobs/ToolJobWorker.kt` | konsisten | Semua `jobDao.updateCompletion(...)` 8 argumen posisional cocok dengan `Daos.kt:81-93`; `HistoryEntity` memakai named args; `R.string.job_*` ada di `strings.xml:125-131` dengan placeholder `%1$s`/`%1$d` yang sesuai; `Result` mewarisi `ListenableWorker.Result`; import `ensureActive`/`NonCancellable`/`first`/`onEach` lengkap. |
| `core/ai/AiClient.kt` | konsisten | `MAX_BINARY_BYTES`/`MAX_DOWNLOAD_BYTES` `private const` di companion dipakai dari method instance (sah); `toBinary`/`download` memanggil `currentCoroutineContext()` di dalam `use {}` (inline) sehingga boleh suspend; `AiException(code,message,retryable,retryAfterSec,httpStatus)` sesuai urutan konstruktor (`AiClient.kt:35-45`); import `pow`/`min`/`IOException`/`InterruptedIOException` dipakai. |

Tidak ada perubahan kode pada ketiga berkas ini.

**Batas**: seluruh tinjauan ini statis. Tidak ada `gradle test`, tidak ada eksekusi Robolectric, dan
tidak ada klaim lulus/gagal. Satu tes diperbaiki karena asersinya terbukti bertentangan dengan kode.

## 7. Audit v4 (25 November 2026) — ringkasan statis

Ringkasan `AUDIT-HARIMU-2026-11-25.md` beserta hasil fase K0–K3. **Label: statis, belum dikompilasi** —
tidak ada `gradle assembleDebug`, `testDebugUnitTest`, `lintDebug`, atau `connectedDebugAndroidTest`
yang dijalankan di lingkungan ini.

| Area | Temuan audit 25 Nov | Tindak lanjut | Status |
|---|---|---|---|
| C1 wrapper hilang | README/CI memakai `./gradlew`, repo tidak punya `gradlew` | **K0**: CI memakai `gradle/actions/setup-gradle` + Gradle 9.3.1, perintah menjadi `gradle ...`; README disinkronkan | Selesai (statis) |
| C2 `libx264` di VID-07 | Encoder GPL tidak ada di build FFmpeg LGPL | **K2**: jalur utama Media3 H.264 + FFmpeg `mpeg4` hanya untuk penajaman, guard `core/media/FfmpegCapabilities.kt`; `FFmpegKit.execute(String)` sudah tidak ada | Selesai (belum diuji perangkat) |
| H1 build/test tak pernah dieksekusi | 167 unit test tanpa bukti hijau | **K1**: desk-check statis seluruh tes, satu asersi keliru diperbaiki (`ConverterAndRegistryTest` koordinat KML `0.0`) | Sebagian (butuh CI) |
| H3 dokumen usang | README ±50 tool, AUDIT "54 tool", DECISIONS butir 2, SKILL/ARSITEKTUR menganjurkan Hilt | **K4**: README memuat 163 tool dari registry, §1–§4 diarsipkan, butir 2 dikoreksi, SKILL/ARSITEKTUR diselaraskan ke `:app` + `AppContainer` | Selesai (statis) |
| H4 tanpa instrumented test | `androidTest/` hanya berisi 3 aset | **K3**: 7 berkas tes instrumented (media, audio, gambar, PDF, converter, keystore) + job CI `instrumented` `continue-on-error: true` | Ditulis, **belum dijalankan** |
| M1 `applicationId` acak | `com.aistudio.harimu.xqmzpk` | **K5**: diganti menjadi `com.harimu.app` (sama dengan `namespace`); manifest & FileProvider memakai `${applicationId}` | Selesai (statis) |
| M5 detekt & skema Room | detekt tidak terpasang, `app/schemas/` tidak di-commit | Belum diubah | Terbuka |
| H2 gateway mock | `wrangler.toml` `PROVIDER_MODE = "mock"`, KV placeholder | **K6**: provider live = Cloudflare Workers AI (binding `AI`), README memuat urutan go-live; belum di-deploy/secret belum diisi | Siap live (menunggu deploy) |

Batas yang masih berlaku:
- `app/build.gradle.kts` **belum** punya `androidTestImplementation` (hanya `testImplementation`), sehingga
  tes K3 belum bisa dikompilasi dan job `instrumented` akan gagal sampai dependency
  `androidx.test.ext:junit`, `androidx.test:runner`, dan `androidx.test:rules` ditambahkan.
- Seluruh hasil K0–K3 adalah verifikasi statis (membaca kode, grep, keseimbangan kurung, validasi YAML).
