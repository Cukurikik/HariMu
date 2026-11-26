# Audio Engine Capabilities Matrix

This document summarizes the exact capabilities and boundaries of HariMu's audio subsystems. The UI exposes only operations genuinely backed by these engines.

## 1. Multi-Track Engine (`AudioStudioEngine` & `AudioStudioPlaybackController`)

| Capability | Live Preview (`ExoPlayer`) | Offline Export (`FFmpeg`) | Notes |
|------------|----------------------------|---------------------------|-------|
| Source Trim (`trimStartMs..trimEndMs`) | Supported (`ClippingConfiguration`) | Supported (`atrim` + `asetpts`) | Minimum 250 ms enforced in ViewModel & Inspector |
| Timeline Offset (`offsetMs`) | Supported (playhead window sync) | Supported (`adelay`) | 0 ms .. timeline end |
| Clip Gain (`-24..+24 dB`) | Supported (linear dB -> player volume) | Supported (`volume=<dB>dB`) | Clamped `[-24, +24]` dB |
| Track Volume (`0.0x..2.0x`) | Supported (combined with master) | Supported (`volume`) | Combined gain in preview normalized `[0..1]` |
| Mute (`M`) / Solo (`S`) | Supported | Supported | Solo tracks silence all non-solo tracks |
| Stereo Pan (`-1.0..+1.0`) | Not live on ExoPlayer | Supported (`pan=stereo\|...`) | Explicitly noted in studio UI & export dialog |
| Master Volume (`0.0x..2.0x`) | Supported | Supported (`volume` post-`amix`) | Applied cleanly before final encode |
| Export Formats | — | WAV (PCM 16-bit), MP3, FLAC | Codec/rate checked via `AudioToolMath` |

## 2. Live Equalizer Engine (`EqualizerFxController` + `AudioBatch2Math`)

| Capability | Engine | Behavior |
|------------|--------|----------|
| API 28+ 10-Band Parametric/PreEq + Limiter | `android.media.audiofx.DynamicsProcessing` | Exact 10 bands (`31 Hz` .. `16 kHz`), `±12 dB`, `±6 dB` preamp, live bypass |
| Legacy < API 28 Fallback | `android.media.audiofx.Equalizer` | Maps 10 UI bands onto device 5-band center frequencies; parity card reports match accuracy |
| Waveform Scrubbing | `AudioWaveformExtractor` + `MediaPlayer.seekTo` | 96-bar peak waveform synced to live playback position |
| Export Render | FFmpeg (`equalizer` / `superequalizer` + limiter) | Exports 10-band curve identically in MP3, WAV, FLAC, or M4A |

## 3. DSP, Waveform & Metadata Utilities

| Component | Role | Capabilities |
|-----------|------|--------------|
| `AudioWaveformExtractor` | Waveform Peak Extraction | Streaming `MediaCodec` + WAV header/PCM fallback + SHA-256 disk/LRU cache |
| `AudioBatch2Math` | DSP Curve & Filter Builders | Mastering compressor/limiter curves, 10-band EQ parity math, FFmpeg filter strings, Visualizer MP4 config (`resolveVisualizerConfig`) |
| `AudioToolMath` | Input/Format Validation | `MediaProbe` validation, codec argument generation across MP3/WAV/OGG/FLAC/M4A/OPUS |
| `Id3TagHelper` | Metadata Read/Write | Reads existing ID3 tags (`title`, `artist`, `album`, `year`, `genre`) to auto-fill `AUD-18` |

## 4. Explicit Non-Goals (Not Invented)

- No real-time FFmpeg DSP preview graphs inside multi-track playback (heavy effects like 8D orbit, convolution reverb, FFT noise reduction, loudness normalization, and compression are accessed via Inspector "Open in Tool" handoffs and rendered cleanly by `MediaJob` / `WorkManager`).
- No MIDI sequencing, virtual instruments (VST/AU), or live multi-track microphone punch-in recording inside `AudioStudioScreen` (recording is handled by dedicated `VoiceRecorderScreen`).
