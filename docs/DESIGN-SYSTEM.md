# HariMu Design System & Product UI Architecture Specification

> **Status:** Master UI/UX Specification for Next Implementation Agent  
> **Package:** `com.harimu.app.core.design`  
> **Core Files:** `Tokens.kt`, `Theme.kt`, `DesignSurfaces.kt`, `DesignChrome.kt`, `DesignInputsAndActions.kt`, `DesignStatusAndCards.kt`, `DesignFeedback.kt`, `ToolFileComponents.kt`, `ToolInputComponents.kt`, `ToolParameterComponents.kt`, `ToolProgressComponents.kt`, `ToolResultComponents.kt`, `ComponentPreviews.kt`  
> **Shell & Entry:** `MainActivity.kt`, `navigation/HariMuAppShell.kt`, `navigation/TopLevelDestination.kt`  
> **Anchor Directive:** `/ui_agent.md` + `/docs/FRONTEND-AUDIT.md`

---

## 1. Filosofi Desain: *Studio Precision — Precision Pocket Workshop*

HariMu mengelola **182 alat** di **7 `ToolGroup`** (`CORE`, `VIDEO`, `AUDIO`, `IMAGE`, `PDF`, `CONVERTER`, `NUSANTARA`) serta belasan Studio interaktif (*Pro Video Editor*, *Audio Studio Pro*, *Image Matrix*, *PDF Studio*, *Universal Converter*, *Recorder*, *Key Vault*, *Nusantara Hub*). Skala sebesar ini akan terasa seperti gudang berantakan jika memakai UI generik.

Identitas visual HariMu adalah **Precision Pocket Workshop**: gabungan ketenangan instrumen studio profesional (presisi garis, angka monospasi sejajar, permukaan matte bertingkat) dengan kecepatan utilitas Android modern.

### Lima Prinsip Non-Negotiable
1. **Calibrated Craft Over Decorative Noise:** Setiap garis border, sudut, warna aksen, dan angka harus membawa informasi atau *affordance* nyata—bukan hiasan.
2. **Surface Tonal Hierarchy Over Shadows:** Kedalaman UI dibentuk oleh tangga warna `surfaceContainerLowest` → `surfaceContainerHighest` + border `1.dp` (`HariMuElevationTokens.card = 0.dp`), bukan drop shadow tebal atau *glassmorphism*.
3. **Typography Splits Intent From Telemetry:** `PlusJakartaSans` untuk niat & keterbacaan manusia; `JetBrainsMono` (`fontFeatureSettings = "tnum"`) untuk telemetri teknis (ID tool, durasi, bitrate, resolusi, ukuran byte, progres `%`) agar angka tidak bergetar saat diperbarui.
4. **Frictionless Tool Invocation:** Menemukan dan menjalankan salah satu dari 182 alat harus terasa maksimal **2–3 tap** (Cari/Pintas → Pilih Input/Parameter → Eksekusi + Rantai Aksi Lanjutan).
5. **60/120fps Budget First:** Desain yang memaksa main-thread I/O, overdraw blur, atau rekomposisi seluruh layar saat playhead/slider bergerak dianggap **cacat desain**.

---

## 2. Hukum Anti-AI-Slop (Zero-Tolerance Checklist)

Agent berikutnya **DILARANG** menghasilkan pola berikut pada layar mana pun:

| Pola Terlarang (AI Slop) | Pengganti Wajib di HariMu |
|---|---|
| Gradien aurora ungu/biru-putih atau latar dekoratif tanpa fungsi | Kanvas netral terkalibrasi (`AlabasterBg` / `DeepObsidian` / `PureBlack`) dengan scrim terarah hanya di atas foto (`HeroStudioBanner`) |
| Kartu di dalam kartu (*nested cards* dengan border ganda) | Gunakan `HariMuSectionCard` sebagai kontainer luar dan `HariMuInsetPanel` (`surfaceContainerLow`, **tanpa border**) untuk sub-grup di dalamnya |
| `Modifier.blur()` / glassmorphism pada daftar scroll atau top bar | Latar solid/tonal `surfaceContainerLow` dengan `HariMuDivider` atau hairline border `1.dp` (menjaga *GPU fill-rate* di Android kelas menengah) |
| Emoji acak di judul tool, badge, atau tombol aksi profesional | Ikon Material terkurasi (`ToolGroup.icon()`, `Icons.Filled.*` untuk aksi aktif/utama, `Icons.Outlined.*` untuk nav tak terpilih) |
| Angka/status palsu, progres dummy, atau tombol tanpa engine nyata | Hubungkan selalu ke `ToolProgress`, `JobManager`, `WorkspaceRepository`, atau tampilkan status/limitasi jujur |
| `Text` / `Box` klik tanpa feedback visual atau target `< 48.dp` | Selalu pakai ripple M3 (`clickable` / komponen button M3) dan `.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` / `.heightIn(min = 48.dp)` |
| Hardcode warna hex, ukuran teks, atau string bahasa di file fitur | Wajib lewat `HariMuThemeTokens`, `MaterialTheme.colorScheme`, dan `stringResource(...)` (`values/` + `values-en/`) |

