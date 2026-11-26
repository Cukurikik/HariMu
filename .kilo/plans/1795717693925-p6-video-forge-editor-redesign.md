# P6 — Video Forge & Pro Video Editor UI Redesign

## Goal
Redesign the HariMu Video Forge experience (VID-01 through VID-30) and Pro Video Editor using actual registered tools and existing engine capabilities. Do not invent editing capabilities beyond engine support.

---

## Design Decisions (Resolved)

| Decision | Resolution |
|----------|------------|
| **Video Forge Architecture** | **Hybrid**: Video Forge launcher screen lists all VID tools with categories/previews; tapping opens dedicated ToolScreen for each tool (consistent with P5 pattern) |
| **Pro Video Editor Scope** | **Hybrid**: Core timeline editing (trim, speed, rotation, mute, reorder, duplicate, remove) stays inline; advanced operations (transitions VID-27, color grading VID-26, text overlay VID-15, image overlay VID-17, subtitles VID-23, stabilization VID-11, face blur VID-25, chroma key VID-28, logo blur VID-14, reverse VID-19, loop VID-12, volume VID-13, add audio VID-16) accessible via "Open in Tool" buttons that launch the dedicated VID tool with the clip pre-filled |
| **Shared Components** | **Both**: Reuse P5 components (`ToolFileSelector`, `ToolParameterPanel`, `ToolProgressView`, `ToolResultCard`, `EngineBadge`, `MonoTextStyle`, `HariMuColors`, `HariMuThemeTokens`); create new video-specific shared components (`VideoPreview`, `TimelineTrack`, `ClipInspector`, `ExportDialog`, `ToolLauncherCard`) |

---

## Engine Capabilities Reference

### Pro Video Editor (VideoEditorEngine) — Supported
- Trim (clip start/end)
- Speed (0.25×–4.0×)
- Rotation (0°, 90°, 180°, 270°)
- Mute audio per clip
- Clip reorder, duplicate, remove
- Resolution presets: 480p, 720p, 1080p
- Project save/load (Room: `VideoProjectEntity`)
- Export: Media3 for single clips, FFmpeg concat for multi-clip

### VID Tools (Batch 1, 1 Extra, 2, 2 Extra) — Engine Operations
| Tool | Engine | Operation |
|------|--------|-----------|
| VID-01 | M3/FF | Trim |
| VID-02 | M3/FF | Merge (concat) |
| VID-03 | M3/FF | Crop (1:1, 16:9, 9:16) |
| VID-04 | M3/FF | Resize (1080p/720p/480p/360p) |
| VID-05 | M3/FF | Speed (0.25×–4×) |
| VID-06 | FF | Video → GIF (2-pass palette) |
| VID-08 | FF | Video → Audio (MP3) |
| VID-09 | M3/FF | Rotate (90/180/270) |
| VID-10 | M3/FF | Flip (H/V) |
| VID-11 | FF | Stabilize (deshake) |
| VID-12 | FF | Loop (N×) |
| VID-13 | FF | Volume (0–200%) |
| VID-14 | FF | Logo blur (ROI blur/pixelate) |
| VID-15 | M3/FF | Text overlay (timed or full) |
| VID-16 | FF | Add audio (mix/replace + offset) |
| VID-17 | M3/FF | Image overlay (timed, scale, opacity) |
| VID-18 | FF | Convert (MP4/WebM/AVI/MKV) |
| VID-19 | FF | Reverse (segmented for >20s) |
| VID-20 | M3/FF | Compress (CRF + optional downscale) |
| VID-21 | M3/FF | Split (equal parts or range → ZIP) |
| VID-22 | NAT | Extract frames (PNG → ZIP) |
| VID-23 | FF | Burn subtitles (SRT/VTT → ASS) |
| VID-25 | ML/FF | Face blur (ML Kit sampling + tracking) |
| VID-26 | M3/FF | Color grading (brightness, contrast, saturation, temp, LUT presets) |
| VID-27 | FF | Transitions (xfade: fade, dissolve, wipe, slide, circle) |
| VID-28 | FF | Chroma key (green/blue/white + background media) |
| VID-29 | M3/FF | Stop motion (images → MP4) |
| VID-30 | FF | Time lapse (4×–32× speedup) |

