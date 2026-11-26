# Video Engine Capabilities Matrix

This document details what the HariMu video processing engine (Media3 Transformer + FFmpeg) supports vs. what requires separate VID tools.

## Media3 Transformer Capabilities

### Supported (Native Media3)

| Feature | API | Notes |
|---------|-----|-------|
| Trim (start/end) | `MediaItem.ClippingConfiguration` | Frame-accurate with `trimOptimization` |
| Speed | `EditedMediaItem.setSpeed(SpeedProvider)` | 0.25x - 4.0x, constant speed |
| Rotation | `ScaleAndRotateTransformation` | 0°, 90°, 180°, 270° |
| Flip (H/V) | `ScaleAndRotateTransformation` | Negative scale values |
| Scale/Pad | `Presentation.createForWidthAndHeight` | `LAYOUT_SCALE_TO_FIT` + pad |
| Crop (aspect) | `Presentation.createForAspectRatio` | `LAYOUT_SCALE_TO_FIT_WITH_CROP` |
| Remove Audio | `EditedMediaItem.setRemoveAudio(true)` | Clean audio strip |
| Concatenation | `EditedMediaItemSequence` | Multiple inputs, same resolution |
| Image Sequence | `EditedMediaItemSequence.withVideoFrom` | Stop motion, custom frame duration |
| Custom Effects | `Effects` + `Effect` | Overlays, color filters (limited) |

### Media3 Limitations

| Limitation | Workaround |
|------------|------------|
| No variable speed (ramping) | Use FFmpeg `setpts` with expression |
| No crossfade transitions | Use FFmpeg `xfade` filter |
| No chroma key | Use FFmpeg `chromakey` filter |
| No stabilization | Use FFmpeg `deshake` filter |
| No face detection | Use ML Kit + FFmpeg overlay |
| No complex filter graphs | Fallback to FFmpeg |
| Audio filters limited | Use FFmpeg `af` filters |

## FFmpeg Capabilities (Full)

FFmpeg is used as fallback for all operations Media3 cannot handle, and as primary for:

### Video Filters (Complete)

| Category | Filters | Examples |
|----------|---------|----------|
| Transform | `scale`, `pad`, `crop`, `rotate`, `hflip`, `vflip`, `transpose` | All geometric transforms |
| Speed | `setpts`, `atempo` | Variable speed, audio sync |
| Color | `colorbalance`, `eq`, `lut3d`, `curves`, `hue`, `saturation` | Full grading |
| Blur | `gblur`, `boxblur`, `avgblur`, `removelogo` | ROI blur, face blur |
| Overlay | `overlay`, `blend` | Text, image, watermark |
| Transitions | `xfade`, `acrossfade` | 20+ transition types |
| Chroma Key | `chromakey` | Green/blue screen |
| Stabilization | `deshake`, `vidstabdetect` + `vidstabtransform` | Motion compensation |
| Audio | `volume`, `adelay`, `amix`, `acrossfade`, `atempo` | Mix, delay, sync |

### FFmpeg Limitations (Mobile)

| Limitation | Impact |
|------------|--------|
| No hardware encoding (libx264 only) | Slower export, higher CPU |
| Limited filtergraph complexity | Very complex graphs may timeout |
| No GPU filters (OpenCL/CUDA) | CPU-only processing |
| Memory constraints | Large frames + complex filters = OOM risk |

## Engine Selection Logic

```
VideoEditorEngine.exportTimeline()
    │
    ├─► For each clip:
    │     ├─ Can Media3 handle it?
    │     │     ├─ Audio handling matches (includeAudio == clipHasAudio && !mute)
    │     │     ├─ No complex filters needed
    │     │     └─ Yes → Media3 Transformer
    │     │
    │     └─ No → FFmpeg fallback
    │           ├─ Copy URI to temp file
    │           ├─ Build filter graph (trim, speed, rotation, scale, pad)
    │           └─ Run with progress tracking
    │
    ├─ After all segments rendered:
    │     ├─ Single segment → Copy to output
    │     └─ Multi-segment:
    │           ├─ Try Media3 concat (if all segments compatible)
    │           └─ Fallback: FFmpeg concat demuxer
    │
    └─ Probe output, emit result
```

## VID Tool Engine Mapping

