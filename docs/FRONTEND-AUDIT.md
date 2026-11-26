# HariMu — Frontend Reconnaissance & UI Audit Baseline (Phase P0)

> **Phase:** P0 — Frontend Reconnaissance & UI Audit (Read-Only Baseline)  
> **Directive Anchor:** `/ui_agent.md` (*HariMu — Frontend Master Directive*)  
> **Codebase Snapshot:** 182 tools across 7 `ToolGroup`s (`CORE` 16, `VIDEO` 30, `AUDIO` 28, `IMAGE` 47, `PDF` 21, `CONVERTER` 28, `NUSANTARA` 12), 30 `@Serializable` Compose Navigation routes, 8 dedicated Studio/Utility screens, 13 Nusantara screens, 1 universal `ToolScreen` workspace.  
> **Constraint Status:** Audit-only. Zero application source files, Gradle configurations, or resources were modified.

---

## 1. Executive Summary

HariMu already has a working, real-engine multi-tool architecture: 182 registered tools in `ToolRegistry` (`ToolRegistryTest.kt:15`), WorkManager background execution (`ToolViewModel.kt:164-188`), custom interactive media/image/PDF preview cards, Material 3 `HariMuTheme` with Light/Dark/AMOLED/Dynamic Color modes (`Theme.kt:27-32, 253-291`), and localized `values/` + `values-en/` strings.

However, frontend quality is **uneven across architectural eras**. While the core tabs (`HomeScreen`, `CatalogScreen`, `HistoryScreen`, `FavoritesScreen`, `SettingsScreen`) and `VideoEditorScreen` partially adopt `HariMuThemeTokens`, `strings.xml`, `minimumInteractiveComponentSize()`, and width constraints, large parts of the dedicated Studios, Nusantara screens, and `ToolScreen` preview cards bypass the design system, hardcode strings (including mixed/untranslated Indonesian labels inside English mode), execute synchronous File I/O on the Main thread during SAF/cache operations, clip on compact/landscape viewports due to missing scroll containers, and lack adaptive layouts on tablets/foldables.

This document establishes the verified P0 implementation baseline for **P1 (Design System)** through **P5 (ToolScreen)** and subsequent Studio/Nusantara follow-ups.

---

## 2. Current Frontend Architecture & UI Inventory

### 2.1 Navigation & Application Shell (`MainActivity.kt`)
- **Entry & Locale Switching (`MainActivity.kt:146-193`):** Uses `enableEdgeToEdge()`, `calculateWindowSizeClass(this)`, and overrides `LocalConfiguration`, `LocalContext`, and `LocalResources` via `CompositionLocalProvider` to support runtime `id` ↔ `en` language switching.
- **Top-Level Shell (`MainActivity.kt:222-356`):**
  - 5 top-level tabs: `HomeRoute`, `NusantaraRoute`, `HistoryRoute`, `FavoritesRoute`, `SettingsRoute`.
  - **Compact (`widthSizeClass == Compact`):** `Scaffold` with a static `TopAppBar(title = "HariMu")` and bottom `NavigationBar`.
  - **Medium/Expanded (`widthSizeClass != Compact`):** Outer `Row` places a `NavigationRail` beside the `Scaffold`, hiding both `NavigationRail` and `TopAppBar`/`NavigationBar` whenever `isTopLevel == false`.
- **Secondary Routes (`MainActivity.kt:110-135, 395-499`):** `CatalogRoute(groupName?)`, `ToolRoute(toolId)`, 8 studios (`VoiceRecorderRoute`, `ScreenRecorderRoute`, `VideoEditorRoute`, `ImageMatrixRoute`, `EqualizerRoute`, `AudioStudioRoute`, `KeyVaultRoute`, `BackupRoute`), 2 utility routes (`PrayerTimesRoute`, `AreaLookupRoute`), 11 Nusantara sub-routes (`EarthquakeRoute`..`PesantrenRoute`), and `OpenSourceLicensesRoute`.
  - **Note:** Every secondary screen instantiates its own **nested `Scaffold`** inside `MainActivity`'s `Box(Modifier.padding(innerPadding).consumeWindowInsets(innerPadding))`.

### 2.2 Design System & Shared UI (`core/design/`)
- **`Theme.kt`:**
  - `HariMuSpacingTokens` (`xxs=4dp, xs=8dp, sm=12dp, md=16dp, lg=24dp, xl=32dp`) and `HariMuLayoutTokens` (`formMaxWidth=640dp, catalogMaxWidth=760dp, dashboardMaxWidth=1040dp, cardBorderAlpha=0.55f, dividerAlpha=0.25f`).
  - `HariMuColors`: brand colors (`ElectricOrange`, `TealEmerald`), dark/AMOLED surfaces (`DeepObsidian`, `SlateSurface`, `PureBlack`, `AmoledCard`), hero scrims, and 7 `ToolGroup` accents (`GroupCore`..`GroupNusantara`).
  - Bundled fonts (`PlusJakartaSans`, `JetBrainsMono`) + `AppTypography` + `AppShapes` (`small=10dp, medium=16dp, large=22dp, extraLarge=28dp`).
- **`Components.kt`:** Exports `ToolIdBadge`, `EngineBadge`, `HariMuSectionCard`, `ToolCard`, `ToolRowCard`, `GroupHeader`, `ParamSlider`, `ParamDropdown`, `ParamToggle`, `FilePickerField`, `ProgressSheet`, `ResultCard`, `EmptyState`, `EmptyStateCard`, `ErrorState`, `ModelDownloadDialog`.
- **`ComponentPreviews.kt`:** 8 component-level `@Preview` functions (`PreviewToolCard`, `PreviewGroupHeader`, `PreviewParamControls`, `PreviewFilePickerField`, `PreviewProgressSheet`, `PreviewResultCard`, `PreviewEmptyAndErrorStates`, `PreviewModelDownloadDialog`). No screen-level `@Preview` functions exist except `EqualizerScreen.kt` (check tail) and `WaveformView` in `AudioPreviewCards.kt:488-508`.

### 2.3 Adaptive Layout & State Management Matrix