---

## 3. Sistem Warna, Kontras & Polaritas Studio (`Theme.kt` & `Tokens.kt`)

### 3.1 Aturan Proporsi 60–30–10
- **65–75% Kanvas & Permukaan Netral:** Memberi ruang napas agar video, foto, waveform, dan halaman PDF pengguna menjadi fokus visual utama.
- **20–25% Struktur & Tipografi Sekunder:** `onSurfaceVariant`, `outline`, `outlineVariant`, dan `surfaceContainer*` untuk hierarki panel tanpa kebisingan visual.
- **5–10% Polaritas Aksen Fungsional:**
  - **Primary Focal — Studio Vermilion (`ElectricOrange #FF6B2C` Dark/AMOLED, `ElectricOrangeDark #C8430C` Light):** Khusus CTA eksekusi primer (*Jalankan Tool*, *Ekspor*, *Rekam*), indikator navigasi aktif, dan *playhead* aktif.
  - **Secondary Success Anchor (`TealEmerald #00BFA5` Dark/AMOLED, `TealEmeraldDark #007A6A` Light):** Aksi simpan/ekspor selesai, validasi sukses, status selesai.
  - **7 Group Accents (`HariMuColors.Group*`):** Hanya dipakai pada `ToolIdBadge`, ikon tile `44.dp`, dan aksen halus header grup—**jangan** membanjiri seluruh kartu dengan warna grup.

### 3.2 Empat Mode Tema & Tangga Surface Container

| Mode | Kanvas (`background`) | Kartu Utama (`surface`) | Inset (`surfaceContainerLow`) | Border (`outline`) | Catatan Khusus |
|---|---|---|---|---|---|
| `ThemeMode.SYSTEM` | Mengikuti OS | — | — | — | Default |
| `ThemeMode.LIGHT` | `AlabasterBg #F6F7FB` | `CrispWhite #FFFFFF` | `LightSurfaceLow #F0F2F8` | `#C5CCE0` (`cardBorderAlpha = 0.55f`) | Wajib pakai `contrastSafeAccent` |
| `ThemeMode.DARK` | `DeepObsidian #11141C` | `SlateSurface #171C26` | `#151923` | `#333D52` | Default studio nyaman di ruang redup |
| `ThemeMode.AMOLED` | `PureBlack #000000` | `AmoledCard #0B0E14` | `#080B10` | `#2A3346` | Tetap menjaga `#000000` walau `dynamicColor = true` |

### 3.3 Hukum Kontras WCAG AA & `contrastSafeAccent`
Warna aksen cerah seperti `GroupImage (#06B6D4)`, `GroupConverter (#10B981)`, dan `GroupCore (#FF6B2C)` memiliki rasio kontras rendah (`< 3.0:1`) jika dipakai langsung sebagai warna teks/ikon kecil di atas latar terang (`CrispWhite` atau tint `0.12f`).
- **Aturan Keras:** Setiap rendering teks kecil, badge, atau ikon berbasis `ToolGroup.accentColor()` **WAJIB** memanggil:
  ```kotlin
  val safeAccent = HariMuThemeTokens.semantic.contrastSafeAccent(accent)
  ```
- Saat ini `GroupGridCard` di `HomeScreen.kt` dan `ResultItemRow` di `ToolResultComponents.kt` masih memakai warna grup mentah; agent berikutnya wajib memigrasikannya ke `contrastSafeAccent(accent)`.

### 3.4 Dynamic Color Guardrail
- `dynamicColor` bersifat opt-in (`settings.dynamicColor = false` secara default) pada Android 12+ (`Build.VERSION_CODES.S`).
- Ketika `dynamicColor = true` dan `ThemeMode.AMOLED` aktif, `Theme.kt` mempertahankan `PureBlack` & `AmoledCard` serta menjaga `HariMuSemanticColors` (`success`, `warning`, `info`) tetap kontras dan terpisah dari seed wallpaper pengguna.