| Tool | Primary Engine | Fallback | Reason |
|------|---------------|----------|--------|
| VID-01 Trim | M3 | FF | Media3 trimOptimization |
| VID-02 Merge | M3 | FF | Media3 concatenation |
| VID-03 Crop | M3 | FF | Aspect ratio presentation |
| VID-04 Resize | M3 | FF | Scale presentation |
| VID-05 Speed | M3 | FF | SpeedProvider |
| VID-06 GIF | FF | - | 2-pass palettegen (Media3 can't) |
| VID-08 Extract Audio | FF | - | Audio-only output |
| VID-09 Rotate | M3 | FF | ScaleAndRotateTransformation |
| VID-10 Flip | M3 | FF | Negative scale |
| VID-11 Stabilize | FF | - | deshake filter |
| VID-12 Loop | FF | - | stream_loop input |
| VID-13 Volume | FF | - | volume filter |
| VID-14 Logo Blur | FF | - | ROI blur overlay |
| VID-15 Text | M3 | FF | Media3 BitmapOverlay (static) / FFmpeg (timed) |
| VID-16 Add Audio | FF | - | amix/adelay filters |
| VID-17 Image | M3 | FF | Media3 BitmapOverlay (static) / FFmpeg (timed) |
| VID-18 Convert | FF | - | Container/format change |
| VID-19 Reverse | FF | - | reverse filter (segmented) |
| VID-20 Compress | M3 | FF | Bitrate control |
| VID-21 Split | M3 | FF | Segment copy |
| VID-22 Frames | NAT | - | MediaMetadataRetriever |
| VID-23 Subtitles | FF | - | ASS burn-in |
| VID-25 Face Blur | ML/FF | - | ML Kit detection + FFmpeg |
| VID-26 Color | M3 | FF | Media3 color effects + LUT |
| VID-27 Transitions | FF | - | xfade filter |
| VID-28 Chroma Key | FF | - | chromakey filter |
| VID-29 Stop Motion | M3 | FF | Image sequence |
| VID-30 Time Lapse | FF | - | fps/filter graph |

## Pro Video Editor Export Pipeline

### Supported Inline (Media3/FFmpeg)

```kotlin
// VideoEditorMath.buildSegmentFfmpegArgs() builds:
- Trim: -ss, -t
- Speed: setpts, atempo
- Rotation: transpose/rotate filter
- Scale: scale=W:H:force_original_aspect_ratio=decrease
- Pad: pad=W:H:(ow-iw)/2:(oh-ih)/2
- FPS: fps=30
- Format: format=yuv420p, setsar=1
- Audio: atempo (if speed != 1), aformat=44100:stereo
- Codec: mpeg4/q:v=3 (video), aac/160k (audio)
```

### Not Supported Inline (Require Separate Tools)

| Feature | Why Not Inline | VID Tool |
|---------|----------------|----------|
| Transitions | Requires multi-clip filter graph with overlap | VID-27 |
| Chroma Key | Requires background media input + chromakey filter | VID-28 |
| Color Grading | Complex filter graph (LUT, colorbalance) | VID-26 |
| Stabilization | Two-pass (detect + transform) | VID-11 |
| Face Blur | ML Kit detection + tracking + overlay | VID-25 |
| Logo Blur | ROI tracking + blur overlay | VID-14 |
| Text Overlay | Timed overlay with font rendering | VID-15 |
| Image Overlay | Timed overlay with scaling/opacity | VID-17 |
| Subtitles | ASS generation + burn-in | VID-23 |
| Audio Mix | adelay + amix with volume control | VID-16 |
| Volume | volume filter | VID-13 |
| Loop | stream_loop input option | VID-12 |
| Reverse | reverse filter (memory-safe segmented) | VID-19 |
| Stop Motion | Image sequence input | VID-29 |
| Time Lapse | fps decimator + speed filter | VID-30 |
| GIF | 2-pass palettegen/paletteuse | VID-06 |
| Extract Audio | Audio-only output | VID-08 |
| Compress | CRF + bitrate targeting | VID-20 |
| Split | Segment copy + ZIP | VID-21 |
| Extract Frames | MediaMetadataRetriever | VID-22 |

## Performance Considerations

### Media3 Transformer
- Hardware accelerated (MediaCodec)
- Lower CPU, faster export
- Limited filter support
- Best for: trim, speed, rotation, simple concat

### FFmpeg
- Software encoding (libmpeg4)
- Higher CPU, slower export
- Full filter graph support
- Best for: complex filters, transitions, overlays, color

### Hybrid Approach
The engine automatically selects the best path per clip, falling back to FFmpeg only when necessary. This optimizes for speed while maintaining capability.

## Testing Engine Capabilities

Run capability tests:
```bash
./gradlew :app:testDebugUnitTest --tests "com.harimu.app.MediaEngineTest"
./gradlew :app:testDebugUnitTest --tests "com.harimu.app.VideoForgeBatch2AndEditorTest"
```

## Future Engine Improvements

1. **Media3 Effect API**: Migrate more filters to Media3 Effects (color, overlay)
2. **Hardware FFmpeg**: Enable libx264 with MediaCodec (requires NDK build)
3. **Unified Filter Graph**: Build single filter graph for entire timeline
4. **Preview Pipeline**: Real-time preview using Media3 ExoPlayer with effects
5. **GPU Filters**: OpenGL/Vulkan compute shaders for real-time effects