| Screen / Module | State Holder | Lifecycle-Aware Flow? | Scrollable Container? | Width Constraint (`widthIn`) | Localization Method |
|---|---|---|---|---|---|
| `MainActivity.kt` | `AppSettings` Flow + `WorkspaceRepository` | Yes (`collectAsStateWithLifecycle`) | Host shell | Outer `Row` + `NavigationRail` | `strings.xml` |
| `HomeScreen.kt` | Local `searchQuery` | N/A (stateless props) | `LazyColumn` + `LazyRow` | `dashboardMaxWidth` (1040dp), 1/2/3 col grid | `strings.xml` |
| `CatalogScreen.kt` | Local `selectedGroup`, `query` | N/A (stateless props) | `LazyColumn` + `LazyRow` | `catalogMaxWidth` (760dp) | `strings.xml` |
| `HistoryScreen.kt` | `WorkspaceRepository` Flow + `pendingSave` | Yes (`collectAsStateWithLifecycle`) | `LazyColumn` | `catalogMaxWidth` (760dp) | `strings.xml` |
| `FavoritesScreen.kt` | Stateless props | N/A | `LazyColumn` | `catalogMaxWidth` (760dp) | `strings.xml` |
| `SettingsScreen.kt` | Local `remember` disk states + `settings` prop | N/A (sync init!) | `LazyColumn` | `formMaxWidth` (640dp) | `strings.xml` |
| `CloudConnectionsCard.kt` | Local `remember` + `PendingOAuth` singleton | Manual `LaunchedEffect` | Inside `SettingsScreen` | Inherits `640dp` | Hardcoded `if (isEnglish)` |
| `OpenSourceLicensesScreen.kt` | Static list | N/A | `LazyColumn` | `catalogMaxWidth` (760dp) | `strings.xml` + static EN/ID |
| `ToolScreen.kt` + `ToolViewModel.kt` | `ToolViewModel` + local `MediaPlayer`/UI flags | Yes (`collectAsStateWithLifecycle`) | `Column.verticalScroll` | `formMaxWidth` (640dp) | Mix `strings.xml` & hardcoded |
| `VideoEditorScreen.kt` | `VideoEditorViewModel` | Yes (`collectAsStateWithLifecycle`) | `Column.verticalScroll` | `760.dp` (hardcoded, not token) | `strings.xml` |
| `AudioStudioScreen.kt` | `AudioStudioViewModel` | **No (`collectAsState()`)** | `LazyColumn` + horizontal scroll | 2-pane (`isWide >= 600dp`) | Hardcoded `if (isEnglish)` |
| `ImageMatrixScreen.kt` | `ImageMatrixViewModel` | **No (`collectAsState()`)** | `Column.verticalScroll` | **None (full bleed)** | **Indonesian-only hardcoded** + partial `if (isEnglish)` |
| `EqualizerScreen.kt` | **No ViewModel** (local `remember` + `MediaJob`) | Only `settingsFlow` | `Column.verticalScroll` | `680.dp` (hardcoded, not token) | `strings.xml` |
| `KeyVaultScreen.kt` | Direct `workspace` Flow + local `remember` | Yes (`collectAsStateWithLifecycle`) | `LazyColumn` | **None (full bleed)** | Hardcoded `if (isEnglish)` + `"Back"`/`"Delete"` |
| `BackupScreen.kt` | Local `remember` + `scope.launch` | N/A | **None (`Column` non-scrollable!)** | **None (full bleed)** | Hardcoded `if (isEnglish)` + ID-only toasts |
| `ScreenRecorderScreen.kt` | `ScreenRecordingController` + local `remember` | Yes (`collectAsStateWithLifecycle`) | **None (`Column` non-scrollable!)** | **None (full bleed)** | Hardcoded `if (isEnglish)` + `"Mikrofon"` |
| `VoiceRecorderScreen.kt` | `VoiceRecordingController` + local `remember` | Yes (`collectAsStateWithLifecycle`) | **None (`Column` non-scrollable!)** | **None (full bleed)** | Hardcoded `if (isEnglish)` + `"Back"`/`"Record"` |
| `PrayerTimesScreen.kt` | `PrayerTimesViewModel` | Yes (`collectAsStateWithLifecycle`) | `Column.verticalScroll` | **None (full bleed)** | Hardcoded `if (isEnglish)` + `"Back"` |
| `AreaLookupScreen.kt` | `AreaLookupViewModel` | Yes (`collectAsStateWithLifecycle`) | `LazyColumn` | **None (full bleed)** | Hardcoded `if (isEnglish)` + `"Back"` |
| `NusantaraHubScreen.kt` & 11 sub-screens | Feature `ViewModel`s | Yes (`collectAsStateWithLifecycle`) | `LazyColumn` (except `NusantaraColumn` lacks scroll!) | **None (full bleed)** | Hardcoded `if (isEnglish)` + `"Back"` |

### 2.4 Existing UI Test Coverage (`app/src/test/java/com/harimu/app/`)
- `ToolRegistryTest.kt` (104 lines): Verifies all 182 tool IDs across 7 groups, unique IDs, non-blank EN/ID titles & descriptions, and 0 converter stubs.
- `HariMuUiAndNavigationTest.kt` (490 lines): Robolectric Compose semantics tests covering compact bottom bar navigation (`nav_*`), expanded `NavigationRail` 48dp touch target heights (`w840dp-h900dp`), `SettingsScreen` -> `OpenSourceLicensesScreen` backstack, `HomeScreen` group card -> `CatalogScreen` filter chips, `HistoryScreen` long-title delete button bounds, and `ToolScreen` (`IMG-06`) + `PerspectivePointsCard` basic semantics.
- **Coverage Gaps:** No UI tests exist for `FavoritesScreen`, `NusantaraHubScreen` or any Nusantara detail screen, `PrayerTimesScreen`, `AreaLookupScreen`, `ToolScreen` parameter rendering/execution/result states, `VideoEditorScreen`, `ImageMatrixScreen`, `ScreenRecorderScreen`, `VoiceRecorderScreen`, `KeyVaultScreen`, `BackupScreen`, dark/AMOLED contrast, or accessibility `fontScale >= 1.5f` layout overflow.

---

## 3. Classified Findings

### P0 — Critical Functionality, Layout Clipping, or Main-Thread Hazards

#### [F-P0-01] Non-Scrollable Studio & Utility Columns Clip Content on Small Phones, Landscape, and Large Font Scales
- **Severity:** `P0`
- **Category:** Layout Issue / Accessibility (`fontScale`)
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/studios/ScreenRecorderScreen.kt:179-394`
  - `app/src/main/java/com/harimu/app/feature/studios/VoiceRecorderScreen.kt:166-367`
  - `app/src/main/java/com/harimu/app/feature/studios/BackupScreen.kt:98-158`
  - `app/src/main/java/com/harimu/app/feature/nusantara/NusantaraUi.kt:133-143` (`NusantaraColumn`)
- **Relevant Composables:** `ScreenRecorderScreen`, `VoiceRecorderScreen`, `BackupScreen`, `NusantaraColumn`
- **Evidence:**
  - `ScreenRecorderScreen.kt:179-185` uses `Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp))` with **no `.verticalScroll(...)`**, while rendering an info banner, 4 resolution chips, 3 fps chips, bitrate `ParamSlider`, 4 audio source chips, 2 container chips, floating-controls switch, Start/Pause buttons, and post-recording Open/Share buttons.
  - `VoiceRecorderScreen.kt:166-173` uses `Column(..., verticalArrangement = Arrangement.SpaceBetween)` without `.verticalScroll(...)`, stacking timer text, 150dp `Canvas`, 3 format chips, 2 sample-rate chips, status text, 84dp circular record button, and Open/Share row.
  - `BackupScreen.kt:98-104` uses a non-scrolling `Column`.
  - `NusantaraUi.kt:133-143` has KDoc `"/** Wadah kolom standar: padding 16dp, jarak antar blok 12dp, bisa di-scroll. */"`, yet `NusantaraColumn` omits `.verticalScroll(rememberScrollState())`.
- **Root Cause:** Fixed vertical `Column`s authored without scroll containers or viewport-height checks.
- **Proposed Solution:** Add `.verticalScroll(rememberScrollState())` (with `Arrangement.spacedBy(...)` instead of `SpaceBetween` on `VoiceRecorderScreen` or `BoxWithConstraints` adaptive height) inside constrained width containers (`widthIn(max = layout.formMaxWidth)`). Fix `NusantaraColumn` to match its KDoc.
- **Impact:** Action buttons (Start/Stop/Save/Restore) become unreachable/clipped on 360×640dp devices, split-screen, landscape orientation, or `fontScale >= 1.3`.
- **Risk:** Low when fixed; ensure `VoiceRecorderScreen` preserves a centered layout on tall phones via `Modifier.fillMaxHeight()` or `Spacer(Modifier.weight(1f))` alternatives compatible with `verticalScroll` (e.g., `BoxWithConstraints` `heightIn(min = maxHeight)`).
- **Recommended Phase:** `P1` (shared responsive scrollable container helper) & **Post-P5 Studio/Nusantara Follow-up** (cannot leave Studio screens broken).

#### [F-P0-02] Synchronous File I/O on Main Thread in SAF Export & Vault Pickers (ANR Hazard)
- **Severity:** `P0`
- **Category:** Performance Risk / Main-Thread Blocking
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolScreen.kt:212-218`
  - `app/src/main/java/com/harimu/app/feature/history/HistoryScreen.kt:85-94`
  - `app/src/main/java/com/harimu/app/feature/studios/VideoEditorScreen.kt:147-153`
  - `app/src/main/java/com/harimu/app/feature/studios/EqualizerScreen.kt:156-162`
  - `app/src/main/java/com/harimu/app/feature/studios/BackupScreen.kt:60-66`
  - `app/src/main/java/com/harimu/app/feature/studios/KeyVaultScreen.kt:123-130, 209-218, 252-263`
- **Relevant Composables:** `ToolScreen`, `HistoryScreen`, `VideoEditorScreen`, `EqualizerScreen`, `BackupScreen`, `KeyVaultScreen`
- **Evidence:**
  - In `ToolScreen.kt:215`, `HistoryScreen.kt:90`, `VideoEditorScreen.kt:150`, `EqualizerScreen.kt:159`, and `BackupScreen.kt:63`, `ActivityResultContracts.CreateDocument` callbacks invoke `container.storage.copyFileToUri(...)` **directly on the Main thread** (contrasting with `ToolScreen.kt:845`, which properly dispatches `saveToTreeUri` on `Dispatchers.IO`).
  - In `KeyVaultScreen.kt:214` and `255`, `openDocument` invokes `copyUriToCache(uri, name)` synchronously on the Main thread, and for `VERIFY_FILE` immediately calls `local.readBytes()` synchronously on the Main thread (risking OOM + ANR on large encrypted/signed files).