---

## 4. Tipografi Presisi & Skala Aksesibilitas (`Tokens.kt`)

Semua slot `AppTypography` diisi oleh dua typeface lokal di `res/font/`:

1. **`PlusJakartaSans` (`plus_jakarta_sans.ttf`) — UI & Hierarki Konten:**
   - `displayLarge` (`34.sp`, `-0.6.sp`) s/d `displaySmall` (`26.sp`, `-0.4.sp`) → Angka statistik hero/studio saja (bukan judul biasa).
   - `headlineLarge` (`24.sp`) s/d `headlineSmall` (`19.sp`) → Header utama hub/dialog.
   - `titleLarge` (`17.sp` Bold) → Judul `HariMuTopBar` dan section utama.
   - `titleMedium` (`15.sp` SemiBold) → Judul `ToolCard`, `GroupGridCard`, sub-bagian.
   - `titleSmall` (`13.sp` SemiBold) → Header panel/inspector (`HariMuSectionCard`).
   - `bodyLarge` (`15.sp`), `bodyMedium` (`13.5.sp`), `bodySmall` (`12.sp`) → Deskripsi, pesan error, petunjuk.
   - `labelLarge` (`14.sp` SemiBold) & `labelMedium` (`12.sp` SemiBold) → Label tombol dan `FilterChip`.
2. **`JetBrainsMono` (`jetbrains_mono.ttf` + `tnum`) — Telemetri & Identitas:**
   - `labelSmall` (`10.5.sp` Medium) → `EngineBadge`, `CapabilityBadge`, metadata mikro.
   - `MonoTextStyle` (`12.sp` Medium) → `ToolIdBadge` (`VID-01`), timer rekaman, persen progres (`78%`), path/nama file, ukuran byte, hash SHA-256.

### Aturan Ketahanan `fontScale >= 1.5f`
- Jangan pernah memberi `height(dp)` tetap pada baris tombol, chip, atau kartu berisi teks; gunakan `heightIn(min = ...)` dan `widthIn(min = ...)`.
- Gunakan `FlowRow` untuk barisan tombol hasil (`ResultCard`, `ResultActionBar`), chip filter yang membungkus, dan barisan badge (`ToolCard`).
- Gunakan `maxLines` + `TextOverflow.Ellipsis` pada elemen grid padat (`GroupGridCard`), tetapi bebaskan multiline pada deskripsi parameter dan pesan error.

---

## 5. Token Sistem Terpadu (`HariMuThemeTokens`)

Seluruh screen fitur wajib mengambil token melalui `HariMuThemeTokens.*`:

| Kelompok Token | Accessor | Nilai Resmi |
|---|---|---|
| **Spacing** | `HariMuThemeTokens.spacing` | `xxs=4.dp`, `xs=8.dp`, `sm=12.dp`, `md=16.dp`, `lg=24.dp`, `xl=32.dp`, `xxl=40.dp` |
| **Layout** | `HariMuThemeTokens.layout` | `formMaxWidth=640.dp`, `catalogMaxWidth=760.dp`, `dashboardMaxWidth=1040.dp`, `compactScreenPadding=16.dp`, `mediumScreenPadding=24.dp`, `cardBorderAlpha=0.55f`, `dividerAlpha=0.25f` |
| **Elevation** | `HariMuThemeTokens.elevation` | `none=0.dp`, `card=0.dp`, `subtle=1.dp`, `bar=2.dp`, `dialog=6.dp`, `sheet=6.dp` |
| **Borders** | `HariMuThemeTokens.borders` | `hairline=1.dp`, `focused=1.5.dp`, `selected=2.dp`, `subtleAlpha=0.42f`, `defaultAlpha=0.62f`, `strongAlpha=0.85f` |
| **Icons** | `HariMuThemeTokens.icons` | `xs=16.dp`, `sm=18.dp`, `md=20.dp`, `lg=24.dp`, `xl=28.dp`, `tile=44.dp`, `emptyContainer=56.dp` |
| **Shapes** | `HariMuThemeTokens.shapes` | `badge=6.dp`, `chip=10.dp`, `iconTile=12.dp`, `control=12.dp`, `card=16.dp`, `container=20.dp`, `dialog=24.dp`, `sheet=24.dp` (top corners) |
| **Controls** | `HariMuThemeTokens.controls` | `minTouchTarget=48.dp`, `buttonMinHeight=48.dp`, `compactButtonMinHeight=40.dp`, `inputMinHeight=52.dp`, `searchBarHeight=52.dp`, `topBarHeight=64.dp` |
| **Motion** | `HariMuThemeTokens.motion` | `fastMs=150`, `standardMs=220`, `emphasizedMs=280` |
| **Semantic** | `HariMuThemeTokens.semantic` | `success*`, `warning*`, `info*`, `favoriteAccent`, `contrastSafeAccent(accent)` |

