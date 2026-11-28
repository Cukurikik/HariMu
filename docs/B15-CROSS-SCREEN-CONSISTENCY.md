# B15 — Cross-Screen Integration & UI Consistency

Audit of the frontend after B1–B14, scoped to integration defects (not a redesign).
Skills applied: `.agents/skills/android-accessibility`, `.agents/skills/compose-component-design`,
`.agents/skills/design-system`, `.agents/skills/compose-ui-testing-patterns`,
`.agents/skills/android-testing`. No SDK/JDK/Gradle was installed locally; verification is CI-only.

## 1. Audit result per area

| Area | Finding |
| --- | --- |
| Home / Catalog / Favorites / History | Consistent: shared `HariMuSearchBar`, `HariMuResponsiveContainer` width caps, `Lazy*` keys, `remember`-ed filters, `HariMuStatusBanner` for empty/error states. No change. |
| Tool Workspace (`ToolScreen`) | Uses shared `ToolProgressView`, `ToolResultComponents`, `HariMuTopBar`. Consistent. |
| Video / Audio / Image / PDF / Converter studios | **Inconsistent chrome**: each screen hand-rolls `Scaffold { TopAppBar(…) }` with its own title typography and colour wiring instead of `HariMuTopBar`. |
| Nusantara / utility screens | Were also inconsistent: `UtilityScreenScaffold` re-implemented the top bar (own colours, `labelSmall` subtitle, no heading semantics). |
| Settings / Cloud / AI | Settings renders inside the app shell (shell top bar). `CloudConnectionsCard` uses the shared inputs/banners. Consistent. |
| Jobs / History / Results / Export | Single pipeline: `JobManager` → `ToolJobWorker` → `JobCenterComponents`; export goes through `ToolResultExportHandlers`. Consistent. |
| Navigation | One `NavHost` in `MainActivity`, one `TopLevelDestination` model, adaptive shell. Consistent. |
| Theme / tokens | `HariMuTheme` + `HariMuThemeTokens` used everywhere; the remaining bypasses were the per-screen `TopAppBarDefaults.topAppBarColors()` calls. |
| Persistence / ToolRegistry | Untouched and consistent (Room v4, `WorkspaceRepository`, 182 registered tools). |

## 2. Root causes corrected

**Root cause 1 — screen chrome was duplicated instead of shared.**
`HariMuTopBar` (colors from `HariMuNavigationDefaults.topBarColors()`, title `titleLarge`, optional
subtitle, `heading()` semantics, back button with `R.string.cd_back` and `minimumInteractiveComponentSize`,
`backTestTag`) existed but four screens bypassed it:

| File | Before | After |
| --- | --- | --- |
| `core/design/utilities/UtilityComponents.kt` | Local `TopAppBar` + own colours + `labelSmall` subtitle, no heading semantics — this one component served every Nusantara/utility screen | Delegates to `HariMuTopBar`, keeps `extraActions` and the refresh action (`utility_refresh_button`, spinner while refreshing) |
| `feature/studios/VoiceRecorderScreen.kt` | Local `TopAppBar` with default colours, inline tag/`sizeIn` | `HariMuTopBar(backTestTag = "voice_recorder_back_button")` |
| `feature/studios/PdfVaultScreen.kt` | Local `TopAppBar` with `titleLarge.copy(fontWeight = Bold)` (typography bypass) and `TopAppBarDefaults` colours | `HariMuTopBar(backTestTag = "pdf_vault_back_btn")` |
| `feature/studios/BackupScreen.kt` | Local `TopAppBar` + hard-coded bilingual title | `HariMuTopBar` with `R.string.backup_title` |

Effect: every utility screen and these studios now share one top-bar implementation — same title
typography, same surface colours, same back affordance, and the B14 heading semantics apply to them
instead of only to shell screens.

**Root cause 2 — UI copy hard-coded inside a screen.**
`BackupScreen` carried its title and intro paragraph as `if (isEnglish) "…" else "…"` literals, so the
copy could not be translated, reviewed or reused. Both moved to resources:
`backup_title`, `backup_intro` (`values/` + `values-en/`).

