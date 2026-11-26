# HariMu Design System Specification (Phase P1)

> **Package:** `com.harimu.app.core.design`  
> **Source Files:** `Tokens.kt`, `Theme.kt`, `DesignSurfaces.kt`, `DesignChrome.kt`, `DesignInputsAndActions.kt`, `DesignStatusAndCards.kt`, `DesignFeedback.kt`, `ComponentPreviews.kt`  
> **Directive:** `/ui_agent.md` + `/docs/FRONTEND-AUDIT.md`

---

## 1. Visual Identity & Principles

HariMu's visual language is **Studio Precision**: a calm, high-contrast utility workbench designed for multi-step media, document, cryptographic, and civic tools.

- **Distinctive Accent Pairing:** Studio Vermilion (`ElectricOrange` / `ElectricOrangeDark`) anchors primary actions and active navigation states, paired with `TealEmerald` for completion/success and seven calibrated `ToolGroup` accents for rapid scanning across 182 tools.
- **Restrained Tonal Hierarchy Over Shadows:** Cards rely on explicit `surfaceContainer*` tonal steps and `1.dp` hairline borders (`HariMuElevationTokens.card = 0.dp`) instead of drop shadows, glassmorphism, or decorative gradients.
- **No Nested Cards:** Nested groupings inside a `HariMuSectionCard` use `HariMuInsetPanel` (`surfaceContainerLow`, borderless) rather than cards inside cards.
- **Dynamic Color Harmony:** When `dynamicColor = true` on Android 12+, Material You adapts primary/surface roles while preserving AMOLED pure black (`#000000`) when selected and keeping semantic status colors (`success`, `warning`, `info`) clear and contrast-checked.

---

## 2. Token Architecture (`Tokens.kt` & `HariMuThemeTokens`)

Access all tokens through `HariMuThemeTokens` inside `@Composable` scope:

| Token Group | Accessor | Key Values |
|---|---|---|
| **Spacing** | `HariMuThemeTokens.spacing` | `xxs=4.dp`, `xs=8.dp`, `sm=12.dp`, `md=16.dp`, `lg=24.dp`, `xl=32.dp`, `xxl=40.dp` |
| **Layout** | `HariMuThemeTokens.layout` | `formMaxWidth=640.dp`, `catalogMaxWidth=760.dp`, `dashboardMaxWidth=1040.dp`, `compactScreenPadding=16.dp`, `mediumScreenPadding=24.dp`, `cardBorderAlpha=0.55f`, `dividerAlpha=0.25f` |
| **Elevation** | `HariMuThemeTokens.elevation` | `none=0.dp`, `card=0.dp`, `subtle=1.dp`, `bar=2.dp`, `dialog=6.dp`, `sheet=6.dp` |
| **Borders** | `HariMuThemeTokens.borders` | `hairline=1.dp`, `focused=1.5.dp`, `selected=2.dp`, `subtleAlpha=0.42f`, `defaultAlpha=0.62f`, `strongAlpha=0.85f` |
| **Icons** | `HariMuThemeTokens.icons` | `xs=16.dp`, `sm=18.dp`, `md=20.dp`, `lg=24.dp`, `xl=28.dp`, `tile=44.dp`, `emptyContainer=56.dp` |
| **Shapes** | `HariMuThemeTokens.shapes` | `badge=6.dp`, `chip=10.dp`, `iconTile=12.dp`, `control=12.dp`, `card=16.dp`, `container=20.dp`, `dialog=24.dp`, `sheet=24.dp` top corners |
| **Controls** | `HariMuThemeTokens.controls` | `minTouchTarget=48.dp`, `buttonMinHeight=48.dp`, `compactButtonMinHeight=40.dp`, `inputMinHeight=52.dp`, `searchBarHeight=52.dp`, `topBarHeight=64.dp` |
| **Motion** | `HariMuThemeTokens.motion` | `fastMs=150`, `standardMs=220`, `emphasizedMs=280` |
| **Semantic Colors** | `HariMuThemeTokens.semantic` | `success*`, `warning*`, `info*`, `favoriteAccent`, `contrastSafeAccent(accent)` |

---

## 3. Color & Surface System (`Theme.kt`)