---

## 6. Arsitektur Layout, Grid & Adaptivitas Multi-Layar

### 6.1 Breakpoints & Canonical Layouts (`WindowSizeClass`)

| Kelas Layar | Chrome Navigasi (`HariMuAppShell`) | Pola Konten Utama | Batas Lebar (`widthIn`) |
|---|---|---|---|
| **Compact Portrait** (`width < 600.dp`, `height >= 480.dp`) | Bottom `NavigationBar` (`SHELL_BOTTOM_NAVIGATION_TAG`) | Single-column (`ToolScreen`, `Catalog`, `History`, `Settings`); Grid 2 kolom di `Home` jika `maxWidth >= 320.dp && fontScale < 1.5f` | `formMaxWidth` (`640.dp`) / `catalogMaxWidth` (`760.dp`) |
| **Compact Landscape** (`heightSizeClass == Compact`) | Left `NavigationRail` **icon-only** (`showLabels = false`, `contentDescription` wajib terisi) | Kontainer **wajib scrollable** (`.verticalScroll` / `LazyColumn`) + tombol aksi tetap terjangkau tanpa kepotong | `catalogMaxWidth` (`760.dp`) |
| **Medium** (`600.dp..839.dp` Foldable/Small Tablet) | Left `NavigationRail` berlabel (`showLabels = true`) | Grid 2 kolom di `Home` & `Catalog`/`Forge`; split-pane opsional untuk `AudioStudio`/`ImageMatrix` | `dashboardMaxWidth` (`1040.dp`) |
| **Expanded** (`width >= 840.dp` Tablet/Desktop/ChromeOS) | Left `NavigationRail` permanen | **Canonical Two-Pane Studio/Tool Layout**: Kiri `0.44f` (Input + Parameter + Run CTA), Kanan `0.56f` (Live Canvas / Media Preview + Progress + ResultCard) | `dashboardMaxWidth` (`1040.dp`) untuk hub; full fluid split-pane untuk editor/studio |

### 6.2 Disiplin `WindowInsets` & `Scaffold`
- `HariMuAppShell` menerapkan `contentWindowInsets = WindowInsets.safeDrawing` dan meneruskan `innerPadding` ke `NavHost` melalui `.padding(innerPadding).consumeWindowInsets(innerPadding)`.
- **Aturan Sub-Screen:** Setiap sub-screen (`ToolScreen`, `CatalogScreen`, Studio screens, Nusantara screens) yang merender `Scaffold` internal **WAJIB** menggunakan `contentWindowInsets = WindowInsets(0)` apabila berada di dalam shell `MainActivity`, atau memastikan hanya `imePadding()` / `navigationBarsPadding()` ditambahkan pada sheet/modal terpisah—supaya tidak terjadi celah kosong ganda di atas/bawah layar.
- **Layar Non-Scrollable Dilarang:** Form atau studio seperti `BackupScreen`, `ScreenRecorderScreen`, dan `VoiceRecorderScreen` harus selalu bisa di-scroll pada tinggi layar kecil/landscape.

---

## 7. Arsitektur Navigasi & Tata Kelola Pemanggilan Alat (182 Tools)

Kelemahan aplikasi multi-tool besar adalah pengguna tersesat di antara "Katalog Daftar Panjang" vs "Studio Khusus". HariMu menyelesaikannya dengan **3 Lapisan Navigasi & Pemanggilan Alat yang Jelas**:

### 7.1 Lapisan 1 — Shell Persisten & Top Bar Kontekstual
`TopLevelDestination` memiliki 5 tab utama (`HOME`, `CATALOG`, `FAVORITES`, `HISTORY`, `SETTINGS`) + hub sekunder `NUSANTARA` (`showInNavigationBar = false`, dibuka dari kartu Home).
- **Aturan Top Bar Kontekstual (Target Upgrade P14+):** `HariMuTopBar` di shell top-level harus menampilkan **judul destinasi aktif** (`Beranda` / `Katalog` / `Favorit` / `Riwayat & Job` / `Pengaturan`), bukan statis `"HariMu"` di semua tab, serta menyediakan aksi kontekstual di slot `actions` (mis. tombol pintas *Job Center* atau *Bersihkan Riwayat*).
- **Deep Navigation Hygiene:** Rute top-level (`SettingsRoute`, dll.) dari dalam tool selalu memakai `navController.navigateToTopLevel(...)` agar backstack tidak menumpuk duplikat tab di atas detail screen.

