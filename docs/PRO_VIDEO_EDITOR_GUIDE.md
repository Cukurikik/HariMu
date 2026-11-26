# Pro Video Editor Guide

## Overview

The Pro Video Editor (CORE-01) is a dedicated timeline-based video editing workspace built on Media3 Transformer and FFmpeg. It provides core editing capabilities inline, with advanced operations accessible via "Open in Tool" buttons that launch dedicated VID tool screens.

## Architecture

### Core Components

1. **VideoEditorScreen.kt** - Main Compose UI
2. **VideoEditorViewModel.kt** - State management (Room persistence, clip operations, export)
3. **VideoEditorEngine.kt** - Export pipeline (Media3 for single clips, FFmpeg concat for multi-clip)
4. **VideoEditorMath.kt** - Timeline math (effective duration, rotation normalization, FFmpeg args)

### Shared Video Components (P6)

Located in `core/design/video/`:
- **VideoPreview.kt** - ExoPlayer-based preview with trim handles, playback controls, speed indicator
- **TimelineTrack.kt** - Visual timeline with thumbnails, zoom, clip selection, time ruler
- **ClipInspector.kt** - Parameter panel (trim, speed, rotation, mute) + "Open in Tool" actions
- **ExportDialog.kt** - Resolution picker, estimates, progress, result actions
- **ToolLauncherCard.kt** - Tool cards for Video Forge launcher

## User Workflow

### 1. Create/Load Project
- New project: Empty timeline, default title "Untitled Project"
- Load project: From Room database (Saved Projects dialog)
- Auto-save: On every change (debounced 1s)

### 2. Add Clips
- Tap "Add Video Clips" button
- Select multiple videos (max 12)
- Clips appended to timeline end
- First clip auto-selected

### 3. Arrange Timeline
- **Visual track**: Clips shown as proportional bars with thumbnails
- **Reorder**: Long-press drag or Move L/R buttons
- **Duplicate**: Copy clip (inserts after original)
- **Remove**: Delete from timeline
- **Zoom**: Slider to scale timeline horizontally

### 4. Edit Selected Clip
Tap a clip to select and open **Clip Inspector**:

| Parameter | Control | Range | Notes |
|-----------|---------|-------|-------|
| Trim | RangeSlider | 0 - clip duration | Min 0.3s, syncs with preview |
| Speed | Slider + Quick chips | 0.25x - 4.0x | Snaps to 0.25x increments |
| Rotation | Chips | 0°, 90°, 180°, 270° | Normalized |
| Mute | Toggle | On/Off | Per-clip |

### 5. Advanced Operations ("Open in Tool")
For operations not supported inline, Clip Inspector shows **ToolActionButton** cards:

| Category | Tools | VID IDs |
|----------|-------|---------|
| Transform | Trim Precise, Speed, Rotate/Flip | VID-01, VID-05, VID-09/10 |
| Color | Color Grading | VID-26 |
| Overlays | Text, Image, Subtitles | VID-15, VID-17, VID-23 |
| Effects | Stabilize, Face Blur, Logo Blur | VID-11, VID-25, VID-14 |
| Audio | Volume, Add Audio | VID-13, VID-16 |
| Creative | Reverse, Loop, Chroma Key | VID-19, VID-12, VID-28 |

**Flow:**
1. User taps "Open in Tool" button
2. `ToolPrefillData.setPrefill(uri, params)` stores clip URI + relevant params
3. Navigation to `ToolRoute(toolId)`
4. `ToolViewModel` consumes prefill on init, populates inputs/params
5. User runs tool, gets result
6. Back navigation returns to Pro Editor

### 6. Export
- Tap "Export" → ExportDialog opens
- Select resolution: 480p / 720p / 1080p
- Shows estimated duration & file size
- Export runs via `VideoEditorEngine.exportTimeline()`
- Progress shown via `ProgressSheet`
- Result: `ResultCard` with Open/Share/Save actions

## Adaptive Layout