- **Root Cause:** `ActivityResultLauncher` callbacks run on the Main thread and stream copy calls were placed directly inside the lambda without `scope.launch(Dispatchers.IO)`.
- **Proposed Solution:** Centralize SAF document export/read helpers in a coroutine-backed controller/ViewModel dispatching file copies onto `Dispatchers.IO` with progress/loading feedback; stream-verify files instead of `readBytes()` where possible.
- **Impact:** Exporting large video (`1080p`), multi-page PDF, or `.omni` backups via Android's document picker stalls UI frames or triggers ANR (`>5s` Main-thread block).
- **Risk:** Medium — ensure `pendingExportFile` is captured locally before coroutine launch so state doesn't mutate mid-copy.
- **Recommended Phase:** `P5` (`ToolScreen`), `P2`/`History` (`HistoryScreen`), & Post-P5 Studio follow-up.

#### [F-P0-03] Synchronous Disk & Model Metadata I/O Inside Composition (`SettingsScreen`)
- **Severity:** `P0`
- **Category:** Performance Risk / State Management
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/settings/SettingsScreen.kt:87-90, 290-294`
- **Relevant Composables:** `SettingsScreen`
- **Evidence:**
  - Lines 87–90 run `container.storage.tempCacheSizeBytes()`, `availableDiskSpaceBytes()`, `container.modelManager.listStatuses()`, and `totalDownloadedBytes()` **synchronously during composition** inside `remember { ... }`.
  - Line 291 executes `container.modelManager.deleteModel(...)` and re-scans disk synchronously on click on the Main thread.
  - Because values are `remember`ed without lifecycle refresh, returning to `SettingsScreen` displays stale temp sizes if a job cleared or wrote temp files.
- **Root Cause:** Absence of a `SettingsViewModel` or `LaunchedEffect` + `Dispatchers.IO` loader.
- **Proposed Solution:** Extract `SettingsViewModel` (or `produceState`/`LaunchedEffect` on `Dispatchers.IO`) to refresh storage/model statuses asynchronously on entry and after mutations.
- **Impact:** Jank on first frame when opening Settings if `temp/` or `models/` contains many files; stale cache counters.
- **Risk:** Low.
- **Recommended Phase:** `P2` / Settings follow-up.

#### [F-P0-04] Broken `DedicatedScreenRoute.SETTINGS` Navigation & `CROSSWORD` Route Mismatch
- **Severity:** `P0`
- **Category:** Navigation Problem
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/MainActivity.kt:243-269, 295-299, 337-341`
  - `app/src/main/java/com/harimu/app/core/toolapi/ToolContract.kt:218, 222`
- **Relevant Composables:** `HariMuRoot`
- **Evidence:**
  - Line 267 handles `DedicatedScreenRoute.SETTINGS -> navController.navigate(SettingsRoute)` without `popUpTo(HomeRoute)` or `launchSingleTop = true`. Unlike top-level tab clicks (`MainActivity.kt:295-299`), navigating via `DedicatedScreenRoute.SETTINGS` stacks `SettingsRoute` on top of another route, yet `isTopLevel` (`236-239`) evaluates `true`, causing the bottom bar / rail to suddenly re-appear on a pushed detail stack.
  - Line 263 maps `DedicatedScreenRoute.CROSSWORD -> navController.navigate(TtsRoute)` (legacy mismatch: Indonesian *Teka-Teki Silang* = TTS, easily confused with Text-to-Speech).
- **Root Cause:** `navigateToStudio` mixes top-level routes (`SettingsRoute`) with modal/pushed studio routes.
- **Proposed Solution:** Route `DedicatedScreenRoute.SETTINGS` using the same top-level tab switch helper (`popUpTo(HomeRoute) { saveState = true }; launchSingleTop = true; restoreState = true`). Document/rename internal navigation clarity for `TtsRoute` (`CrosswordHelperRoute` or explicit KDoc) without altering persisted data or tool IDs.
- **Impact:** Corrupts backstack predictability and bottom-bar visibility when jumping to Settings from a tool.
- **Risk:** Low; verify `HariMuUiAndNavigationTest.kt` continues passing.
- **Recommended Phase:** `P2` (Application Shell & Navigation).

#### [F-P0-05] `AudioStudioScreen` Mixes Pixel (`dp.value`) & DP Offsets for Playhead and Timeline Clips
- **Severity:** `P0`
- **Category:** Layout Issue / Functional Preview Defect
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/studios/AudioStudioScreen.kt:360-370, 653, 682-688`
- **Relevant Composables:** `AudioStudioScreen`, `TrackTimelineRow`
- **Evidence:**
  - Line 360 computes `val timelineWidthPx = (state.totalDurationMs * 0.15f * state.zoomLevel).coerceAtLeast(600f)`.
  - Line 362 subtracts `horizontalScrollState.value` (**pixels**) from `timelineWidthPx * playheadRatio`, then passes it directly to `.offset(x = playheadX.dp)` (**dp**!).
  - Lines 653, 682–688 name variables `clipStartPx` / `clipWidthPx` and pass them as `.dp`, while comparing against `800.dp.value` (`800f`). On `xhdpi`/`xxhdpi`/`xxxhdpi` (`density = 2f..4f`), `horizontalScrollState.value` is in pixels while `.offset(x = ...dp)` multiplies by density, causing the red playhead line to drift completely out of sync with the timeline and scroll position!
- **Root Cause:** Conflation of `px` (`ScrollState.value`) and `dp` units.
- **Proposed Solution:** Convert all timeline positioning to consistent `Dp` using `LocalDensity.current` (`with(density) { horizontalScrollState.value.toDp() }`) or draw the playhead inside the horizontally scrolled container / custom `Canvas`.
- **Impact:** Timeline playhead visually desynchronizes from audio clips on all high-density screens (`2x`–`4x`).
- **Risk:** Low–Medium (self-contained in `AudioStudioScreen`).
- **Recommended Phase:** Post-P5 Studio Follow-up.

---

### P1 — Major Product UX, Discovery, Shell, Localization & Architecture Problems

#### [F-P1-01] Full Tool Catalog ("All 182 Tools") Is Hidden From Top-Level Navigation
- **Severity:** `P1`
- **Category:** Poorly Organized Tool Discovery / Navigation Problem
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/MainActivity.kt:222-239, 367`
  - `app/src/main/java/com/harimu/app/feature/home/HomeScreen.kt:113-141, 236-263`
  - `app/src/main/java/com/harimu/app/feature/catalog/CatalogScreen.kt:56-203`
- **Relevant Composables:** `HariMuRoot`, `HomeScreen`, `CatalogScreen`
- **Evidence:**
  - Top-level tabs are `[Home, Nusantara, History, Favorites, Settings]`. `CatalogRoute` is **not** a top-level destination and has no entry CTA on `HomeScreen` (`HomeScreen` only lets users open a specific `ToolGroup` via `onOpenGroup`, forcing them to tap a group first and then tap `filter_all` inside `CatalogScreen` to browse all 182 tools).
  - Meanwhile, `HomeScreen` embeds its own separate search `OutlinedTextField` (`home_search_input`), creating two disconnected search experiences (`HomeScreen` vs `CatalogScreen`) with no category/engine filtering on Home.
- **Root Cause:** `CatalogRoute` was added as a group drill-down destination while `NusantaraRoute` took a top-level slot.
- **Proposed Solution:** In `P2` + `P3` + `P4`, promote Catalog/Tools discovery to a clear first-class route (either a top-level tab or an unmistakable "Browse All 182 Tools" entry / search handoff from Home) while keeping `CatalogRoute(groupName?)` intact for deep navigation and preserving existing `nav_*` test tags or updating tests deliberately within scope.
- **Impact:** Users discovering HariMu via Home cannot browse or filter the full 182-tool catalog without a hidden two-step workaround.
- **Risk:** Medium — `HariMuUiAndNavigationTest.kt:68-73, 115` explicitly asserts `nav_home`, `nav_nusantara`, `nav_history`, `nav_favorites`, `nav_settings` tags; P2 must preserve existing routes and handle test expectations explicitly.
- **Recommended Phase:** `P2` (Shell & Nav), `P3` (Home), `P4` (Catalog).