### 7.2 Lapisan 2 — Hierarki `HomeScreen` (Zero-Confusion Launcher)
Urutan vertikal `HomeScreen` harus ketat menjaga *scannability* dan *above-the-fold clarity*:
1. **Studio Masthead (`HeroStudioBanner`):** Tinggi ringkas (`148.dp..168.dp`), `img_hero_studio` + scrim horizontal terarah + badge kapabilitas monospasi + judul 1–2 baris yang padat. Jangan memperbesar tinggi hero sehingga mendorong search bar keluar viewport layar kecil.
2. **Unified Instant Search (`HariMuSearchBar`):**
   - Menyatukan pengalaman pencarian dengan `CatalogScreen`.
   - Saat `searchQuery` kosong: tampilkan tombol/chip pintas ke **Semua 182 Alat (`CatalogRoute`)** dan shortcut studio populer agar pengguna tidak bingung mana pintu ke seluruh katalog.
   - Saat `searchQuery` terisi: tampilkan hasil instan per `ToolRowCard` dengan pencocokan **ID (`VID-01`), judul ID/EN, nama engine (`FFmpeg`, `Media3`, `PdfBox`), dan sinonim intent**; sertakan tombol *"Filter lanjut di Katalog"* saat hasil `> 8`.
3. **Conditional Job Alert Strip (`HomeJobCenterBanner`):** Hanya dirender ketika `activeJobCount > 0 || attentionJobCount > 0`.
4. **Resume Carousel (`Terakhir Dipakai`):** `LazyRow` horizontal dengan kartu `196.dp`, menampilkan `ToolIdBadge` + judul 1 baris + ringkasan 2 baris. Tap langsung melompat ke tool atau studio terkait.
5. **7 Domain Cards (`GroupGridCard`):**
   - Konsisten menampilkan: `iconTile` (`40–44.dp`, sudut `12.dp`) + badge jumlah tool (`ToolRegistry.countByGroup`) + judul grup + subjudul + chevron.
   - Bedakan secara visual/aksesibilitas apakah kartu membuka **Dedicated Workbench Hub** (`AUDIO` → `AudioForge`, `PDF` → `PdfVault`, `CONVERTER` → `UniversalConverter`, `NUSANTARA` → `NusantaraHub`) atau **Domain Catalog + Studio Shortcuts** (`CORE`, `VIDEO` → dengan pintas langsung ke `VideoForge` & `VideoEditor`, `IMAGE` → dengan pintas langsung ke `ImageForge` & `ImageMatrix`).

### 7.3 Lapisan 3 — Tiga Pola Eksekusi Pemanggilan Alat
Setiap pemanggilan alat mengikuti salah satu dari 3 grammar berikut (satu sumber implementasi di `ToolRegistry`, tanpa duplikasi logika):

| Pola Eksekusi | Kapan Dipakai | Alur Interaksi Standar |
|---|---|---|
| **Pola A — Quick Deterministic Tool (`ToolScreen`)** | Tool parameter-driven (`VID-01`..`VID-30`, `IMG-*`, `PDF-*`, `CNV-*`, dll.) | **4-Step Vertical / 2-Pane Flow:** (1) Header Badge + Favorit + Deskripsi 1 kalimat → (2) `FilePickerField` / Input Zone + Pratinjau Input Kontekstual → (3) `ToolParameterPanel` tervalidasi real-time (`ParamValidators`) → (4) `HariMuPrimaryButton` penuh + `ProgressSheet` / `ToolResultCard` |
| **Pola B — Interactive Workbench Studio** | `VideoEditorScreen`, `AudioStudioScreen`, `EqualizerScreen`, `ImageMatrixScreen`, `PdfStudioScreen`, `VoiceRecorderScreen`, `ScreenRecorderScreen`, `KeyVaultScreen` | **Canvas-First Workbench:** Area kanvas/timeline/waveform mendominasi bagian atas (`40–55%` tinggi viewport portrait atau pane kanan di tablet), dikelilingi kontrol presisi berjarak aman (`>= 48.dp`) tanpa scroll horisontal yang bertabrakan dengan gestur sistem |
| **Pola C — Asynchronous Job & Result Chaining** | Job media/ML berat (`> 3 detik`) lewat `WorkManager` | Menampilkan `ProgressSheet` dengan persentase `MonoTextStyle`, pesan tahapan nyata, tombol `Batalkan` (`CancellationException` + cleanup temp), dan notifikasi foreground. Saat selesai, `ToolResultCard` menawarkan **Rantai Aksi Next-Step**: `Buka`, `Bagikan`, `Simpan (SAF/MediaStore)`, `Unggah Cloud`, serta *Lihat di Job Center* (`HistoryRoute`). |