### Portrait (Default)
- Stacked: Preview → Timeline → Inspector
- Preview: 40% screen height (min 200dp)
- Full-width timeline scrollable
- Inspector below timeline

### Landscape
- Split pane: Preview (60% width) | Timeline + Inspector (40% width)
- Preview: Full height, 16:9 aspect
- Timeline + Inspector in scrollable column on right
- Better for precise editing

## Accessibility

- All interactive elements: `contentDescription`, `testTag`
- Focus order: Preview → Timeline → Inspector → Export
- TalkBack announces: clip selection, trim values, playback state
- Color contrast: HariMuColors tokens meet WCAG AA
- Touch targets: ≥48dp (Material 3 default)

## Project Persistence

- Room entity: `VideoProjectEntity`
- Fields: id, title, clipsJson, outputResolution, createdAt, updatedAt, lastExportPath
- Clips serialized as JSON via `WorkspaceRepository.encodeEditorClips()`
- Auto-save on every mutation
- Manual save via "Save" button in top bar

## Export Pipeline

```
VideoEditorEngine.exportTimeline()
├── Validate clips (VideoToolMath.validateVideoInputs)
├── Determine canvas dimensions (landscape/portrait)
├── For each clip:
│   ├── Try Media3 Transformer (if audio handling matches)
│   │   ├── Build Media3Request with clip params
│   │   └── Run transformer
│   └── Fallback to FFmpeg
│       ├── Copy URI to temp file
│       ├── Build FFmpeg args (trim, speed, rotation, scale, pad)
│       └── Run mediaJob.runStep()
├── If single clip: copy segment to output
├── If multi-clip:
│   ├── Try Media3 concat
│   └── Fallback: FFmpeg concat (demuxer)
└── Probe output, emit ToolProgress.Done
```

## Engine Capabilities Matrix

| Feature | Inline Support | Via "Open in Tool" | Not Supported |
|---------|----------------|-------------------|---------------|
| Trim | ✅ | VID-01 | - |
| Speed | ✅ | VID-05 | - |
| Rotation | ✅ | VID-09/10 | - |
| Mute | ✅ | VID-24 | - |
| Reorder | ✅ | - | - |
| Duplicate | ✅ | - | - |
| Resolution | ✅ | VID-04 | - |
| Transitions | ❌ | VID-27 | - |
| Chroma Key | ❌ | VID-28 | - |
| Color Grading | ❌ | VID-26 | - |
| Stabilization | ❌ | VID-11 | - |
| Face Blur | ❌ | VID-25 | - |
| Logo Blur | ❌ | VID-14 | - |
| Text Overlay | ❌ | VID-15 | - |
| Image Overlay | ❌ | VID-17 | - |
| Subtitles | ❌ | VID-23 | - |
| Audio Mix | ❌ | VID-16 | - |
| Volume | ❌ | VID-13 | - |
| Loop | ❌ | VID-12 | - |
| Reverse | ❌ | VID-19 | - |
| Stop Motion | ❌ | VID-29 | - |
| Time Lapse | ❌ | VID-30 | - |
| GIF | ❌ | VID-06 | - |
| Extract Audio | ❌ | VID-08 | - |
| Compress | ❌ | VID-20 | - |
| Split | ❌ | VID-21 | - |
| Extract Frames | ❌ | VID-22 | - |

## Testing

Run tests:
```bash
./gradlew :app:testDebugUnitTest --tests "com.harimu.app.VideoEditorComponentsTest"
./gradlew :app:testDebugUnitTest --tests "com.harimu.app.VideoForgeScreenTest"
```

## Future Enhancements

1. **Multi-track timeline**: Separate video, audio, overlay tracks
2. **Waveform rendering**: Audio visualization on timeline
3. **Keyframe animation**: Animate parameters over time
4. **Preview cache**: Thumbnail generation for faster scrubbing
5. **Collaborative editing**: Cloud sync via workspace backup
6. **AI-assisted**: Auto-trim, smart crop, scene detection