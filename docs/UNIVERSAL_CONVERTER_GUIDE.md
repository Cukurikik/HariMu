# Universal Converter Guide (HariMu — P10)

## 1. Architecture Overview

HariMu's **Universal Converter** replaces flat 28-item converter browsing with a dedicated **format-aware Workbench + Workflow Explorer + Background Queue** experience (`UniversalConverterScreen` backed by `UniversalConverterViewModel`), while preserving every individual `CNV-01`..`CNV-28` tool in `ToolRegistry` and direct `ToolScreen` routes.

### Core Modules & Files
- **`core/converter/ConverterWorkflow.kt`**: Partitions all 28 converter IDs (`CNV-01..CNV-28`) across 6 user-oriented workflows:
  1. `VIDEO_AUDIO` (`CNV-01..CNV-08`) — Video, Audio & Speech
  2. `IMAGES_VECTOR` (`CNV-09..CNV-12`) — Images, SVG, Icons & Base64
  3. `DOCUMENTS_BOOKS` (`CNV-13..CNV-15`, `CNV-19`, `CNV-20`) — Documents, OCR & E-Books
  4. `ARCHIVES_SECURITY` (`CNV-16..CNV-18`) — Archives & AES-256 Encryption
  5. `FONTS_3D` (`CNV-21`, `CNV-22`, `CNV-28`) — Fonts & 3D Meshes
  6. `DATA_CODE` (`CNV-23..CNV-27`) — Structured Data, GeoJSON & Code
- **`core/converter/ConverterFormatMatrix.kt`**:
  - `SourceFormatDetector`: Identifies formats from magic headers (`%PDF-`, PNG, JPEG, RIFF `WEBP`/`WAVE`/`AVI `, ISO-BMFF `ftyp`, Matroska/WebM, FLAC, OggS, MP3/ID3, 7z, `Rar!`, GZIP, `HARIMUA1`, SFNT/OTTO/`wOFF`/`wOF2`, `<svg`, ASCII STL, ZIP manifest sniffing for `.docx`/`.epub`) before falling back to extension and structured text classification.
  - `ConverterFormatMatrix`: Computes `validFormatTargetsFor`, `securityTargetsFor`, multi-file intersections (`validTargetsForSources`), honest `unsupportedReason` notices, and pre-run input validation (`validateInputs`).
- **`core/design/converter/`**:
  - `ConverterComponents.kt`: `ConverterHeroHeader`, `ConverterWorkflowStrip`, `ConverterSourceCard`, `ConverterTargetMatrix`, `ConverterBatchToggleRow`, `ConverterCapabilityNoticeBanner`, `ConverterWorkflowSearchBar`.
  - `ConverterQueueComponents.kt`: `ConverterJobQueueSection` displaying Room-backed `JobEntity` cards (`QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`), live progress bars, Cancel, Retry, Open, Share, Save (SAF), and Cloud Upload actions.
- **`feature/studios/UniversalConverterScreen.kt` & `UniversalConverterViewModel.kt`**:
  - Adaptive `BoxWithConstraints` layout: two-pane (`>= 840.dp`) Workbench/Workflows + Live Queue side-by-side on tablets/desktops, stacked responsive scroll on phones (`< 840.dp`), using `WindowInsets.safeDrawing` and `>= 48.dp` touch targets.
  - Entry points: Home `ToolGroup.CONVERTER` card, `CatalogScreen` (`catalog_open_converter_btn`), and every `CNV-*` `ToolScreen` (`tool_open_converter_btn`).

---

## 2. Supported Format Combinations & Batch Rules