---

## 8. Blueprint Komponen & Visual Craft Anti-Slop

### 8.1 Hierarki Permukaan (`DesignSurfaces.kt`)
- **`HariMuResponsiveContainer`:** Pembungkus luar berpusat dengan `widthIn(max = maxWidth)`. Gunakan `formMaxWidth` (`640.dp`) untuk `ToolScreen`/`Settings`/`Recorder`/`Backup`, `catalogMaxWidth` (`760.dp`) untuk `Catalog`/`History`/`Favorites`, dan `dashboardMaxWidth` (`1040.dp`) untuk `HomeScreen`.
- **`HariMuSectionCard`:** Kontainer grup utama (`surface`, border `hairline 1.dp` `outline @ 0.55f`, radius `20.dp`, padding internal `16.dp`, jarak anak `12.dp`).
- **`HariMuInsetPanel`:** Sub-kontainer di dalam `HariMuSectionCard` (`surfaceContainerLow`, **tanpa border**, radius `16.dp`, padding `12.dp`). Contoh: daftar file terpilih di `FilePickerField`, detail ringkas hasil, atau preview teks read-only.
- **`HariMuDivider`:** Pembatas internal tipis (`1.dp`, `outline @ 0.25f`).

### 8.2 Precision Instrument Details (Sentuhan Khas $700M Tanpa Bloat)
- **Custom Vector/Canvas Studio Guides (Ringan, 0 Bitmap Overhead):** Pada studio visual (`AudioStudioScreen`, `VoiceRecorderScreen`, `VideoEditorScreen`, `ImageMatrixScreen`, `PerspectivePointsCard`), gunakan `Canvas` / `Modifier.drawBehind` untuk *precision tick marks*, *waveform amplitude bars*, *db meter scale*, dan *aspect-ratio safe guides* dengan warna `outlineVariant @ 0.35f`. Ini menghadirkan aura perangkat lunak kreatif kelas atas dengan biaya GPU mendekati nol.
- **Unified Action Bar (`ResultCard` & `ResultActionBar`):**
  - Aksi sekunder (`Open`, `Share`, `Details`) memakai `HariMuSecondaryButton` (`OutlinedButton`, `>= 48.dp`).
  - Aksi final (`Save`) memakai `HariMuPrimaryButton` berwarna `semantic.success` + `semantic.onSuccess`.
  - Aksi destruktif (`Delete`) memakai `HariMuDestructiveButton` atau dialog konfirmasi `HariMuAlertDialog(isDestructive = true)`—**jangan** memakai dialog konfirmasi untuk aksi non-destruktif seperti membuka tool atau menyalin teks.
- **Standar State UI (Tidak Boleh Kosong/Polos):**
  - *Loading:* `ProgressSheet` determinate (`0–100%` dalam `MonoTextStyle`) bila engine melaporkan progres, atau indeterminate bersih dengan tombol batal yang langsung responsif.
  - *Empty:* `EmptyState` (ikon `56.dp` container + judul singkat + subjudul + 1 tombol aksi opsional bila relevan).
  - *Error:* `ErrorState` (`errorContainer` + border `error` + pesan manusiawi yang dapat dipahami + tombol `Coba Lagi` bila dapat diulang).
  - *Missing File Guard:* Jika output riwayat terhapus dari disk, tampilkan banner peringatan `R.string.job_file_missing` tanpa tombol aksi file yang rusak (*no dead-end UI*).

---

## 9. Standar Performa Jetpack Compose ("Perfect 60/120fps" Engineering Contract)

Setiap layar atau komponen baru maupun hasil refaktor **WAJIB** lulus audit performa berikut:

