# Image Engine Capabilities Matrix

This document summarizes the actual capabilities and deliberate boundaries of HariMu's image processing engines. All UI previews and Studio actions reflect only implemented engine behaviors.

## 1. Core Image Subsystems

| Engine | Responsibility | Real Implementation Details |
|--------|----------------|-----------------------------|
| `ImageIO` | Memory-safe decode, EXIF/ICC preservation, encode | `inJustDecodeBounds` bounds inspection; `BitmapRegionDecoder` region decoding for `IMG-01`/`IMG-08`/`IMG-10`; guarded full decode capped at `MAX_FULL_DECODE_PIXELS = 48 MP`; EXIF read/write/strip (`androidx.exifinterface.media.ExifInterface`); JPEG APP2 ICC profile extraction & re-injection; PDF single-page encode (`PdfDocument`). |
| `ImageGeometryMath` | Pure Kotlin geometry & K-Means math | Crop ratio calculation, EXIF-to-raw-source coordinate mapping, horizon straighten corner-safe rectangle, 8×8 Gaussian elimination projective homography (`IMG-06`), saliency subject bounding box (`IMG-08`), block SAD alignment offset (`IMG-09`), icon pack specs (`IMG-24`), deterministic K-Means color clustering (`IMG-28`). |
| `ImageFilterPipeline` | Color grading & blur/vignette export | Generates 20-element `ColorMatrix` shared identically between live GPU `FilterPreviewCard` (`IMG-33`) and export canvas `ColorMatrixColorFilter`, followed by downscale-upscale blur and radial vignette shader. |
| `ImageLutEngine` | 3D Color Lookup Tables (`IMG-39`) | Parses Adobe `.cube` (`LUT_3D_SIZE` 2..64), decodes horizontal Hald/PNG LUT strips, provides built-in 17³ presets (`Teal & Orange`, `Fade Film`, `Mono Kontras`, `Warm Kodak`), and applies trilinear interpolation with adjustable intensity blending. |
| `ImageLayerCompositor` | Multilayer composition (`IMG-47`) | Draws sorted `IMAGE`, `TEXT`, and `STICKER` layers onto ARGB_8888 canvas with normalized transform matrices, opacity, and `ImageBlendModes` Porter-Duff modes (`Normal`, `Multiply`, `Screen`, `Overlay`, `Darken`, `Lighten`, `Color Dodge`, `Color Burn`, `Difference`). |
| `ImageOverlayEngine` | Overlays, stickers, shadows & film FX | Drop shadow & outer glow (`BlurMaskFilter`), emoji stamping (`IMG-38`), vintage film warmth/fade/grain/vignette (`IMG-40`), radial/streak lens flare (`IMG-41`), Porter-Duff image overlay (`IMG-32`). |
| `BackgroundSegmenters` | Subject cutout (`IMG-12`, `IMG-47`) | Uses ML Kit Subject Segmentation when available with automatic fallback to border flood-fill (`SegmentationMode.BASIC`) and configurable edge feathering. |
| `ImageFormatMatrix` | Honest format capability registry (`IMG-23`) | Single source of truth refusing unbundled encoders cleanly (`HEIC`, `AVIF`, `SVG`, `TIFF`, `BMP`, `PSD`, `RAW` encode disallowed; `JPG`, `PNG`, `WebP`, `PDF` encode supported). |
| `GifEncoder` | GIF89a animation writer (`IMG-45`) | Encodes multi-frame quantized palette GIF streams locally without native binary dependencies. |

## 2. Explicit Non-Goals (Not Fabricated in UI)

- **No brush painting, lasso/magic-wand pixel masking, or vector path nodes** inside `ImageMatrixScreen` — edits are structured around `IMAGE`, `TEXT`, and `STICKER` layer objects, transforms, blend modes, and whole-layer background removal.
- **No GPU blur or vignette live shader in `IMG-33` preview on API < 31**: `FilterPreviewCard` accurately mirrors the exact 20-value `ColorMatrix` in real time and states transparently that blur and vignette are applied at full resolution during export.
- **No fake local neural models**: `IMG-11` (Upscale), `IMG-19` (Bokeh), `IMG-22` (Denoise), and `IMG-08` (Auto-Crop) use fast classic DSP/saliency heuristics, clearly labeled as non-neural, while `IMG-13..18, 20, 21` require user upload consent to route through the Cloudflare AI Gateway.
- **No unsupported format spoofing**: `ImageFormatMatrix.requireEncodable` rejects encoders Android does not natively ship (`TIFF`, `PSD`, `SVG`, `AVIF`, `HEIC`).
