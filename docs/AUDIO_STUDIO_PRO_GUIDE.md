# Audio Studio Pro Guide (AUD-21)

## Overview

Audio Studio Pro (`AUD-21`) is HariMu's multi-track audio arrangement and mixing workspace built on `AudioStudioEngine` (FFmpeg multi-input `adelay`, `atrim`, `volume`, `pan`, and `amix`) and `AudioWaveformExtractor` (streaming `MediaCodec` + disk/memory LRU peak cache). Live timeline preview uses per-clip Media3 `ExoPlayer` instances via `AudioStudioPlaybackController`.

## Architecture

### Feature Files
- `feature/studios/AudioStudioScreen.kt` — Adaptive multi-track timeline, mixer drawer/pane, project switcher, transport bar, and export workflow.
- `feature/studios/AudioStudioViewModel.kt` — Room persistence (`WorkspaceRepository`), track/clip mutations, async waveform peak extraction, and cancellable FFmpeg export jobs.
- `feature/studios/AudioStudioPlaybackController.kt` — Per-clip `ExoPlayer` management with `ClippingConfiguration`, timeline offset sync, clip gain, track volume, M/S, and master gain.
- `feature/studios/AudioForgeScreen.kt` — Unified 6-category launcher for all 28 `AUD-*` tools plus direct studio shortcuts.
- `feature/studios/EqualizerScreen.kt` & `EqualizerFxController.kt` — 10-band live `DynamicsProcessing` (API 28+) / 5-band fallback EQ + waveform scrubbing + FFmpeg render.

### Shared Audio UI Components (`core/design/audio/`)
- `AudioTimelineModels.kt` — `AudioTimelineMath` (density-free dp/ms scaling, ruler ticks, `slicePeaksForClip`, `computeClipEffectivePlaybackGain`, `activeClipsSummary`, `estimateAudioExportSizeBytes`) and `WaveformOverlaySpec`.
- `AudioTimelineTrack.kt` — `AudioTimeRuler` (interactive tap/drag seek), `AudioTimelineTrackRow` (color strip, clip count, M/S pills, sliced waveforms), and density-accurate `AudioPlayheadOverlay`.
- `AudioClipInspector.kt` — Source trim (`RangeSlider`, min 250 ms), timeline offset (`Slider` + `To 0s`, `-0.5s`, `+0.5s` chips), clip gain (`-24..+24 dB`), delete clip, and categorized "Open in Tool" handoffs via `ToolPrefillData`.
- `AudioStudioExportDialog.kt` — `WAV` / `MP3` / `FLAC` selection, active track/clip summary (Mute/Solo aware), estimated duration & size, real job cancellation (`vm.cancelExport()`), retry on failure, and Open / Share / Save (SAF or default folder) / Cloud Upload actions.
- `AudioToolLauncherCard.kt` — Hero studio launcher card, search bar, collapsible category grids, and Audio Forge tool cards.
- `AudioForgeVisualPreviews.kt` — Live parameter-driven cards for `AUD-01`, `AUD-05`, `AUD-11`, `AUD-18`, `AUD-24`, `AUD-25`, `AUD-28`, and waveform overlay specs (`AUD-03`, `AUD-16`, `AUD-23`, `AUD-26`).

## Supported Capabilities & Honest Scope

- **Track Management**: 1 to 8 tracks (`addTrack`, `renameTrack`, `deleteTrack`), Volume (`0.0x..2.0x`), Pan (`-1.0..+1.0`), Mute (`M`), Solo (`S`).
- **Clip Operations**: Source trim (`trimStartMs..trimEndMs`, min 250 ms), timeline offset (`offsetMs >= 0`), clip gain (`-24..+24 dB`), and clip deletion.
- **Live Preview Fidelity**: `AudioStudioPlaybackController` applies clip trim, timeline offset, clip gain, track volume, Mute/Solo, and master volume in real time. Per-track stereo L/R panning (`pan`) is applied during FFmpeg `amix` export and is noted transparently in the UI.
- **Adaptive Behavior**:
  - Compact screens (`< 600.dp`): Timeline pane is primary; `audio_studio_mixer_toggle` swaps cleanly to full-width `Tracks Mixer`.
  - Tablets & Landscape (`>= 600.dp`): Mixer and Timeline display side-by-side when toggled open; `EqualizerScreen` switches to a two-pane studio layout at `>= 720.dp`; `AudioForgeScreen` switches to a 2-column grid per category at `>= 720.dp`.