### Engine Limitations (Document in UI)
- No native timeline transitions (VID-27 is separate concat tool)
- No chroma key in Pro Editor export pipeline
- No stop motion / time lapse in Pro Editor
- No color grading in Pro Editor
- No face/logo blur in Pro Editor
- No text/image overlay in Pro Editor
- No subtitle burning in Pro Editor
- No stabilization in Pro Editor

---

## Task Breakdown

### Phase 1: Shared Video Components (`/core/design/video/`)
Create new video-specific composables in `com.harimu.app.core.design.video`:

1. **`VideoPreview.kt`**
   - ExoPlayer-based preview with `ContentFrame`
   - Play/pause, seek bar, time display, speed indicator
   - Responsive sizing (adaptive height, landscape full-width)
   - Touch-friendly controls (large hit targets ≥48dp)
   - Trim handle overlay (visual range markers)
   - Accessibility: content descriptions, TalkBack support

2. **`TimelineTrack.kt`**
   - Horizontal scrollable track with clip thumbnails
   - Visual duration bars proportional to effective duration
   - Selected clip highlight, drag-to-reorder (future)
   - Waveform placeholder (optional, if Media3 provides)
   - Zoom slider (pinch/zoom for timeline scale)
   - Landscape: full-width, multi-row if needed

3. **`ClipInspector.kt`**
   - Extract from `SelectedClipInspector` in `VideoEditorScreen.kt`
   - Trim RangeSlider (with min 0.3s constraint)
   - Speed slider (0.25×–4×, snap to 0.25× increments)
   - Quick speed chips (0.5×, 1×, 1.5×, 2×)
   - Rotation chips (0°, 90°, 180°, 270°)
   - Mute toggle (`ParamToggle`)
   - "Open in Tool" buttons for advanced ops (link to VID tools)

4. **`ExportDialog.kt`**
   - Resolution picker (480p/720p/1080p chips)
   - Estimated output size / duration preview
   - Export progress (reuse `ToolProgressView`)
   - Save to gallery / share / open actions (reuse `ToolResultCard`)

5. **`ToolLauncherCard.kt`**
   - Card for Video Forge tool grid/list
   - Icon, title, description, engine badge (M3/FF/FF/ML/NAT)
   - "Open" action → launches `ToolScreen` with toolId
   - Category grouping (Trim/Merge, Transform, Audio, Effects, Advanced)

---

### Phase 2: Video Forge Launcher Screen
**New file:** `VideoForgeScreen.kt` in `feature/studios/`

- **Layout**: Scaffold with top bar ("Video Forge"), scrollable content
- **Categories** (expandable sections):
  1. **Core Editing**: VID-01 Trim, VID-02 Merge, VID-03 Crop, VID-04 Resize, VID-05 Speed, VID-09 Rotate, VID-10 Flip
  2. **Audio**: VID-08 Extract Audio, VID-12 Loop, VID-13 Volume, VID-16 Add Audio, VID-24 Remove Audio
  3. **Transform & Convert**: VID-18 Convert, VID-19 Reverse, VID-20 Compress, VID-21 Split, VID-22 Extract Frames
  4. **Overlays & Effects**: VID-11 Stabilize, VID-14 Logo Blur, VID-15 Text Overlay, VID-17 Image Overlay, VID-23 Subtitles, VID-25 Face Blur, VID-26 Color Grading
  5. **Creative**: VID-06 GIF, VID-27 Transitions, VID-28 Chroma Key, VID-29 Stop Motion, VID-30 Time Lapse