#### [F-P1-02] Static TopAppBar Title Across All 5 Top-Level Tabs & Inconsistent Screen Chrome
- **Severity:** `P1`
- **Category:** Visual Hierarchy / UX Inconsistency / Navigation Problem
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/MainActivity.kt:309-321`
  - `app/src/main/java/com/harimu/app/feature/favorites/FavoritesScreen.kt:49-54`
  - `app/src/main/java/com/harimu/app/feature/history/HistoryScreen.kt:111-136`
  - `app/src/main/java/com/harimu/app/feature/settings/SettingsScreen.kt:110-116`
  - `app/src/main/java/com/harimu/app/feature/nusantara/NusantaraHubScreen.kt:48-53`
- **Relevant Composables:** `HariMuRoot`, `FavoritesScreen`, `HistoryScreen`, `SettingsScreen`, `NusantaraHubScreen`
- **Evidence:**
  - `MainActivity.kt:314-315` hardcodes `TopAppBar(title = { Text(stringResource(R.string.app_name)) })` for all 5 top-level tabs (`Home`, `Nusantara`, `History`, `Favorites`, `Settings`).
  - To compensate, `FavoritesScreen.kt:50-53` puts `"Tool Favorit"` inside the scrolling list; `HistoryScreen.kt:117-123` shows `"N Riwayat Pemrosesan"` + Clear button only when non-empty; `SettingsScreen` and `NusantaraHubScreen` have no screen title header at all.
  - Meanwhile `MainActivity.kt:280-356` puts `NavigationRail` **outside** the `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`, risking left-edge display-cutout/inset double handling or unpadded rail items, and inner sub-screens render **nested `Scaffold`s** (`EqualizerScreen.kt:291` explicitly passes `WindowInsets.safeDrawing` while other sub-screens rely on `ScaffoldDefaults`).
- **Root Cause:** Top-level chrome was factored into `MainActivity` once, while sub-screens were built independently with nested `Scaffold`s.
- **Proposed Solution:** In `P2`, render contextual destination titles & contextual top-bar actions (e.g., Clear History action in History top bar) per top-level route, unify window inset handling between `NavigationRail` and `Scaffold`, and standardize sub-screen `TopAppBar` tokens (`HariMuTopBar`).
- **Impact:** Weak spatial orientation across top-level sections and uneven top-bar surface/elevation transitions (`AudioStudioScreen` uses `surfaceVariant` at line 137 while all others use `surface`/`background`).
- **Risk:** Low–Medium (check nested `Scaffold` inset padding on `CatalogScreen` / `ToolScreen`).
- **Recommended Phase:** `P2` (Shell & Nav).

#### [F-P1-03] Widespread Bypass of `strings.xml` & Broken English Localization (Indonesian Strings Exposed in EN Mode)
- **Severity:** `P1`
- **Category:** UX Inconsistency / Accessibility / Technical Debt
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/studios/ImageMatrixScreen.kt:186, 197, 204, 215, 219, 223, 244, 248, 252-257, 293-306, 320, 352-411, 440`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/FilterPreviewCard.kt:70, 89-90`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/PdfPagePickerCard.kt:126-136, 213, 216, 241`
  - `app/src/main/java/com/harimu/app/feature/studios/ScreenRecorderScreen.kt:254` (`"Mikrofon"`), `345` (`"Stop"`)
  - `app/src/main/java/com/harimu/app/feature/studios/VoiceRecorderScreen.kt:160` (`"Back"`), `298` (`"Stop"`/`"Record"`)
  - `app/src/main/java/com/harimu/app/feature/studios/BackupScreen.kt:64, 73, 79, 92, 131`
  - `app/src/main/java/com/harimu/app/feature/studios/KeyVaultScreen.kt:145, 201, 248, 296, 384, 477`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolScreen.kt:771-801, 874, 888-890`
  - `app/src/main/java/com/harimu/app/feature/nusantara/NusantaraUi.kt:47`, `PrayerTimesScreen.kt:63`, `AreaLookupScreen.kt:74`, `RegionsScreen.kt:206`
- **Relevant Composables:** `ImageMatrixScreen`, `FilterPreviewCard`, `PdfPagePickerCard`, `ScreenRecorderScreen`, `VoiceRecorderScreen`, `BackupScreen`, `KeyVaultScreen`, `ToolScreen`, `CloudConnectionsCard`, `NusantaraScaffold`
- **Evidence:**
  - `ImageMatrixScreen.kt` hardcodes `"Tambah Layer Gambar"`, `"Teks baru"`, `"Kanvas"`, `"Latar #Hex"`, `"Urutan Layer"`, `"Belum ada layer..."`, `"Visibilitas"`, `"Naik"`, `"Turun"`, `"Duplikat"`, `"Hapus"`, `"Teks"`, `"Warna #Hex"`, `"Ukuran font (%)"`, `"Posisi X (%)"`, `"Skala (%)"`, `"Rotasi (°)"`, `"Opasitas (%)"`, `"Ekspor PNG ..."` regardless of `isEnglish`.
  - `FilterPreviewCard.kt:70, 89-90` does not even accept `isEnglish` and hardcodes `"Pratinjau filter langsung (GPU)"` and `"Matrix warna identik dengan hasil ekspor..."`.
  - `PdfPagePickerCard.kt:241` hardcodes `contentDescription = "Halaman $label"` in Indonesian even when `isEnglish = true`.
  - `ScreenRecorderScreen.kt:254` renders `"Mikrofon"` when `isEnglish = true`.
  - `VoiceRecorderScreen.kt:160, 298`, `BackupScreen.kt:92`, `KeyVaultScreen.kt:296, 477`, `PrayerTimesScreen.kt:63`, `AreaLookupScreen.kt:74`, `NusantaraUi.kt:47` hardcode English `"Back"` / `"Delete"` content descriptions even when Indonesian mode is active (bypassing `R.string.cd_back` and `R.string.action_delete`).
- **Root Cause:** Incremental feature additions skipped `strings.xml` + `values-en/strings.xml`, relying on ad-hoc `if (isEnglish)` checks or single-language strings.
- **Proposed Solution:** Move all UI copy and `contentDescription` strings into `res/values/strings.xml` and `res/values-en/strings.xml`, using `stringResource(...)` everywhere in Compose (since `MainActivity` already provides localized `LocalContext`/`LocalResources`).
- **Impact:** Mixed-language UI degrades trust and breaks TalkBack localization for both Indonesian and English users.
- **Risk:** Low.
- **Recommended Phase:** `P5` (`ToolScreen` + preview cards), `P2` (shared `TopBar` back button), & Post-P5 Studios/Nusantara.

#### [F-P1-04] Monolithic `ToolScreen.kt` & Engine/Network Logic Embedded Directly Inside Composables
- **Severity:** `P1`
- **Category:** Technical Debt / State Management / Separation of Concerns
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolScreen.kt` (988 lines: lines 106–987 in one composable)
  - `app/src/main/java/com/harimu/app/feature/studios/EqualizerScreen.kt:104-277` (734-line file; no ViewModel)
  - `app/src/main/java/com/harimu/app/feature/settings/CloudConnectionsCard.kt:50-137, 250-390` (`PendingOAuth` singleton + HTTP/PKCE + upload queue calls inside Composable)
  - `app/src/main/java/com/harimu/app/feature/studios/AudioStudioScreen.kt:55-56` & `ImageMatrixScreen.kt:90` (`.collectAsState()` instead of `.collectAsStateWithLifecycle()`)