### Theme Modes
- `ThemeMode.SYSTEM`: Follows `isSystemInDarkTheme()`.
- `ThemeMode.LIGHT`: Alabaster canvas (`#F6F7FB`), Crisp White primary surfaces (`#FFFFFF`), high-contrast Light Vermilion primary (`#C8430C`), and contrast-darkened group text badges via `contrastSafeAccent`.
- `ThemeMode.DARK`: Deep Obsidian canvas (`#11141C`), Slate surfaces (`#171C26` -> `#2A3143`), Electric Orange primary (`#FF6B2C`).
- `ThemeMode.AMOLED`: True Pure Black background (`#000000`), near-black `AmoledCard` (`#0B0E14`), crisp outline borders (`#2A3346`).

### Explicit Surface Container Hierarchy
All three static themes (`LightColors`, `DarkColors`, `AmoledColors`) define the full Material 3 surface ladder (`surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`, `outline`, `outlineVariant`, `tertiary*`, `errorContainer*`) so Studio and Tool components never fall back to mismatched default grays.

---

## 4. Typography (`Tokens.kt`)

Bundled local typefaces (`res/font/plus_jakarta_sans.ttf`, `res/font/jetbrains_mono.ttf`) populate **all 15 Material 3 typography slots**:

- **UI Display / Headline / Title / Body / Label (`PlusJakartaSans`):**
  - `displayLarge` (34.sp) through `displaySmall` (26.sp)
  - `headlineLarge` (24.sp) through `headlineSmall` (19.sp)
  - `titleLarge` (17.sp), `titleMedium` (15.sp), `titleSmall` (13.sp)
  - `bodyLarge` (15.sp), `bodyMedium` (13.5.sp), `bodySmall` (12.sp)
  - `labelLarge` (14.sp SemiBold) & `labelMedium` (12.sp SemiBold)
- **Technical / ID / Numeric Readouts (`JetBrainsMono` + `tnum` tabular figures):**
  - `labelSmall` (10.5.sp) for engine/capability badges
  - `MonoTextStyle` (12.sp) for tool IDs (`VID-01`), timestamps, bitrates, file paths, and progress percentages.

---

## 5. Shared Composable Catalog

| File | Composables | Purpose |
|---|---|---|
| `DesignSurfaces.kt` | `HariMuResponsiveContainer`, `HariMuSectionCard`, `HariMuElevatedCard`, `HariMuInsetPanel`, `HariMuDivider` | Centered max-width tablet containers, tonal card hierarchy, borderless inset panels |
| `DesignChrome.kt` | `HariMuTopBar`, `HariMuNavigationDefaults`, `HariMuAlertDialog`, `ModelDownloadDialog`, `HariMuModalBottomSheet` | Consistent sub-screen top bars (`R.string.cd_back`), nav bar/rail indicator defaults, M3 dialogs & sheets |
| `DesignInputsAndActions.kt` | `HariMuPrimaryButton`, `HariMuSecondaryButton`, `HariMuDestructiveButton`, `HariMuTextAction`, `HariMuSearchBar`, `HariMuTextField`, `ParamSlider`, `ParamDropdown`, `ParamToggle`, `FilePickerField` | `48.dp` touch-target action buttons, unified search bar (`R.string.cd_clear`), tool parameter controls |
| `DesignStatusAndCards.kt` | `ToolIdBadge`, `EngineBadge`, `CapabilityBadge`, `HariMuStatusBanner`, `ToolCard`, `ToolRowCard`, `GroupHeader` | Contrast-safe badges, 4-tone status banners, tool discovery cards & headers |
| `DesignFeedback.kt` | `ProgressSheet`, `ResultCard`, `EmptyState`, `EmptyStateCard`, `ErrorState` | WorkManager progress feedback, responsive `FlowRow` result actions, empty/error states |

---

## 6. Accessibility & Localization Defaults

1. **Touch Targets:** All buttons, icon buttons, filter chips, and toggle rows apply `.minimumInteractiveComponentSize()` (`>= 48.dp`).
2. **Semantics & Roles:** `ToolCard` uses `Role.Button`; `ParamToggle` uses `Modifier.toggleable(role = Role.Switch)` on the full row so TalkBack announces row label + switch state as one atomic control.
3. **Font Scaling Safety:** `ResultCard` uses `FlowRow` for action buttons; `ToolCard` badge rows wrap via `FlowRow`; typography uses `sp` units throughout.
4. **Localization:** Shared chrome/status labels (`cd_back`, `cd_clear`, `cd_close`, `status_info`, `status_warning`, `status_success`, etc.) live in both `res/values/strings.xml` and `res/values-en/strings.xml`.