- **Each tool**: `ToolLauncherCard` with icon, title, 1-line desc, engine badge
- **Tap action**: Navigate to `ToolScreen(toolId = "VID-XX", ...)`
- **Search/Filter**: Optional text filter across tool titles
- **Accessibility**: Semantic headings, proper focus order

---

### Phase 3: Pro Video Editor Improvements
**Modify:** `VideoEditorScreen.kt`, `VideoEditorViewModel.kt`

#### 3.1 Preview Area (`VideoPreview` component)
- Replace fixed 185dp height with responsive sizing:
  - Portrait: 40% screen height, min 200dp
  - Landscape: full width, height = width × 9/16 (16:9 canvas)
- Add playback feedback: loading spinner, error state, buffering indicator
- Seek behavior: drag seek bar → preview frame update (ExoPlayer `seekTo`)
- Trim handles: visual markers on seek bar matching `RangeSlider` values

#### 3.2 Timeline (`TimelineTrack` component)
- Replace `LazyRow` of cards with visual timeline track:
  - Each clip = colored bar proportional to effective duration
  - Thumbnail at start (Media3 `MediaMetadataRetriever` or first frame)
  - Selected clip: elevated, border, shows trim handles on track
  - Reorder: long-press drag (or keep move L/R buttons for accessibility)
  - Zoom: slider to scale timeline horizontally
- Compact mode: single row; expanded: multi-row wrapping

#### 3.3 Clip Inspector (`ClipInspector` component)
- Extract inline composable to shared `ClipInspector.kt`
- Add "Open in Tool" row for advanced ops:
  - "Trim precisely" → VID-01 (pre-fills start/end)
  - "Change speed" → VID-05 (pre-fills speed)
  - "Rotate/Flip" → VID-09/VID-10
  - "Color grade" → VID-26
  - "Add text" → VID-15
  - "Add image/logo" → VID-17
  - "Blur face/logo" → VID-25/VID-14
  - "Stabilize" → VID-11
  - "Add subtitles" → VID-23
  - "Reverse" → VID-19
  - "Loop" → VID-12
  - "Adjust volume" → VID-13
  - "Add background audio" → VID-16
  - "Chroma key" → VID-28
- Each button: `ToolLauncherCard` compact variant → launches `ToolScreen` with clip URI pre-selected via `ToolViewModel` initial state

#### 3.4 Export (`ExportDialog` component)
- Replace inline export button with dialog:
  - Resolution chips (480p/720p/1080p)
  - Estimated duration (from `totalTimelineDurationMs`)
  - Estimated file size (rough: bitrate × duration)
  - Progress sheet during export (reuse `ToolProgressView`)
  - Result actions: Open, Share, Save to folder (reuse `ToolResultCard`)

#### 3.5 Project State & Persistence
- Auto-save on any change (debounced 1s) — already in `persistCurrentProjectQuietly()`
- Show "Last saved" timestamp in top bar
- New project / Load project / Delete project (existing dialog, enhance UI)

#### 3.6 Landscape Optimization
- Detect orientation via `LocalConfiguration.current.orientation`
- Landscape: Preview full-width left, Timeline + Inspector right (split pane)
- Portrait: Stacked (Preview → Timeline → Inspector)
- Use `BoxWithConstraints` or `WindowSizeClass` for adaptive layout

#### 3.7 Accessibility
- All interactive elements: `contentDescription`, `testTag`
- Focus order: Preview → Timeline → Inspector → Export
- TalkBack: announce clip selection, trim values, playback state
- Color contrast: `HariMuColors` tokens meet WCAG AA

---

### Phase 4: Engine Integration & "Open in Tool" Flow
**Modify:** `ToolViewModel.kt` (or create `VideoForgeToolViewModel`)

- Add `preFillFromClip(uri: Uri, params: Map<String, Any>)` to `ToolViewModel`
- When "Open in Tool" pressed from Pro Editor:
  1. Navigate to `ToolScreen(toolId = "VID-XX")`
  2. `ToolViewModel` receives clip URI + pre-fill params
  3. Auto-select primary input (and secondary if needed, e.g., VID-16 audio)
  4. Pre-fill relevant parameters (trim start/end, speed, rotation, etc.)