- **Strict Valid-Target Filtering**: Only target combinations backed by working HariMu engines appear in `ConverterTargetMatrix`. Unsupported or unimplemented codecs (`TIFF`, `PSD`, `CR2/NEF/ARW/ORF/RW2`, `WOFF2`, and `HEIC`/`AVIF` below API 28/31) expose zero format conversion targets with an explicit explanation banner instead of failing mid-run.
- **Multi-File Batch Intersections**: Selecting multiple files intersects their supported `ConversionTargetOption` IDs:
  - Homogeneous sets (e.g., multiple PNG/JPG images) preserve common conversions (`WebP`, `JPG`, `PNG`, `PDF`, `Icon Pack`, `Base64`) and allow enqueuing separate `WorkManager` jobs via `JobManager` (`maxParallel = 2`).
  - Mixed sets (e.g., PNG + PDF) hide mismatched format conversions and expose only `CNV-18` (multi-file **AES-256 Encrypted ZIP**).
- **Direct Tool Handover**: Choosing **Open Tool (`CNV-XX`)** switches to the dedicated `ToolScreen` for single-tool inspection and parameter tuning.

---

## 3. Honest Capability & Engine Disclosures

| Area / ID | Engine Behavior | Explicit Limitations Disclosed in UI |
|---|---|---|
| `CNV-03` Video → Audio | FFmpeg/Media3 extract to `MP3`, `AAC`, `WAV`, `FLAC` | `WAV`/`FLAC` wrap decoded PCM streams into lossless containers without restoring lost source frequencies. |
| `CNV-04` Video 2x Scale | Media3 H.264 + optional FFmpeg `lanczos`/`unsharp` | Device scaler capped at `≤ 60s` and `≤ 1080p` input; **not** a neural super-resolution model. |
| `CNV-06` Hi-Res Audio | FFmpeg 24-bit WAV/FLAC up to 96 kHz | Upsampling lossy inputs (`MP3`/`AAC`/`OGG`) does not recover lost acoustic detail. |
| `CNV-07` Speech → Text | Cloud Whisper-compatible AI Gateway (`AUD-27`) | Requires network, explicit Upload Consent dialog, and `≤ 15 MB` audio limit. |
| `CNV-09` Images | Android `Bitmap` / `PdfDocument` (`IMG-23`) | Encodable targets restricted strictly to `WebP`, `JPG`, `PNG`, `PDF`; `HEIC`/`AVIF` decode only on API 28+/31+. |
| `CNV-10` SVG Raster | AndroidSVG 1.4 (`≤ 4096 px`) | SVG `<filter>` effects, external/system fonts, and SMIL/CSS animations are skipped. |
| `CNV-13` Office ↔ PDF | `OfficeConvertEngine` (`PDF-06` / `PDF-07`) | **Basic conversion**: paragraph/page text & image slides only; complex Office layouts, styled tables, and headers/footers are omitted. |
| `CNV-15` OCR → Text | Tesseract4Android (`PDF-15`) | 8 languages supported; downloads `tessdata` (`~4–15 MB`) automatically on first use. |
| `CNV-16` / `CNV-17` Archives | `zip4j`, `commons-compress`, `junrar` | `RAR` is **read-only** (`junrar`); outputs always repack to sanitized `.zip` with zip-slip path checks and 128 MB/entry, 512 MB total, 200x ratio zip-bomb caps. |
| `CNV-18` AES-256 ZIP | `ArchiveEngine.createAesZip` (`zip4j`) | Requires `≥ 8` character password when encrypting; decrypting legacy `.hzm` (`HARIMUA1`) remains supported without storing passwords in outputs or logs. |
| `CNV-19` / `CNV-20` Books | Spine XML + `junrar` / `ZipFile` | `EPUB` reads spine text only (CSS/images/DRM rejected); `CBR`/`CBZ` sorts pages naturally into PDF or minimal image-per-page EPUB. |
| `CNV-21` / `CNV-22` Fonts | `FontEngine` SFNT / WOFF 1.0 | Supports `TTF/OTF ⇄ WOFF 1.0` and TrueType glyph subsetting (`CNV-22` drops hinting/`GSUB`/`GPOS`/`kern`). `WOFF2` (Brotli) and `OTF(CFF) ⇄ TTF` outline conversions or CFF subsetting are rejected. |