- **Relevant Composables:** `ToolScreen`, `EqualizerScreen`, `CloudConnectionsCard`, `AudioStudioScreen`, `ImageMatrixScreen`
- **Evidence:**
  - `ToolScreen` (988 lines) exceeds project modularity limits (500-line guideline), declaring 10 `ActivityResultLauncher`s, raw `MediaPlayer` lifecycle, tool-ID branching (`AUD-18`, `VID-12`, `VID-19`, `VID-14`, `VID-25`, `VID-01`, `VID-21`, `IMG-06`, `IMG-33`, `PDF-04`, `AUD-09`, `AUD-28`), `aiClient.hasUploadConsent()` / `grantUploadConsent()`, and `uploadQueue.enqueue` + `processPending` inside composable lambdas.
  - `EqualizerScreen` manages `AudioToolMath`, `MediaJob().runCommand(...)`, `workspace.recordHistory(...)`, `fxController`, and 10 `mutableStateOf` variables directly inside the composable without a `ViewModel`, risking cancellation/state loss on rotation.
  - `AudioStudioScreen` and `ImageMatrixScreen` use non-lifecycle-aware `collectAsState()`.
- **Root Cause:** Rapid feature-phase additions bolted custom controls and async actions directly into screen composables instead of ViewModel state models.
- **Proposed Solution:** Split `ToolScreen.kt` during `P5` into modular sections (`ToolHeaderCard`, `ToolInputSection`, `ToolPreviewHost`, `ToolParametersSection`, `ToolExecutionFooter`, `ToolResultSection`) and move cloud-upload, consent, and SAF export orchestration into `ToolViewModel`. In Studio follow-up, migrate `EqualizerScreen` to `EqualizerViewModel` and switch `AudioStudioScreen`/`ImageMatrixScreen` to `collectAsStateWithLifecycle()`.
- **Impact:** Hard to test/maintain; rotation destroys `EqualizerScreen` live state, `ToolScreen` audio player/pendingExportFile state, and dialog flags (`showUploadConsent`).
- **Risk:** Medium — carefully preserve all 182 tool IDs, `ToolParam` contract, `testTag`s (`pick_input_button`, `run_tool_button`, etc.), and ID3 auto-fill behavior (`AUD-18`).
- **Recommended Phase:** `P5` (`ToolScreen`) & Post-P5 Studio Follow-up.

#### [F-P1-05] Dedicated Studios & Nusantara Screens Lack Tablet / Foldable / Expanded Support
- **Severity:** `P1`
- **Category:** Tablet & Foldable Weakness / Layout Issue
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/nusantara/NusantaraHubScreen.kt:48-52`
  - `app/src/main/java/com/harimu/app/feature/nusantara/EarthquakeScreen.kt:42-46`, `DoasScreen.kt:45-49`, `TtsScreen.kt:41-45`, `ReferenceScreens.kt:51-55, 117-121`
  - `app/src/main/java/com/harimu/app/feature/prayer/PrayerTimesScreen.kt:69-76`
  - `app/src/main/java/com/harimu/app/feature/area/AreaLookupScreen.kt:80-87`
  - `app/src/main/java/com/harimu/app/feature/studios/ImageMatrixScreen.kt:130-137`
  - `app/src/main/java/com/harimu/app/feature/studios/KeyVaultScreen.kt:302-308`
  - `app/src/main/java/com/harimu/app/feature/studios/BackupScreen.kt:98-104`
  - `app/src/main/java/com/harimu/app/feature/studios/ScreenRecorderScreen.kt:179-185`
  - `app/src/main/java/com/harimu/app/feature/studios/VoiceRecorderScreen.kt:166-173`
- **Relevant Composables:** All Nusantara, Prayer, Area, and 5 of 8 Studio screens
- **Evidence:**
  - While `HomeScreen` (`1040dp`), `CatalogScreen`/`HistoryScreen`/`FavoritesScreen`/`OpenSourceLicensesScreen` (`760dp`), `SettingsScreen`/`ToolScreen` (`640dp`), `VideoEditorScreen` (`760dp`), and `EqualizerScreen` (`680dp`) constrain line width, **17+ other screens stretch `fillMaxWidth()` to 100% edge-to-edge on tablets (`840dp+`) and unfolded foldables**.
  - On `Expanded` screens, `ToolScreen` (`640dp` centered single column), `VideoEditorScreen`, `ImageMatrixScreen`, `SettingsScreen`, `CatalogScreen`, and `HistoryScreen` leave vast empty side margins instead of two-pane/supporting-pane layouts (e.g., preview + parameters side-by-side in `ToolScreen`, or studio canvas + inspector side-by-side in `ImageMatrixScreen`/`VideoEditorScreen`). Only `AudioStudioScreen` (`215-221`) adapts into a 2-pane Mixer + Timeline layout at `600dp`.
  - `HomeScreen.kt:95-99` jumps straight from 2 columns at `320dp..839dp` to 3 columns at `840dp+`, leaving `ToolRowCard` / `GroupGridCard` very wide on Medium (`600dp–839dp`) tablets.
- **Root Cause:** `HariMuLayoutTokens` was not rolled out across Studios/Nusantara, and no shared `HariMuResponsiveContainer` or two-pane studio scaffold exists.
- **Proposed Solution:** Provide `HariMuContentContainer(maxWidth)` in `P1`, add two-pane (`BoxWithConstraints` / `WindowWidthSizeClass.Expanded`) layouts for `ToolScreen` (`P5`: Input/Preview left + Params/Action/Result right) and Studios (`VideoEditorScreen`, `ImageMatrixScreen` post-P5), and tune `HomeScreen`/`CatalogScreen` multi-column thresholds (`P3`/`P4`).
- **Impact:** Awkwardly stretched text fields, oversized buttons, and wasted horizontal space on tablets and foldables.
- **Risk:** Low–Medium.
- **Recommended Phase:** `P1` (Container Tokens), `P3`–`P5`, & Post-P5 Studios/Nusantara.

#### [F-P1-06] Legacy `StubTool` UI & Outdated "Phase 14" Cloud Copy Still Live in UI/Strings
- **Severity:** `P1`
- **Category:** UI-vs-Functionality Mismatch / Fake or Incomplete UI / Technical Debt
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/core/design/Components.kt:173, 217-229, 284`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolScreen.kt:199, 272-274, 290-316, 759`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolViewModel.kt:145-157`
  - `app/src/main/res/values/strings.xml:113-119, 122, 125-127, 132-133`
- **Relevant Composables:** `ToolCard`, `ToolScreen`, `ToolViewModel`
- **Evidence:**
  - Active catalogs now implement all 182 tools (`CatalogMediaStubs.kt` returns `emptyList()`, `ToolRegistryTest.kt` passes with real tools), yet `ToolCard`, `ToolScreen`, and `ToolViewModel` still carry `isStub` dummy-job execution UI (`action_run_dummy_job`, `stub_notice_card`, `badge_coming_soon`).
  - `strings.xml:114, 119` still retain stale copy `"Integrasi S3/R2, Google Drive, Dropbox, dan REST kustom disiapkan pada Fase 14"` (`settings_cloud_empty_desc`) and `"Fase 14"` (`cloud_status_phase14`), even though `CloudConnectionsCard.kt` is fully implemented.
- **Root Cause:** Leftover scaffolding from pre-Phase-14 development.
- **Proposed Solution:** Keep `Tool.isStub` guardrails clean or remove dead dummy-job affordances where unused (without violating `/ui_agent.md` §7 if any dynamic future stub exists—clearly differentiate unimplemented status vs removing stale `"Fase 14"` string resources). Update/clean unused Phase-14 placeholder strings.
- **Impact:** Confuses maintainers and risks exposing fake "dummy job" affordances if a stub is ever re-added.
- **Risk:** Low.
- **Recommended Phase:** `P1` (cleanup of `ToolCard` badges) & `P5` (`ToolScreen`).

