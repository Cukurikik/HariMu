# Audio Forge Tools Reference (AUD-01 through AUD-28)

This document lists all 28 Audio Forge tools grouped into the 6 `AudioForgeScreen` workspace categories, together with their underlying engines and live UI preview support.

## 1. Studios & Mastering (4 tools)

| Tool ID | Title | Engine | Dedicated / Preview UI | Key Parameters |
|---------|-------|--------|------------------------|----------------|
| AUD-21 | Audio Studio Pro | FF | Dedicated `AudioStudioScreen` | Multi-track timeline, trim, offset, gain, pan, M/S, WAV/MP3/FLAC export |
| AUD-01 | Mastering Hub | FF | `WaveformView` + `AudioMasteringCurveCard` | `preset`, `thresholdDb`, `ratio`, `ceilingDb`, `makeupDb`, `warmthEnhance`, `format` |
| AUD-12 | Loudness Normalizer (-16 LUFS) | FF | `WaveformView` (Before/After) | Target LUFS / 2-pass `loudnorm`, true peak, output format |
| AUD-22 | Audio Enhancer | FF | `WaveformView` (Before/After) | Clarity boost, bass warmth, de-harshing, output format |

## 2. Equalizer & Tone (4 tools)

| Tool ID | Title | Engine | Dedicated / Preview UI | Key Parameters |
|---------|-------|--------|------------------------|----------------|
| AUD-02 | Pro Equalizer 10-Band | FX/FF | Dedicated `EqualizerScreen` | Live `DynamicsProcessing`/5-band fallback + `WaveformView` scrubbing + FFmpeg render |
| AUD-06 | Change Audio Pitch | FF | `WaveformView` (Before/After) | `semitones` (-12..+12), formant preservation option, `format` |
| AUD-07 | Change Audio Speed | FF | `WaveformView` (Before/After) | `speed` (0.5x..2.0x), pitch lock option, `format` |
| AUD-08 | Change Audio Volume | FF | `WaveformView` (Before/After) | Volume percentage / dB boost, `format` |

## 3. Restoration & AI (5 tools)

| Tool ID | Title | Engine | Dedicated / Preview UI | Key Parameters |
|---------|-------|--------|------------------------|----------------|
| AUD-03 | Noise Reduction | FF | `WaveformView` + `NoiseFloor` band overlay | `strength` (%), `noiseFloorDb`, `format` |
| AUD-04 | Vocal Remover (Karaoke) | FF | `WaveformView` (Before/After) | Mode (center-cancel / vocal isolate), low/high pass guard |
| AUD-16 | Silence Remover | FF | `WaveformView` + `SilenceThreshold` guides | `thresholdDb`, `minDurationSec`, `format` |
| AUD-20 | Stem Splitter | DSP/ML | `WaveformView` (Before/After + multi-output) | Vocal / Instrumental / Bass / Drums separation (mid/side DSP + optional model) |
| AUD-27 | Speech to Text (Whisper) | ML/CL | Cloud AI Gateway / Audio input | Language hint, timestamp output format (Text/SRT) |

## 4. Effects & Spatial (4 tools)

| Tool ID | Title | Engine | Dedicated / Preview UI | Key Parameters |
|---------|-------|--------|------------------------|----------------|
| AUD-05 | 8D Audio Engine | FF | `WaveformView` + `AudioSpatial8DCard` | `rotationHz`, `depthPct`, `roomReverb`, `format` |
| AUD-24 | Add Echo | FF | `WaveformView` + `AudioEchoDiagramCard` | `delayMs`, `decay`, `taps` (Single/Dual/Stadium), `format` |
| AUD-25 | Add Reverb | FF | `WaveformView` + `AudioReverbDiagramCard` | `roomPreset`, `roomSizePct`, `dampingPct`, `wetPct`, `format` |
| AUD-28 | Audio Visualizer (MP4) | FF | `AudioVisualizerPreviewCard` + `VideoTrimPreviewCard` output | `style`, `colorTheme`, `resolution`, `fps` |

## 5. Cut, Split & Merge (5 tools)

| Tool ID | Title | Engine | Dedicated / Preview UI | Key Parameters |
|---------|-------|--------|------------------------|----------------|
| AUD-09 | Trim Audio | FF | `WaveformView` + interactive `RangeSlider` + Playhead Set Start/End | `startSec`, `endSec`, `format` |
| AUD-10 | Merge Audio | FF | Multi-audio picker + result player | Sequential concat or parallel mix mode, crossfade, `format` |
| AUD-17 | Reverse Audio | FF | `WaveformView` (Before/After) | Segmented RAM-safe reverse, `format` |
| AUD-23 | Audio Splitter | FF | `WaveformView` + `SplitMarkers` dashed dividers | `mode` (Duration/Parts), `segmentSec`, `partsCount`, `.zip` bundle output |
| AUD-26 | Fade In / Fade Out | FF | `WaveformView` + `FadeEnvelope` curve overlay | `fadeIn` (s), `fadeOut` (s), curve type, `format` |

## 6. Convert, Tags & Protect (6 tools)

| Tool ID | Title | Engine | Dedicated / Preview UI | Key Parameters |
|---------|-------|--------|------------------------|----------------|
| AUD-11 | Audio Compressor | FF | `WaveformView` + `AudioCompressionPreviewCard` | `bitrate` (32k..320k), `format` (MP3/OGG/M4A) |
| AUD-13 | Audio Converter | FF | `WaveformView` (Before/After) | `format` (MP3/WAV/OGG/FLAC/M4A/OPUS), bitrate |
| AUD-14 | Audio Resampler | FF | `WaveformView` (Before/After) | Sample rate (8k..96k Hz), `format` |
| AUD-15 | Channel Manipulator | FF | `WaveformView` (Before/After) | Mono / Stereo / Swap L-R / Left-only / Right-only |
| AUD-18 | ID3 Metadata & Cover Art | NAT/FF | `AudioId3PreviewCard` + `Id3TagHelper` auto-fill | `title`, `artist`, `album`, `year`, `genre`, optional cover image |
| AUD-19 | Audio Watermark | FF | `WaveformView` (Before/After) | Periodic tone or secondary watermark audio + interval/level |