- Handle back navigation: return to Pro Editor with result (optional: auto-replace clip)

---

### Phase 5: Tests
**New test files:**
1. `VideoForgeScreenTest.kt` — UI tests for tool launcher, categories, navigation
2. `VideoEditorScreenIntegrationTest.kt` — Extend existing; test timeline interactions, inspector, export dialog, landscape
3. `VideoPreviewTest.kt` — Preview playback, seek, trim handle sync
4. `TimelineTrackTest.kt` — Clip rendering, selection, zoom, reorder
5. `ClipInspectorTest.kt` — Parameter updates, "Open in Tool" actions
5. `ExportDialogTest.kt` — Resolution selection, progress, result actions

---

### Phase 6: Documentation
**Update/Create:**
1. `VIDEO_FORGE_TOOLS.md` — Table of all 30 VID tools with engine, params, limitations
2. `PRO_VIDEO_EDITOR_GUIDE.md` — Timeline workflow, inspector, export, "Open in Tool" integration
3. `VIDEO_ENGINE_CAPABILITIES.md` — What Media3/FFmpeg supports vs. not supported
4. Inline KDoc on new shared components (`VideoPreview`, `TimelineTrack`, etc.)

---

## File Map (New / Modified)

### New Files
```
/core/design/video/VideoPreview.kt
/core/design/video/TimelineTrack.kt
/core/design/video/ClipInspector.kt
/core/design/video/ExportDialog.kt
/core/design/video/ToolLauncherCard.kt
/feature/studios/VideoForgeScreen.kt
```

### Modified Files
```
/feature/studios/VideoEditorScreen.kt         → Use new shared components, adaptive layout
/feature/studios/VideoEditorViewModel.kt      → Add pre-fill support for "Open in Tool"
/feature/toolscreen/ToolViewModel.kt          → Add preFillFromClip() for VID tools
```

---

## Validation Plan

| Check | Method |
|-------|--------|
| Video Forge launches all 30 VID tools | Manual tap each `ToolLauncherCard` → verify `ToolScreen` opens with correct toolId |
| Pro Editor core editing works | Add clips → trim → speed → rotate → mute → reorder → export → verify output |
| "Open in Tool" pre-fills params | From inspector, tap "Color grade" → VID-26 opens with clip selected, preset=Manual |
| Landscape layout switches correctly | Rotate device/emulator → preview left, timeline+inspector right |
| Export progress + result actions | Export → ProgressSheet shows % → Done → Open/Share/Save work |
| Accessibility | TalkBack navigation, content descriptions, contrast check |
| No fake timeline features | Verify transitions, chroma key, etc. only via "Open in Tool" |

---

## Out of Scope
- Implementing missing engine features (transitions, chroma key, etc. in Pro Editor export)
- Waveform rendering (requires Media3 audio processor integration)
- Multi-track timeline (video + audio + overlay tracks)
- Collaborative editing / cloud sync
- AI-powered features (auto-trim, smart crop, etc.)

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Android SDK unavailable in sandbox (no local compile/test) | Rely on CI/CD for validation; write tests that can run headless; manual verify on device |
| "Open in Tool" navigation complexity | Use single-activity Compose navigation; pass clip URI via `ToolViewModel` factory |
| Landscape/portrait state loss | Hoist state to ViewModel; use `rememberSaveable` for UI-only state |
| Performance with many timeline clips | Lazy rendering, thumbnail caching, limit preview resolution |

---

## Rollout
1. Merge shared video components first (independent, reusable)
2. Video Forge Screen (standalone, no Pro Editor dependency)
3. Pro Editor refactor (uses shared components)
4. "Open in Tool" integration (connects both)
5. Tests + docs
6. CI validation → merge to main