#### [F-P1-07] `Tool.titleRes` / `Tool.descriptionRes` Contract Unused & `ToolGroup` Duplicated
- **Severity:** `P1`
- **Category:** UI Technical Debt
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/core/toolapi/ToolContract.kt:13-77, 233-244`
- **Relevant Types:** `ToolGroup`, `Tool`
- **Evidence:** `Tool` declares `val titleRes: Int get() = 0` and `val descriptionRes: Int get() = 0`, but no UI screen reads them; all rendering calls `tool.title(isEnglish)` and `tool.description(isEnglish)`. Meanwhile `ToolGroup` holds *both* `@StringRes` (`titleRes`, `subtitleRes`) and hardcoded `titleId`/`titleEn`/`subtitleId`/`subtitleEn` strings.
- **Root Cause:** Migration from early contract design without cleanup.
- **Proposed Solution:** Document `title(isEnglish)`/`description(isEnglish)` as canonical (preserving engine/tool contract safety per `/ui_agent.md` §4: *"Do not change tool IDs. Do not change engine contracts."*), and keep UI consumption consistent rather than breaking 182 tool implementations.
- **Impact:** Confusion during P4/P5 when refactoring `CatalogScreen` and `ToolScreen`.
- **Risk:** Low if preserved read-only.
- **Recommended Phase:** `P1` / `P4` documentation alignment.

---

### P2 — Moderate UI/UX, Design System Consistency, Duplication & Accessibility Findings

#### [F-P2-01] Half-Migrated Design System: Unused `HariMuSectionCard`, Inconsistent Borders, Padding, Shapes, and Hardcoded Brand Colors
- **Severity:** `P2`
- **Category:** Visual Inconsistency / Design System Bypass
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/core/design/Components.kt:140-159` (`HariMuSectionCard` — 0 usages in app), `257` (`HariMuColors.ElectricOrange`), `581, 588, 628` (`HariMuColors.TealEmerald`)
  - `app/src/main/java/com/harimu/app/core/design/Theme.kt:45-51, 185-190`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolScreen.kt:240, 263, 265, 688, 690`
  - `app/src/main/java/com/harimu/app/feature/home/HomeScreen.kt:181, 210, 298, 302, 346, 357, 364`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/VideoTrimPreviewCard.kt:324, 352-353`
  - `app/src/main/java/com/harimu/app/feature/toolscreen/AudioPreviewCards.kt:390, 434-435`
- **Evidence:**
  - `HariMuSectionCard` (`Components.kt:140`) was added to `core/design` but `grep` confirms **0 callers** anywhere in the codebase. Every screen builds bespoke `Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)))` (using `0.5f` vs `0.55f` vs `0.4f` vs `0.45f` vs no border).
  - Padding inside cards varies arbitrarily across screens (`10.dp`, `12.dp`, `14.dp`, `16.dp`, `18.dp`) instead of `HariMuThemeTokens.spacing`.
  - Corner shapes bypass `AppShapes` (`RoundedCornerShape(6.dp)`, `8.dp`, `10.dp`, `12.dp`, `16.dp` hardcoded inline).
  - `ToolCard` (`257`), `ToolScreen` (`240`), `ResultCard` (`581, 588, 628`), `VideoTrimPreviewCard` (`352-353`), `AudioPreviewCards` (`434-435`), `VoiceRecorderScreen` (`188, 292`), and `KeyVaultScreen` (`432`) hardcode `HariMuColors.ElectricOrange` / `TealEmerald` instead of `MaterialTheme.colorScheme.primary` / `secondary`, breaking Material You Dynamic Color cohesion when `settings.dynamicColor = true`.
- **Root Cause:** `HariMuThemeTokens` and `HariMuSectionCard` were introduced incrementally without refactoring existing feature cards.
- **Proposed Solution:** Standardize surface tiers, border tokens, card primitives (`HariMuSectionCard` / `HariMuSurfaceCard`), badge shapes, and semantic color roles in `P1`, ensuring Dynamic Color compatibility while preserving `ToolGroup` category accents.
- **Impact:** Fragmented visual rhythm and broken Dynamic Color harmony.
- **Risk:** Low.
- **Recommended Phase:** `P1` (Design System), adopted across `P3`–`P5`.

#### [F-P2-02] Heavy Composable & Flow Duplication Across Features
- **Severity:** `P2`
- **Category:** Composable Duplication / Maintainability
- **File Paths & Lines:**
  - Search bars: `HomeScreen.kt:113-140`, `CatalogScreen.kt:103-131`, `ReferenceScreens.kt:167-183`
  - Error cards: `NusantaraUi.kt:72-107`, `PrayerTimesScreen.kt:127-156`, `AreaLookupScreen.kt:150-183` vs `Components.kt:716-758` (`ErrorState`)
  - Video playback frame overlay: `VideoTrimPreviewCard.kt:204-253` vs `VideoEditorScreen.kt:569-607`
  - Trim `RangeSlider` + `Set Start`/`Set End` buttons: `VideoTrimPreviewCard.kt:337-400` vs `AudioPreviewCards.kt:419-482`
  - Secondary input picker rows (label + clear button): `ToolScreen.kt:444-556` (repeated 4 times nearly verbatim!)
  - SAF `CreateDocument` + Toast flow: `ToolScreen.kt:212-218`, `HistoryScreen.kt:85-94`, `VideoEditorScreen.kt:147-153`, `EqualizerScreen.kt:156-162`, `BackupScreen.kt:60-66`
- **Root Cause:** Feature screens and preview cards implemented local UI blocks instead of extending `core/design` or `feature/toolscreen` shared components.
- **Proposed Solution:** Consolidate Search input (`HariMuSearchBar`), unified `ErrorState`, `SecondaryFileRow`, `MediaTrimRangeControls`, and `SafExportEffect` in `P1` / `P4` / `P5`.
- **Impact:** Inconsistent padding, missing clear buttons (`ReferenceScreens.SearchField` lacks clear button), and duplicated bugs (like the Main-thread SAF copy).
- **Risk:** Low.
- **Recommended Phase:** `P1` (`HariMuSearchBar`, `ErrorState` adoption) & `P5` (`SecondaryFileRow`, trim controls).

#### [F-P2-03] Accessibility Deficiencies: Non-Semantic Tap Targets, Sub-48dp Controls & Small Canvas Handles
- **Severity:** `P2`
- **Category:** Accessibility Deficiency
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/toolscreen/PdfPagePickerCard.kt:192-198` (page selection `Column` uses `.pointerInput { detectTapGestures }` without `.clickable`, `Role.Checkbox`/`Button`, or `selected` semantics — invisible to TalkBack & Switch Access!)
  - `app/src/main/java/com/harimu/app/feature/toolscreen/PdfPagePickerCard.kt:148-171` (drag-reorder only accessible via long-press drag; no accessibility action / keyboard fallback; though `order` text param exists as workaround)
  - `app/src/main/java/com/harimu/app/feature/studios/ImageMatrixScreen.kt:290-307` (5 `IconButton`s packed in one `Row` with `weight(1f)` title — squeezes title on 360dp phones and lacks explicit `minimumInteractiveComponentSize()`)
  - `app/src/main/java/com/harimu/app/feature/toolscreen/PerspectivePointsCard.kt:146-153` (Canvas stroke `3f` px and handle radii `10f`/`6f` px — tiny ~3.3dp circles on 3x density displays, hard to grab with fingers and unscaled across DPIs)
  - `app/src/main/java/com/harimu/app/feature/settings/SettingsScreen.kt:201, 335-388` (`TextButton`s without `minimumInteractiveComponentSize()`)
  - `app/src/main/java/com/harimu/app/feature/nusantara/NusantaraHubScreen.kt:129` (`TextButton` without `minimumInteractiveComponentSize()`), `146-171` (Nusantara hub tool cards lack favorite/ID/engine cues and `ToolCard` touch consistency)
- **Root Cause:** Raw `pointerInput` and `Float` pixel radii used in custom views; inconsistent application of `.minimumInteractiveComponentSize()`.
- **Proposed Solution:** Replace `PdfPagePickerCard` tap gesture with `Modifier.clickable(role = Role.Checkbox)` + `semantics { selected = ... }`; convert `PerspectivePointsCard` handle sizes to `dp.toPx()` (`14.dp`/`8.dp` visual + generous hit target); enforce `minimumInteractiveComponentSize()` on all `IconButton`/`TextButton` controls and move ImageMatrix layer actions to overflow/action bar on narrow widths.
- **Impact:** Inaccessible PDF page selection for TalkBack users; cramped/tiny touch targets on dense rows and perspective handles.
- **Risk:** Low.
- **Recommended Phase:** `P1` (button/touch defaults), `P5` (`PdfPagePickerCard`, `PerspectivePointsCard`), & Post-P5 Studios.

#### [F-P2-04] Missing State Preservation Across Configuration Changes & Tool Navigation
- **Severity:** `P2`
- **Category:** State Management Problem Affecting UI
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/home/HomeScreen.kt:83` (`remember { mutableStateOf("") }` loses search query on tab switch/rotation)
  - `app/src/main/java/com/harimu/app/feature/catalog/CatalogScreen.kt:66-67` (`remember(initialGroup)` loses selected filter chip & search query on rotation or returning from `ToolScreen`)
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolScreen.kt:118, 126` (`showUploadConsent`, `pendingExportFile` lost on rotation; `pendingExportFile` loss breaks SAF export if OS kills/recreates activity during `CreateDocument`!)
  - `app/src/main/java/com/harimu/app/feature/studios/EqualizerScreen.kt:113-128` (all state lost on rotation)
  - `app/src/main/java/com/harimu/app/feature/studios/ScreenRecorderScreen.kt:87-93` (config lost on rotation)
- **Root Cause:** Use of `remember` instead of `rememberSaveable` or `ViewModel` / `SavedStateHandle`.
- **Proposed Solution:** Store `CatalogScreen` filter/query and `HomeScreen` query via `rememberSaveable` (or `CatalogViewModel`); move `pendingExportFile` and `showUploadConsent` into `ToolViewModel` in `P5` so SAF activity-result restarts cannot lose the pending export target.
- **Impact:** Selecting a file via SAF document picker on low-RAM devices that recreate Activity loses `pendingExportFile`, silently failing to save!
- **Risk:** Low–Medium.
- **Recommended Phase:** `P3` (`HomeScreen`), `P4` (`CatalogScreen`), `P5` (`ToolScreen`).

#### [F-P2-05] Asymmetric Corner Radius & Decorative Hero Iconography / Contrast Concerns
- **Severity:** `P2`
- **Category:** Visual Hierarchy / Design Polish
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/home/HomeScreen.kt:269-329` (`HeroStudioBanner` hardcoded dark scrim colors even in Light mode; badge pill + gradient + static raster overlay takes ~180dp above fold before tools/search appear)
  - `app/src/main/java/com/harimu/app/feature/settings/SettingsScreen.kt:125-137` (4 theme chips in horizontal scroll row rather than segmented/wrapping grid; cards have identical visual weight across 6 settings sections)
  - `app/src/main/java/com/harimu/app/feature/history/HistoryScreen.kt:203-247` (`Open`, `Share`, `Save` buttons squeezed into 1 `Row` on 360dp phones — `"Bagikan"`/`"Simpan"` risk truncation with `maxLines = 1` at `fontScale >= 1.3`)
