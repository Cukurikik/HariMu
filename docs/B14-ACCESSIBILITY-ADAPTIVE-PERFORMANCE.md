# B14 — Accessibility, Adaptive UI, Performance & Lifecycle Audit

Scope: cross-cutting quality of the Compose frontend (`app/src/main/java/com/harimu/app/**`).
Skills applied: `.agents/skills/android-accessibility`, `.agents/skills/jetpack-compose-performance`,
`.agents/skills/compose-ui-testing-patterns`, `.agents/skills/android-testing`.

No Android SDK/JDK/Gradle was installed locally; verification runs in GitHub Actions (see
"Verification status").

## 1. Accessibility

### 1.1 What was already correct (verified, unchanged)

| Area | Evidence |
| --- | --- |
| Touch targets | All shared controls call `Modifier.minimumInteractiveComponentSize()` and `heightIn(min = …)` (`DesignInputsAndActions.kt` buttons, text actions, search bar; `DesignChrome.kt` back button). Existing Robolectric checks assert ≥48dp in `P13UxAndAccessibilityTest` and `HariMuUiAndNavigationTest`. |
| Icon descriptions | Icons inside labelled buttons use `contentDescription = null` (Button/TextButton text is the accessible name); standalone icon buttons carry `stringResource(R.string.cd_*)`; decorative icons in banners/badges are `null`. |
| Compound controls | Rows use `Modifier.toggleable(role = Role.Switch)` / `clickable(role = Role.Button)` so TalkBack reads label + state as one node (`DoasScreen`, `NusantaraHubScreen`, `HomeScreen`, `SettingsScreen`). |
| Dialog structure | All dialogs go through `HariMuAlertDialog` (Material 3 `AlertDialog`: focus trap, escape via `onDismissRequest`, labelled confirm/dismiss buttons). |
| Dynamic text | Layouts avoid fixed heights on text rows; font-scale regression tests exist at `fontScale = 1.5f`. |
| Reduced motion | `grep -E "animate|Animatable|Crossfade|AnimatedVisibility"` over `app/src/main/**` returns no UI animation; only indeterminate `LinearProgressIndicator` (functional progress, allowed by `docs/DESIGN-SYSTEM.md` §211). Nothing to gate on the system animation scale. |
| Form validation | Fields use Material `isError` + `supportingText` (`ToolParameterComponents.kt`, `SettingsScreen.kt`, `DesignInputsAndActions.kt`), so the message is attached to the field and read when it gains focus. |

### 1.2 Defects fixed

1. **Nothing announced asynchronous status.** No `liveRegion` existed anywhere in the app, so a load
   that failed, a refresh that fell back to cached data, or a job that finished while the user was
   elsewhere produced no TalkBack announcement — the change was only discoverable by swiping.
   - `HariMuStatusBanner` (the shared error/warning/success surface used by every screen) is now a
     live region: **Assertive** for `StatusTone.ERROR`, **Polite** for info/success/warning.
2. **Job outcomes were silent.** `ToolProgressView` terminal cards (`Done`, `Failed`, `Interrupted`,
   `Cancelled`) now carry a polite live region via a small private `Modifier.jobOutcomeLiveRegion()`.
   Running progress keeps the Material `ProgressBarRangeInfo` semantics and is intentionally *not*
   a live region (per-percent announcements would be noise).
3. **Screen titles were not heading navigation targets.** `heading()` added to the `HariMuTopBar`
   title, the `HariMuAlertDialog` title, and the `HariMuModalBottomSheet` title, so TalkBack users can
   jump between the screen title and dialog/sheet headings instead of linear-swiping the whole tree.

## 2. Adaptive UI

Verified, no change required:

- `MainActivity` computes `WindowSizeClass` (compact/medium/expanded) and passes width + height class
  into `HariMuAppShell`.
- `HariMuAppShell.shouldUseNavigationRail` switches to a `NavigationRail` at non-compact widths and on
  short (landscape phone) windows, keeping the bottom bar for compact portrait phones; rail items drop
  their label and move it into `contentDescription` when the window is height-constrained.
- Content width is capped by `HariMuResponsiveContainer` / `layout.dashboardMaxWidth` /
  `layout.catalogMaxWidth`, so text measure does not stretch on tablets/foldables; `HomeScreen` uses
  `BoxWithConstraints` for 1/2/3-column tool grids and falls back to one column below 320dp or at
  `fontScale >= 1.5`.
