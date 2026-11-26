# Image Forge Tools Reference (IMG-01 through IMG-47)

This document catalogs all 47 Image Forge tools grouped across the 7 `ImageForgeScreen` workspace categories, including underlying engines, specialized live preview widgets, and input specifications.

## 1. Studio & Compositor (1 tool)

| Tool ID | Title | Engine | Workspace / Preview UI | Key Capabilities |
|---------|-------|--------|------------------------|------------------|
| IMG-47 | Image Matrix Studio | MAT | Dedicated `ImageMatrixScreen` | Multilayer `IMAGE` / `TEXT` / `STICKER` canvas, checkerboard transparency preview, viewport zoom, drag-to-move, undo/redo, ML Kit & Basic cutout, PNG/JPG/WEBP export |

## 2. Geometry, Crop & Framing (10 tools)

| Tool ID | Title | Engine | Preview / Widget | Key Parameters |
|---------|-------|--------|------------------|----------------|
| IMG-01 | Image Cropper Pro | NAT/TILE | `ImageCropOverlayPreviewCard` (draggable ratio + rule-of-thirds overlay via `ImageGeometryMath.cropRectForRatio`) | `ratio`, `zoom`, `offsetX`, `offsetY`, `format`, `keepExif` |
| IMG-02 | Smart Image Resizer | NAT | Single image input + dimension output | `scale` (%), `maxEdge` (px) |
| IMG-03 | Image Rotator | NAT | EXIF-normalized full-res rotation | `deg` (90/180/270), `format`, `keepExif` |
| IMG-04 | Image Flipper | NAT | Single image mirror transform | `dir` (Horizontal / Vertikal / Keduanya) |
| IMG-05 | Canvas Expander | NAT | `ImageCanvasExpanderPreviewCard` (`ImageGeometryMath.expandCanvasSize`) | `aspect`, `mode` (Solid / Blurred copy), `bgHex` |
| IMG-06 | Perspective Corrector | NAT | `PerspectivePointsCard` (4 draggable homography handles) | `tlX..blY`, `format` |
| IMG-07 | Image Straightener | NAT | `ImageStraightenPreviewCard` (`ImageGeometryMath.straightenSafeRect`) | `angle` (-25°..+25°), `fill`, `bgHex` |
| IMG-08 | Smart Auto-Crop | NAT | Local saliency bounding box (`ImageGeometryMath.saliencyBoundingBox`) + region decode | `threshold` (%), `padding` (%) |
| IMG-09 | Image Alignment | NAT | Multi-image translation offset stack (`ImageGeometryMath.alignmentOffset`) | `maxShift` (%), `average` |
| IMG-10 | Multilayer Crop | NAT | Named region ZIP + metadata (`ImageGeometryMath.parseCropRegions`) | `regions`, `format` |

## 3. Color, Filters & Typography (6 tools)

| Tool ID | Title | Engine | Preview / Widget | Key Parameters |
|---------|-------|--------|------------------|----------------|
| IMG-28 | Color Palette Extractor | NAT | K-Means ARGB cluster extractor (`ImageGeometryMath.kMeansPalette`) | `count`, swatch card PNG + hex report |
| IMG-33 | Image Filters (GPU Preview) | GPU+MAT | `FilterPreviewCard` (`ImageFilterPipeline.matrixValues` 20-float GPU ColorMatrix) | `preset`, `brightness`, `contrast`, `saturation`, `temperature`, `blur`, `vignette`, `format` |
| IMG-35 | Typography Poster Card | NAT | `ImagePosterPreviewCard` live 1080×1350 quote card preview | `textInput`, `style` (Sunset Amber / Teal Obsidian / Editorial Paper) |
| IMG-36 | Gradient Overlay | NAT | `ImageGradientOverlayPreviewCard` linear gradient preview | `startHex`, `endHex`, `opacity` (%) |
| IMG-39 | LUT Color Filter | NAT/LUT | `ImageLutPreviewCard` (`ImageLutEngine.preset` 3D tone strip comparison) | `mode` (Preset / `.cube` text / PNG Strip), `preset`, `cubeText`, `intensity` (%) |
| IMG-40 | Vintage / Film Simulation | NAT/GL | Film look simulation pipeline | `preset` (Kodak Portra / Fuji Classic / Cinema Fade / Mono Grain), `warmth`, `fade`, `grain`, `vignette` |

## 4. Effects, Overlays & Borders (7 tools)

| Tool ID | Title | Engine | Preview / Widget | Key Parameters |
|---------|-------|--------|------------------|----------------|
| IMG-31 | Image Watermark | NAT | `ImageWatermarkOverlayPreviewCard` (positioned or tiled overlay preview) | `text`, `opacity`, `pos`, `tiled` |
| IMG-32 | Image Overlay Blender | NAT/PorterDuff | Multi-image composite across 9 `ImageBlendModes` | `blend`, `opacity`, `scale`, `offsetX`, `offsetY`, `format` |
| IMG-34 | Border & Frame Generator | NAT | `ImageBorderFramePreviewCard` (gallery or Polaroid border) | `pad` (%), `color`, `polaroid` |
| IMG-37 | Shadow & Glow | NAT/Blur | Drop shadow / outer glow on alpha silhouette or box | `shape`, `shadowColor`, `shadowBlur`, `shadowX`, `shadowY`, `glowColor`, `glow`, `padding` |
| IMG-38 | Sticker / Emoji Overlay | NAT | `ImageStickerOverlayPreviewCard` (drag-to-place emoji canvas preview) | `emoji`, `xPct`, `yPct`, `sizePct`, `rotation`, `opacity` |
| IMG-41 | Lens Flare Generator | NAT | `ImageLensFlarePreviewCard` (drag-to-place light source & streak) | `xPct`, `yPct`, `intensity`, `streak`, `ghosts`, `warm` |
| IMG-42 | Reflection & Mirror | NAT | Bottom water reflection or symmetrical horizontal mirror | `mode` |