- **Root Cause:** Fixed single-row action bars and oversized static hero banner.
- **Proposed Solution:** In `P1`–`P3`, tighten `HeroStudioBanner` height & replace redundant decorative clutter with functional quick-actions/status; use `FlowRow` or compact icon+label overflow in `ResultCard` / `HistoryScreen` action rows at large font scales; establish clear surface elevation/section grouping in `SettingsScreen`.
- **Impact:** Improved above-the-fold tool reachability and legibility under large system font scaling.
- **Risk:** Low.
- **Recommended Phase:** `P1` (`ResultCard`), `P3` (`HomeScreen`), History/Settings polish.

#### [F-P2-06] `HistoryScreen` & `HomeScreen` Minor Performance / List Keying Hazards
- **Severity:** `P2`
- **Category:** Performance Risk
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/history/HistoryScreen.kt:149-151` (performs `File(item.outputPath).exists()` syscall for every visible item during composition!)
  - `app/src/main/java/com/harimu/app/feature/nusantara/TtsScreen.kt:113` (`items(result.answers)` without `key = { ... }`)
  - `app/src/main/java/com/harimu/app/feature/studios/ImageMatrixScreen.kt:259` (`forEachIndexed` inside `Column.verticalScroll` instead of `LazyColumn` if layer count grows)
  - `app/src/main/java/com/harimu/app/feature/toolscreen/ToolScreen.kt:692` (`tool.params` rendered in `Column`, fine for <=10 params, though `ToolInput.params: Map<String, Any>` triggers Compose instability if passed directly)
- **Root Cause:** Inline `java.io.File.exists()` checks inside `LazyColumn` item lambdas and unkeyed list items.
- **Proposed Solution:** Cache/resolve file existence asynchronously or inside `HistoryViewModel` UI model; add stable `key`s to `TtsScreen` items and annotate immutable UI state models with `@Immutable` where needed.
- **Impact:** Prevents dropped scroll frames on long history lists.
- **Risk:** Low.
- **Recommended Phase:** `P4`/`P5` & History/Nusantara follow-up.

---

### P3 — Minor Polish, Previews, Typography & Test Gaps

#### [F-P3-01] `Locale.US` Missing in `VoiceRecorderScreen` Timer Formatting & Typographic Polish
- **Severity:** `P3`
- **Category:** Minor Polish / Localization Consistency
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/feature/studios/VoiceRecorderScreen.kt:186` (`String.format("%02d:%02d", ...)` without `Locale.US`)
  - `app/src/main/java/com/harimu/app/feature/studios/ImageMatrixScreen.kt:440` (`"Mengekspor..."` ASCII dots instead of `"…"`)
  - `app/src/main/java/com/harimu/app/feature/toolscreen/PdfPagePickerCard.kt:126, 245` (`"..."` instead of `"…"`)
  - `app/src/main/java/com/harimu/app/core/design/Theme.kt:115-183` (`AppTypography` omits `displayLarge`, `displayMedium`, `headlineLarge`, `labelLarge`, falling back to M3 default system font `Roboto` instead of `PlusJakartaSans` whenever `MaterialTheme.typography.labelLarge` is used — e.g. `VideoTrimPreviewCard.kt:259`, `AudioPreviewCards.kt:414`, `VideoEditorScreen.kt:236`, `ScreenRecorderScreen.kt:200`!)
- **Root Cause:** Partial `Typography(...)` override in `Theme.kt`.
- **Proposed Solution:** Populate `labelLarge` (and remaining typography roles) with `PlusJakartaSans` in `Theme.kt` during `P1`; replace `"..."` with `"…"` and add `Locale.US` to `String.format`.
- **Impact:** Ensures consistent `PlusJakartaSans` typeface across section headers, chip labels, and buttons.
- **Risk:** Very Low.
- **Recommended Phase:** `P1` (`Theme.kt` Typography) & Post-P5 polish.

#### [F-P3-02] Missing `@Preview` Matrix & Automated UI/Accessibility Coverage for Core Flows
- **Severity:** `P3`
- **Category:** Testing & Preview Infrastructure
- **File Paths & Lines:**
  - `app/src/main/java/com/harimu/app/core/design/ComponentPreviews.kt:1-157`
  - `app/src/test/java/com/harimu/app/HariMuUiAndNavigationTest.kt:1-490`
- **Evidence:** Only `core/design/ComponentPreviews.kt`, `AudioPreviewCards.kt`, and `EqualizerScreen.kt` contain `@Preview`s. `HomeScreen`, `CatalogScreen`, `HistoryScreen`, `FavoritesScreen`, `SettingsScreen`, `ToolScreen`, and Studios have zero `@Preview` coverage for Light/Dark/AMOLED/Compact/Expanded/`fontScale = 1.5f`. `HariMuUiAndNavigationTest.kt` does not test `FavoritesScreen`, `NusantaraHubScreen`, `ToolScreen` execution states (`Queued`/`Running`/`Done`/`Failed`), or `fontScale` overflow.
- **Proposed Solution:** Expand `ComponentPreviews.kt` (P1) and add Robolectric Compose UI/accessibility/state-transition tests across `P2`–`P5` verification gates.
- **Impact:** Prevents visual and accessibility regressions across redesign phases.
- **Risk:** Very Low.
- **Recommended Phase:** `P1`–`P5` (incrementally per phase).

---

## 4. Shared vs. Specialized Component Consolidation Matrix