1. **Zero Main-Thread I/O During Composition or Callbacks:**
   - **Dilarang** memanggil `File.exists()`, `File.length()`, `File.readText()`, `File.readBytes()`, `storage.copyFileToUri()`, `storage.tempCacheSizeBytes()`, atau `modelManager.listStatuses()` di dalam `remember { ... }`, body `@Composable`, atau lambda `ActivityResultLauncher`.
   - **Wajib** memindahkan semua I/O ke `Dispatchers.IO` melalui `ViewModel` (`viewModelScope`) atau `produceState` dengan batas baca aman untuk preview teks (mis. maks `64 KB` pertama + indikator terpotong untuk file log/teks besar).
   - *Target Fix Teridentifikasi di Repo:* `ToolResultComponents.kt` (`TextResultPreview` line 309 synchronous `readText()`), `SettingsScreen.kt` (synchronous disk/model scan in `remember`), serta audit `KeyVaultScreen`/`BackupScreen`/`EqualizerScreen` SAF callbacks.
2. **Isolasi Rekomposisi untuk Tick Frekuensi Tinggi:**
   - Pada pemutar media (`AudioResultPreview`, `VideoEditorScreen`, `AudioStudioScreen`, `VoiceRecorderScreen`) dan drag handle (`PerspectivePointsCard`): **jangan** mengoper nilai float posisi yang berubah tiap 16–120ms ke root layar.
   - Bungkus komponen pembaca ke composable kecil tersendiri, atau baca state posisi hanya di fase Layout/Draw via `Modifier.offset { IntOffset(...) }`, `Modifier.graphicsLayer { ... }`, atau `Canvas { ... }` dengan `State<Float>` / lambda getter `() -> Float`.
   - *Target Fix Teridentifikasi di Repo:* `AudioStudioScreen.kt` mencampur `px` (`horizontalScrollState.value`) dengan `.dp` untuk playhead; ubah ke koordinat konsisten berbasis `LocalDensity` atau gambar langsung di `Canvas` yang mengikuti scroll.
3. **Compose Stability & Immutable Models:**
   - Beri anotasi `@Immutable` pada seluruh model state UI, parameter presentasi, dan objek token; hindari mengirim `List` / `Map` mutable mentah tanpa pembungkus stabil ke composable list item.
   - Selalu gunakan `.collectAsStateWithLifecycle()` (bukan `.collectAsState()`) pada `AudioStudioScreen`, `ImageMatrixScreen`, dan seluruh layar berbasis Flow.
4. **Lazy List & Media Memory Hygiene:**
   - Setiap `LazyColumn`, `LazyRow`, dan `LazyVerticalGrid` **wajib** mendefinisikan `key = { ... }` deterministik **dan** `contentType = { ... }` untuk memaksimalkan reuse slot Compose (termasuk pada `HomeScreen`, `CatalogScreen`, `HistoryScreen`, `FavoritesScreen`, `SettingsScreen`).
   - Dilarang melakukan alokasi `Brush`, `Path`, `Paint`, `SimpleDateFormat`, atau `ColorFilter` berulang di dalam loop `LazyColumn` atau `onDraw`; cache dengan `remember(...)` atau `drawWithCache`.
   - Gambar/thumbnail memakai Coil `AsyncImage` dengan batas ukuran kontainer & `ContentScale`; `PdfRenderer` dikelola di `Dispatchers.IO` dengan cache halaman terbatas; `ExoPlayer` wajib di-`release()` dalam `DisposableEffect.onDispose`.
5. **Motion & Hardware Acceleration Budget:**
   - Durasi transisi terkunci di `fastMs=150`, `standardMs=220`, `emphasizedMs=280`.
   - Hindari `animateContentSize()` di dalam item `LazyColumn` yang di-scroll cepat; gunakan hanya pada ekspansi detail terfokus atau dialog/sheet.
   - Hormati preferensi aksesibilitas animasi rendah dan hindari animasi infinite (`rememberInfiniteTransition`) kecuali pada indikator rekaman aktif yang memiliki makna fungsional (*live mic/screen capture*).

---

## 10. Standar Aksesibilitas & Ergonomi Sentuh (Verified Rules)

1. **Aturan Ukuran Sentuh `>= 48.dp` (Compose Semantics Safe):**
   - Material 3 `IconButton` atau `FilterChip` bila diberi `.minimumInteractiveComponentSize().testTag(...)` dapat mengekspos node `testTag` dengan tinggi intrinsik `32–40.dp` di Compose semantics tree.
   - **Standar Kode Wajib HariMu:**
     - Pada `IconButton`: `.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`
     - Pada `FilterChip` / `Button` / `OutlinedButton` / `TextButton`: `.heightIn(min = 48.dp)`
     - Letakkan `.testTag(...)` pada modifier yang sama atau setelah modifier dimensi agar test aksesibilitas & TalkBack melihat bounds `>= 48.dp`.