- Studio screens already branch on `maxWidth >= 600.dp` (e.g. `AudioStudioScreen` mixer/timeline) and
  use `FlowRow` for action rows (see `docs/DECISIONS.md` #29).

New verification: `B14AccessibilityAdaptiveTest` asserts the shell picks bottom navigation for the
compact class and a rail for the expanded class, and that the **real** app still renders the
bottom-navigation shell plus the home search field at `w320dp` (foldable cover / small phone).

## 3. Performance

Investigated before changing anything; **no optimisation was applied** because no defect was provable
from source and no profiler/benchmark can run in this environment.

| Candidate | Finding |
| --- | --- |
| Recomposition | Search/filter derivations are wrapped in `remember(...)` with their real inputs (`CatalogScreen.filterAndSortCatalogTools`, `HistoryScreen` filtered lists, `HomeScreen` favourites/recents). No state reads/writes in composition bodies that would loop. |
| Lazy lists | Every `LazyColumn`/`LazyRow` `items(...)` call declares a deterministic `key` (41 call sites checked). `contentType` is absent, but each `items()` block feeds a single homogeneous item type, so slot reuse is already correct; adding `contentType` would be churn, not a measurable win. |
| Media previews | ExoPlayer instances are created in `remember` and released in `DisposableEffect.onDispose` (`VideoPreview`, `VideoTrimPreviewCard`, `AudioPreviewCards`, `ToolResultComponents`, `ToolInputComponents`). |
| Waveforms / bitmaps | `AudioWaveformExtractor` streams peaks from the decoder; `BitmapFactory` usage is bounds-then-sample (`ImageIO`, `ImageEngine`, `VideoForgeBatch2Tools`) instead of decoding originals. Previews are produced off the composition (engine/IO coroutines) and published as state. |
| UI blocking | Long work runs through `Dispatchers.IO`/job workers; export/copy paths hop back with `withContext(Dispatchers.Main)` only for the toast. |
| Resource leaks | Recording/playback/PDF controllers are remembered objects released in `onDispose`; no `Handler.postDelayed` polling loops found in UI code. |

## 4. Lifecycle

- **Rotation / process recreation**: every user-entered value that must survive is `rememberSaveable`
  — search queries (`HomeScreen`, `CatalogScreen`, `FavoritesScreen`, `HistoryScreen`,
  `NusantaraHubScreen`, `VideoForgeScreen`, `ImageForgeScreen`, `AudioForgeScreen`, `PdfVaultScreen`),
  selected filters/sort, recorder format/fps/bitrate/container, mixer panel visibility. Secrets
  (vault alias payloads, backup/S3 passwords) are deliberately *not* saveable.
- **Background/foreground**: UI collects with `collectAsStateWithLifecycle`; media players pause with
  the lifecycle owner (`DisposableEffect(exoPlayer, lifecycleOwner)`), so playback stops when the
  screen leaves RESUMED.
- **Media/job lifecycle**: jobs are owned by `JobManager`/`ToolJobWorker` (WorkManager), not by the
  screen, so rotation or backgrounding does not cancel a running tool; recording services are
  foreground services with their own notification state.
- Not runtime-verified here (no emulator/device available in this environment): real process death,
  real permission revocation while in background.

## 5. Files changed

| File | Change |
| --- | --- |
| `core/design/DesignStatusAndCards.kt` | `HariMuStatusBanner` exposes a live region (assertive for errors, polite otherwise). |
| `core/design/ToolProgressComponents.kt` | Terminal job cards announce their outcome (`jobOutcomeLiveRegion`). |
| `core/design/DesignChrome.kt` | `heading()` on top-bar, dialog and bottom-sheet titles. |
| `app/src/test/java/com/harimu/app/B14AccessibilityAdaptiveTest.kt` | New Robolectric + semantics tests (7). |

## 6. Verification status

Static checks performed locally:

- `awk` line-length check on every edited/new line: all ≤ 120 (matches `.editorconfig`).
- Import ordering checked by hand for the new/edited import blocks (ktlint `import-ordering`).
- No new string resources were needed, so `values/` and `values-en/` parity is untouched.
- `git diff --stat`: 3 production files + 1 test file.

CI (GitHub Actions, `.github/workflows/android-ci.yml`): **the Android job still cannot reach the
compile/test steps.** `Check ktlint formatting` runs before any Gradle task and fails on pre-existing
violations in files unrelated to B14 (first reported: `app/src/test/java/com/harimu/app/
VideoForgeBatch2AndEditorTest.kt`; `ToolProgressComponents.kt` alone still has ~50 pre-existing lines
over 120 characters). Until that repo-wide formatting debt is paid, `assembleDebug`,
`testDebugUnitTest` (which would run the new tests), `lintDebug` and `assembleRelease` are skipped for
every branch, including this one.

Therefore: B14's tests are written but have **not been executed**, and TalkBack was **not** exercised
on a device. Both remain open until the ktlint gate is green.

## 7. Recommended next step

Do a dedicated, behaviour-free formatting batch over the whole `app/src/**` tree (line wrapping,
argument-list wrapping, import ordering) until `Check ktlint formatting` passes, then let CI run the
existing + new unit tests. That single change unblocks verification for B13 and B14 alike.