**Root cause 3 — dead imports / duplicated affordances after the migration.**
Removed the now-unused `TopAppBar`, `TopAppBarDefaults`, `Icons.AutoMirrored.Filled.ArrowBack`,
`sizeIn`, `minimumInteractiveComponentSize`, `FontWeight` and `TextOverflow` imports from the migrated
files, so no duplicated icon/button code is left behind.

## 3. Files modified

| File | Change |
| --- | --- |
| `core/design/utilities/UtilityComponents.kt` | `UtilityScreenScaffold` → `HariMuTopBar` (−40 lines of duplicated chrome) |
| `feature/studios/VoiceRecorderScreen.kt` | Shared top bar |
| `feature/studios/PdfVaultScreen.kt` | Shared top bar (bold-title bypass removed) |
| `feature/studios/BackupScreen.kt` | Shared top bar + resources instead of inline copy |
| `res/values/strings.xml`, `res/values-en/strings.xml` | `backup_title`, `backup_intro` |
| `app/src/test/java/com/harimu/app/B15CrossScreenConsistencyTest.kt` | New: utility + studio chrome render the shared bar (heading, back/refresh tags, ≥48dp) |
| `docs/B15-CROSS-SCREEN-CONSISTENCY.md` | This report |

## 4. Remaining inconsistencies (reported, not changed)

1. **Nine studio screens still hand-roll their top bar**: `VideoForgeScreen:107`, `VideoEditorScreen:154`,
   `PdfStudioScreen:241`, `AudioStudioScreen:257`, `ImageMatrixScreen:158`, `EqualizerScreen:361`,
   `ScreenRecorderScreen:243`, `UniversalConverterScreen:191`, `KeyVaultScreen:348`. Each carries
   screen-specific actions/tabs, so migrating them needs a per-screen pass (and the same heading/tag
   guarantees). Until then their titles are not heading navigation targets.
2. **Inline bilingual copy remains** in `PdfStudioScreen` (metadata field labels “Title/Author/Subject/
   Keywords”, lines ~1333–1336) and `EqualizerScreen` (“Audio FX error” fallback, line ~206). These are
   inside callbacks/small dialogs where `stringResource` needs a hoisted value.
3. **Raw `Color.Black`/`Color.White` in media previews** (`VideoPreview`, `VideoTrimPreviewCard`,
   `ToolResultComponents`, `ImagePreviewComponents`) is intentional: letterboxed video/photo surfaces
   must stay black regardless of theme. Not treated as a token bypass.
4. **`Toast` for transient confirmations** (45 call sites) instead of a shared snackbar. Consistent with
   itself (all copy comes from resources) and announced by the system; converting it would be a
   redesign, out of scope for B15.

## 5. Verification

Performed locally (no compiler available):

- All migrated call sites import exactly one `HariMuTopBar`; `grep` confirms no `TopAppBar`,
  `Icons.AutoMirrored.Filled.ArrowBack`, `sizeIn`, `minimumInteractiveComponentSize`, `FontWeight` or
  `TextOverflow` usages are left imported-but-unused in the four migrated files.
- Test tags preserved: `utility_back_button`, `utility_refresh_button`, `voice_recorder_back_button`,
  `pdf_vault_back_btn`, `backup_back_button` (existing P13 assertions still address the same nodes).
- Resource parity: `python3` XML parse → `values/` and `values-en/` both 1204 strings, zero duplicate
  keys, zero keys missing on either side.
- Every added Kotlin line is ≤ 120 characters.

CI: `.github/workflows/android-ci.yml` still stops at **“Check ktlint formatting”** on pre-existing
violations outside this change (1228 lines > 120 columns across 133 files, plus existing
argument-wrapping/import-order findings), so `assembleDebug`, `testDebugUnitTest` — including the new
`B15CrossScreenConsistencyTest` — `lintDebug` and `assembleRelease` do not run. **The B15 tests are
written but not executed, and the migrated screens were not rendered on a device or emulator.**

## 6. Recommended next step

Pay the formatting debt (or set a project-wide policy for `max_line_length`) so the Android job can
compile and run unit tests; then migrate the nine remaining studio top bars in one follow-up pass so
heading semantics and top-bar colours are uniform across every screen.