| Component | Current Location | Verdict | Target Location & Notes |
|---|---|---|---|
| `HariMuThemeTokens`, `HariMuColors`, `AppShapes`, `AppTypography` | `core/design/Theme.kt` | **Upgrade & Share** | Complete `labelLarge` + semantic surface/border tokens in `P1`; stop direct `ElectricOrange`/`TealEmerald` bypasses. |
| `HariMuSectionCard`, `ToolCard`, `GroupHeader`, `Param*`, `FilePickerField`, `ProgressSheet`, `ResultCard`, `EmptyState`, `ErrorState` | `core/design/Components.kt` | **Upgrade & Share** | Actually adopt `HariMuSectionCard` across Settings/ToolScreen/Studios; add `HariMuSearchBar`, `HariMuTopBar`, `HariMuResponsiveContainer`. |
| `SearchField` | Duplicated in `HomeScreen`, `CatalogScreen`, `ReferenceScreens` | **Consolidate → Shared** | Move single `HariMuSearchBar` to `core/design/` (`P1`/`P3`/`P4`). |
| `NusantaraErrorCard` / `PrayerTimes` / `AreaLookup` error cards | Duplicated in 3 places | **Consolidate → Shared** | Reuse `core/design/ErrorState` (`P1` + Nusantara/Utility cleanup). |
| `SAF CreateDocument` async copy helper | Duplicated in 5 screens (on Main thread!) | **Consolidate → Shared** | Shared coroutine-safe `Dispatchers.IO` export handler (`P2`/`P5`). |
| `MediaTrimRangeControls` + `ExoPlayer` play/pause overlay | Duplicated in `VideoTrimPreviewCard` & `AudioPreviewCards` | **Share within `toolscreen/`** | Extract shared trim-range slider + `Set Start`/`Set End` bar in `P5`. |
| `PerspectivePointsCard`, `FilterPreviewCard`, `RoiLogoPreviewCard`, `FaceBlurPreviewCard`, `PdfPagePickerCard`, `AudioBeforeAfterPreviewCard` | `feature/toolscreen/` | **Keep Specialized** | Domain-specific interactive tool previews; upgrade to `strings.xml`, `dp`-scaled handles, semantic accessibility, and `P1` card tokens. |
| `VideoEditorScreen`, `AudioStudioScreen`, `ImageMatrixScreen`, `EqualizerScreen`, `VoiceRecorderScreen`, `ScreenRecorderScreen`, `KeyVaultScreen`, `BackupScreen` | `feature/studios/` | **Keep Specialized** | Complex multi-track/timeline/live-FX/foreground-service workflows must remain dedicated studios, adopting `P1` tokens & responsive two-pane layouts after `P5`. |

---

## 5. Traceability Matrix Across All 17 P0 Audit Objectives

| # | Audit Objective | Mapped Finding IDs |
|---|---|---|
| 1 | Visual inconsistencies | `F-P1-02`, `F-P2-01`, `F-P2-05`, `F-P3-01` |
| 2 | UX inconsistencies | `F-P1-01`, `F-P1-02`, `F-P1-03`, `F-P2-05` |
| 3 | Navigation problems | `F-P0-04`, `F-P1-01`, `F-P1-02` |
| 4 | Layout issues | `F-P0-01`, `F-P0-05`, `F-P1-05`, `F-P2-05` |
| 5 | Accessibility deficiencies | `F-P0-01`, `F-P1-03`, `F-P2-03` |
| 6 | Composable duplication | `F-P2-02` |
| 7 | Components that should be shared | Section 4 Matrix (`HariMuSearchBar`, `HariMuSectionCard`, `ErrorState`, `HariMuTopBar`, `SafExport`, `MediaTrimRangeControls`) |
| 8 | Components that should remain specialized | Section 4 Matrix (`*PreviewCard`, 8 Studio screens, Nusantara data screens) |
| 9 | State management problems affecting UI | `F-P0-03`, `F-P1-04`, `F-P2-04` |
| 10 | Performance risks | `F-P0-02`, `F-P0-03`, `F-P2-06` |
| 11 | Poorly organized tool discovery | `F-P1-01`, `F-P2-05` |
| 12 | UI elements not matching actual functionality | `F-P0-04` (`CROSSWORD`->`TtsRoute`, `SETTINGS`), `F-P0-05` (playhead vs scroll), `F-P1-06` (`Fase 14` cloud copy) |
| 13 | Fake or incomplete UI | `F-P1-06` (`StubTool` dummy job strings/UI remnants; actual 182 tools are implemented) |
| 14 | Tablet and foldable weaknesses | `F-P1-02`, `F-P1-05`, `F-P2-01` |
| 15 | Opportunities for improved visual hierarchy | `F-P1-01`, `F-P1-02`, `F-P2-01`, `F-P2-05`, `F-P3-01` |
| 16 | UI technical debt | `F-P1-03`, `F-P1-04`, `F-P1-07`, `F-P2-02`, `F-P3-02` |
| 17 | Potential regressions if redesigning current screens | Section 6 Guardrails (preserving 182 `ToolRegistry` IDs, `testTag`s, WorkManager `ToolViewModel` flow, OAuth `harimu://oauth` intent bus, foreground service consent flow) |

---

## 6. Phased Implementation Roadmap & Regression Guardrails

### Recommended Execution Order
1. **P1 — HariMu Design System (`core/design/`)**:
   - Complete `AppTypography` (`labelLarge` et al.), refine `HariMuColors` semantic surface hierarchy (`surfaceContainerLow/High` alignment), eliminate hardcoded `ElectricOrange`/`TealEmerald` overrides when `dynamicColor = true`, adopt `HariMuSectionCard`, add `HariMuSearchBar`, `HariMuTopBar`, `HariMuResponsiveContainer`, and expand `ComponentPreviews.kt`.
2. **P2 — Application Shell & Navigation (`MainActivity.kt` + Top-Level Framing)**:
   - Contextual `TopAppBar` titles & actions per top-level destination, fix `DedicatedScreenRoute.SETTINGS` pop/singleTop behavior, fix `NavigationRail` + `WindowInsets` composition, make all 182 tools discoverable from top-level navigation/Home without breaking existing routes or `nav_*` test tags, and move `SettingsScreen` disk scans off Main thread.
3. **P3 — Home Dashboard (`HomeScreen.kt`)**:
   - Streamline hero footprint, elevate quick-access Favorites/Recents + full-catalog entry, unify search experience with `HariMuSearchBar`, preserve query via `rememberSaveable`, and refine Medium/Expanded grid breakpoints (`1/2/3/4` cols appropriately).
4. **P4 — Catalog & Discovery (`CatalogScreen.kt`)**:
   - Promote full-catalog browsing, add saved filter/scroll state, optional engine/capability filtering (offline vs internet vs model) backed by real `Tool` metadata, and polish `GroupHeader` + `ToolRowCard` density.
5. **P5 — Universal Tool Workspace (`ToolScreen.kt` + Preview Cards)**:
   - Modularize 988-line `ToolScreen.kt` into focused composables; move `pendingExportFile`, `showUploadConsent`, and `Dispatchers.IO` SAF copy into `ToolViewModel`; fix hardcoded strings in `FilterPreviewCard`, `PdfPagePickerCard`, and `ToolScreen`; fix `PdfPagePickerCard` TalkBack semantics and `PerspectivePointsCard` `dp`-scaled handles; add responsive 2-pane (`Input/Preview` | `Params/Action/Result`) layout on `Expanded` screens.
6. **Post-P5 Follow-Up — Dedicated Studios & Nusantara Suite**:
   - Fix `P0` non-scrollable columns (`ScreenRecorderScreen`, `VoiceRecorderScreen`, `BackupScreen`, `NusantaraColumn`), `AudioStudioScreen` playhead `dp/px` math, `KeyVaultScreen` Main-thread `copyUriToCache`/`readBytes`, `EqualizerScreen` ViewModel extraction, `ImageMatrixScreen` localization + 48dp layer actions, and `widthIn`/2-pane tablet support across Nusantara, Prayer, and Area screens.

### Non-Negotiable Regression Guardrails (`/ui_agent.md` §4)
- **Never alter tool IDs (`CORE-01`..`NUS-12`) or `ToolRegistry` contracts (`Tool`, `ToolParam`, `InputSpec`).**
- **Preserve all existing `Modifier.testTag(...)` selectors** (`nav_*`, `home_search_input`, `catalog_search_input`, `filter_*`, `group_card_*`, `tool_card_*`, `fav_button_*`, `pick_input_button`, `run_tool_button`, `language_switch`, `open_source_licenses_*`, `clear_history_button`, `delete_history_*`, studio tags) or update tests explicitly inside the phase touching that screen.
- **Keep `MainActivity.handleOAuthIntent` (`harimu://oauth`), localized `CompositionLocalProvider`, `ToolJobWorker` WorkManager queue, and Room schemas intact.**