2. **Semantics Atomik & Content Descriptions:**
   - `ToolCard`: `Modifier.clickable(role = Role.Button)` + label tombol favorit terpisah `R.string.cd_toggle_favorite`.
   - `ParamToggle`: `Modifier.toggleable(role = Role.Switch)` pada satu baris penuh (`Switch(onCheckedChange = null)`) agar TalkBack membacakan label + status switch sebagai satu kontrol atomik.
   - Semua `IconButton` dan `Image` non-dekoratif wajib punya `contentDescription` dari `strings.xml` (`values/` + `values-en/`). Ikon dekoratif wajib `contentDescription = null`.
3. **Navigasi Keyboard & Urutan Fokus:**
   - `HariMuSearchBar` memakai `ImeAction.Search`; form input memakai `ImeAction.Next`/`Done`.
   - Screen sekunder atau modal custom wajib mendukung tombol Back sistem (`BackHandler`) menuju induk yang wajar.

---

## 11. Peta Eksekusi & Prioritas Upgrade untuk Agent Berikutnya

Untuk membawa seluruh frontend HariMu ke standar spesifikasi di atas secara bertahap dan aman (tanpa regresi pada 182 tool), ikuti urutan prioritas berikut:

1. **Fase A — Polish Kontras, Token & Performa `core/design` + `HomeScreen` + `HistoryScreen`:**
   - Migrasi `GroupGridCard` (`HomeScreen.kt`) dan `ResultItemRow` (`ToolResultComponents.kt`) ke `HariMuThemeTokens.semantic.contrastSafeAccent(accent)` + token `borders.hairline` / `shapes.*`.
   - Ubah `TextResultPreview` (`ToolResultComponents.kt`) agar membaca file secara asinkron di `Dispatchers.IO` via `produceState` dengan batas aman `<= 64 KB`.
   - Tambahkan `key` dan `contentType` pada seluruh item `LazyColumn`/`LazyRow` di `HomeScreen.kt`, `FavoritesScreen.kt`, `HistoryScreen.kt`, `CatalogScreen.kt`.
   - Terapkan `.heightIn(min = 48.dp)` pada `FilterChip` di `HistoryScreen.kt` dan `.sizeIn(minWidth = 48.dp, minHeight = 48.dp)` pada `ResultItemRow` action buttons.
2. **Fase B — Penyatuan Shell, Search & Discovery:**
   - Berikan judul & aksi kontekstual pada `HariMuTopBar` untuk 5 tab `TopLevelDestination`.
   - Tambahkan shortcut jelas dari `HomeScreen` ke *"Semua 182 Alat"* (`CatalogRoute()`) dan filter engine di hasil pencarian Home.
   - Refactor `HomeScreen` agar memakai `HariMuSearchBar` (tetap mempertahankan `testTag("home_search_input")`).
3. **Fase C — Hardening Performa & Lokalitas Studio + Settings:**
   - Pindahkan scan disk `SettingsScreen.kt` dari `remember` sinkron ke `LaunchedEffect` / `SettingsViewModel` pada `Dispatchers.IO`.
   - Perbaiki kalkulasi koordinat `px` vs `dp` playhead di `AudioStudioScreen.kt`, ganti `.collectAsState()` → `.collectAsStateWithLifecycle()` di `AudioStudioScreen.kt` & `ImageMatrixScreen.kt`.
   - Gantikan sisa string hardcoded di `ImageMatrixScreen`, `AudioStudioScreen`, `BackupScreen`, `CloudConnectionsCard`, `FilterPreviewCard`, dan `PdfPagePickerCard` ke `res/values/strings.xml` + `res/values-en/strings.xml`.
   - Terapkan *Canonical Two-Pane Layout* pada `Width >= 840.dp` di `ToolScreen` dan Studio utama.

### Definition of Done (DoD) per Perubahan UI
- [ ] Tidak ada hardcode hex warna, ukuran font non-`sp`, atau string UI mentah di luar `strings.xml`.
- [ ] Seluruh kontrol interaktif memiliki bounds semantics `>= 48.dp` dan lolos inspeksi `fontScale = 1.5f` + `Light/Dark/AMOLED`.
- [ ] Zero I/O sinkron di dalam komposisi atau callback SAF main-thread.
- [ ] `LazyColumn`/`LazyRow` memakai `key` stabil dan `contentType`.
- [ ] Build & test hijau: `gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
