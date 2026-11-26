# Video Forge Tools Reference (VID-01 through VID-30)

This document lists all 30 Video Forge tools with their engine, parameters, and capabilities.

## Core Editing (7 tools)

| Tool ID | Title | Engine | Input | Key Parameters |
|---------|-------|--------|-------|----------------|
| VID-01 | Trim Video | M3/FF | SingleVideo | startSec (0-3600s), endSec (0-3600s, 0=end) |
| VID-02 | Merge Videos | M3/FF | MultipleVideos (min 2) | None (concatenates sequentially) |
| VID-03 | Crop Video | M3/FF | SingleVideo | ratio: 1:1, 16:9, 9:16 |
| VID-04 | Resize Video | M3/FF | SingleVideo | res: 1080p, 720p, 480p, 360p |
| VID-05 | Change Video Speed | M3/FF | SingleVideo | speed (0.25x-4.0x), includeAudio (bool) |
| VID-09 | Rotate Video | M3/FF | SingleVideo | deg: 90, 180, 270 |
| VID-10 | Flip Video | M3/FF | SingleVideo | dir: Horizontal, Vertical |

## Audio (5 tools)

| Tool ID | Title | Engine | Input | Key Parameters |
|---------|-------|--------|-------|----------------|
| VID-08 | Video to Audio (MP3) | FF | SingleVideo | bitrate: 128k, 192k, 256k, 320k |
| VID-12 | Loop Video | FF | SingleVideo | loops: 2-10 |
| VID-13 | Change Video Volume | FF | SingleVideo | vol: 0-200% |
| VID-16 | Add Audio to Video | FF | VideoAndAudio | mode: Mix/Replace, addedVol, origVol, offsetSec |
| VID-24 | Remove Video Audio | M3/FF | SingleVideo | None |

## Transform & Convert (5 tools)

| Tool ID | Title | Engine | Input | Key Parameters |
|---------|-------|--------|-------|----------------|
| VID-18 | Video Converter | FF | SingleVideo | fmt: MP4, WebM, AVI, MKV |
| VID-19 | Reverse Video | FF | SingleVideo | includeAudio: bool (segmented for >20s) |
| VID-20 | Video Compressor | M3/FF | SingleVideo | crf: 18-35, scale: Original/720p/480p/360p |
| VID-21 | Video Splitter | M3/FF | SingleVideo | mode: Equal Parts/Range, segSec, startSec, endSec |
| VID-22 | Extract Frames | NAT | SingleVideo | intervalSec: 1-10s, maxFrames: 5-60 |

## Overlays & Effects (7 tools)

| Tool ID | Title | Engine | Input | Key Parameters |
|---------|-------|--------|-------|----------------|
| VID-11 | Stabilize Video | FF | SingleVideo | strength: Light/Medium/Strong, cropBorder: bool |
| VID-14 | Remove Logo (Blur) | FF | SingleVideo | mode: Blur/Pixelate, xPct, yPct, wPct, hPct, strength |
| VID-15 | Add Text to Video | M3/FF | SingleVideo | caption, position, fontStyle, textColor, fontSize, bgBox, startSec, endSec |
| VID-17 | Add Image to Video | M3/FF | VideoAndImage | position, scalePct, opacityPct, startSec, endSec |
| VID-23 | Add Subtitles (SRT/VTT) | FF | VideoAndSubtitle | fontSize, color, style, position |
| VID-25 | Blur Faces | ML/FF | SingleVideo | blurMode, strength, samplingFps |
| VID-26 | Color Grading | M3/FF | SingleVideo | preset, brightness, contrast, saturation, temperature |

## Creative (5 tools)

| Tool ID | Title | Engine | Input | Key Parameters |
|---------|-------|--------|-------|----------------|
| VID-06 | Video to GIF | FF | SingleVideo | startSec, maxSec, fps, width |
| VID-27 | Video Transitions | FF | MultipleVideos (min 2) | transition, durationSec, resolution |
| VID-28 | Green Screen (Chroma Key) | FF | VideoAndMedia | keyColor, similarity, blend, fallbackBg |
| VID-29 | Stop Motion | M3/FF | MultipleImages | fps, resolution, fitMode |
| VID-30 | Time Lapse | FF | SingleVideo | speedFactor: 4x-32x, targetFps, keepAudio |

## Engine Legend

- **M3/FF**: Media3 Transformer with FFmpeg fallback
- **FF**: FFmpeg only
- **ML/FF**: ML Kit (face detection) + FFmpeg
- **NAT**: Native Android APIs (MediaMetadataRetriever)

## Pro Video Editor Integration

The Pro Video Editor (CORE-01) supports the following **inline** operations:
- Trim (clip start/end)
- Speed (0.25x-4.0x)
- Rotation (0°, 90°, 180°, 270°)
- Mute audio per clip
- Clip reorder, duplicate, remove
- Resolution presets: 480p, 720p, 1080p

All other VID tools are accessible via **"Open in Tool"** buttons in the Clip Inspector, which launch the dedicated ToolScreen with the clip pre-filled.

## Limitations (Engine Does Not Support)

The following operations are **not** available in the Pro Video Editor export pipeline:
- Transitions (VID-27)
- Chroma key / Green screen (VID-28)
- Stop motion (VID-29)
- Time lapse (VID-30)
- Color grading (VID-26)
- Stabilization (VID-11)
- Face blur (VID-25)
- Logo blur (VID-14)
- Text overlay (VID-15)
- Image overlay (VID-17)
- Subtitle burning (VID-23)
- Audio mixing/replacement (VID-16)
- Volume adjustment (VID-13)
- Loop (VID-12)
- Reverse (VID-19)

These must be performed using the individual VID tools via "Open in Tool".