## 5. Local & Cloud AI (12 tools)

| Tool ID | Title | Engine | Classification | Key Capabilities |
|---------|-------|--------|----------------|------------------|
| IMG-11 | AI Image Upscaler | NAT/DSP | On-device heuristic | 2×–4× bicubic + unsharp sharpening (not neural super-res) |
| IMG-12 | AI Background Remover | ML/NAT | On-device ML Kit + Basic | ML Kit Subject Segmentation (`FULL`) with flood-fill fallback (`BASIC`), transparent or solid fill |
| IMG-13 | Object Remover | AI | Cloud Gateway (`CL`) | Prompt + mask description via Cloudflare Worker |
| IMG-14 | Photo Enhancer | AI | Cloud Gateway (`CL`) | Lighting, sharpness, and color enhancement via Cloud AI |
| IMG-15 | Face Restorer | AI | Cloud Gateway (`CL`) | Portrait sharpening and restoration via Cloud AI |
| IMG-16 | Style Transfer | AI | Cloud Gateway (`CL`) | Artistic style re-rendering via Cloud AI |
| IMG-17 | Photo Colorizer | AI | Cloud Gateway (`CL`) | Monochrome photo colorization via Cloud AI |
| IMG-18 | AI Sky Replacement | AI | Cloud Gateway (`CL`) | Sky swapping & lighting match via Cloud AI |
| IMG-19 | AI Portrait Bokeh | NAT/DSP | On-device heuristic + `ImageRadialFocusPreviewCard` | Draggable `(focusX, focusY)` radial blur with radius & strength controls |
| IMG-20 | Photo Cartoonizer | AI | Cloud Gateway (`CL`) | Stylized cartoon/anime rendering via Cloud AI |
| IMG-21 | AI Image Generator | AI | Cloud Gateway (`CL`) | Prompt-to-image generation (`InputSpec.TextInput`) |
| IMG-22 | AI Image Denoiser | NAT/DSP | On-device heuristic | Edge-preserving smart blur (`strength`, `detail`) |

## 6. Convert, Optimize & Metadata (7 tools)

| Tool ID | Title | Engine | Preview / Widget | Key Parameters |
|---------|-------|--------|------------------|----------------|
| IMG-23 | Format Converter | NAT | `ImageFormatSupportCard` (`ImageFormatMatrix` live decode/encode status) | `format` (`JPG`, `PNG`, `WebP`, `PDF` encodable; honest refusal for unbundled encoders), `quality`, `keepExif` |
| IMG-24 | App Icon Pack Generator | NAT | Generates `IconSpec` multi-density Android + iOS + Favicon + adaptive XML ZIP | `appName`, `bgHex` |
| IMG-25 | Base64 Image Encoder / Decoder | NAT | Bi-directional image ↔ data-URI / Base64 text or decoded image | Mode, mime/prefix handling |
| IMG-26 | Smart Image Compressor | NAT | Target KB binary-search quality compression + byte stats | `maxKb`, `format`, `maxEdge` |
| IMG-27 | Web Image Optimizer | NAT | Multi-width responsive `srcset` WebP/JPG bundle + HTML snippet in ZIP | Responsive breakpoints & quality |
| IMG-29 | EXIF Metadata & Stripper | NAT | Full EXIF/GPS inspection, selective write, or JPEG APP1/attribute strip | `mode` (Read / Edit / Strip GPS / Strip All), `artist`, `copyright`, `description` |
| IMG-30 | File Size Analyzer | NAT | Bounds/EXIF/ICC/estimated size report across formats (`ImageIO.readSourceInfo`) | No encode guesswork, non-destructive inspection |

## 7. Batch, Sprite, GIF & Video Frames (4 tools)

| Tool ID | Title | Engine | Output | Key Capabilities |
|---------|-------|--------|--------|------------------|
| IMG-43 | Batch Image Processing | BATCH | ZIP + CSV per-file audit log | Format conversion, resize, watermark, compress, EXIF strip, thumbnail, rename, ML Kit / Basic background removal |
| IMG-44 | Sprite Sheet Maker | NAT | PNG atlas + JSON metadata | Grid layout (`cols`, `cellSize`, `padding`) with exact frame coordinates |
| IMG-45 | Animated GIF Maker | NAT/GIF | GIF89a (`GifEncoder`) | Multi-image sequence with configurable delay (`ms`), loop count, max edge |
| IMG-46 | Video Frame Extractor | FF | Single or interval PNG/JPG frame(s) (`VID-22` alias) | Exact timestamp or interval extraction via `VideoEngine` |
