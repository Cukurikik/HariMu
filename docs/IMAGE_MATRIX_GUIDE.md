# Image Matrix Studio Guide (IMG-47)

`ImageMatrixScreen` (`IMG-47`) is HariMu's multilayer raster/text/sticker compositor backed by `ImageMatrixViewModel` and `ImageLayerCompositor`. Every live preview render passes through the exact same `ImageLayerCompositor.compose(...)` pipeline used at export time.

## 1. Architecture & State Model

- **State holder**: `ImageMatrixViewModel` (`StateFlow<ImageMatrixUiState>`).
- **Undo/Redo stack**: `LayerHistory` (bounded to 50 snapshots of `List<MatrixLayer>`). Continuous drag or slider adjustments call `previewLayer(...)` without flooding undo history, and push a single undo state on gesture release via `commitLayers()`.
- **Bitmap lifecycle**: Decoded preview bitmaps (capped at `MAX_CACHED_BITMAPS = 24`, max 2048px edge) live in `ConcurrentHashMap<String, Bitmap>`. Unreferenced bitmaps are recycled only once no snapshot in the current, undo, or redo stacks references them.
- **Coordinate system**: Layer positions (`xPct`, `yPct`), scales (`scalePct`), text font sizes (`fontSizePct`), rotations (`rotationDeg`), opacities (`opacityPct`), and Porter-Duff blend modes (`ImageBlendModes.labels`) are stored normalized so previews at max 720px render proportionally identically to full-resolution exports (up to `2048×2048`).

## 2. Adaptive Workspace Layout

- **Compact / Handheld (`maxWidth < 720.dp`)**:
  - Top: Zoomable checkerboard preview (`100%`–`300%`) + Quick Add Layer bar (`Image`, `+ Text`, `+ Sticker`).
  - Bottom: Segmented tabs (`Transform` | `Layers (N)` | `Canvas`) for focused parameter editing.
- **Tablet / Landscape (`maxWidth >= 720.dp`)**:
  - Side-by-side two-pane layout (`matrix_wide_workspace`): canvas stage + Quick Add on the left, segmented inspector pane on the right.

## 3. Supported Operations

1. **Canvas Stage (`ImageMatrixCheckerboardBox`)**:
   - Transparency checkerboard backdrop behind rendered preview.
   - Viewport zoom (`-`, `100%` reset, `+`, up to `300%`).
   - Direct canvas drag gesture on the selected layer using `MatrixLayerMath.moved(...)` + active layer crosshair overlay.
2. **Layers Tab (`ImageMatrixLayerRow`)**:
   - Reorder stack (`Move Up` / `Move Down`), toggle visibility (`Visibility`), duplicate, and delete.
   - Top-most visual layer renders at the top of the list (reverse stack order).
3. **Transform Tab (`ImageMatrixTransformInspector`)**:
   - Image layers: one-tap **Remove Bg (ML Kit)** (`SegmentationMode.FULL`) or **Basic** (`SegmentationMode.BASIC`). Because cut-out bitmaps are stored under `#nobg-<timestamp>` keys, **Undo** restores the original image intact.
   - Text & Sticker layers: live text editing, quick hex color swatch strip + hex field, `Font Size (%)`.
   - All layers: `Center` quick button, `Reset Transform` quick button, `Position X/Y (%)`, `Scale (%)`, `Rotation (°)`, `Opacity (%)`, and 9 Porter-Duff blend modes (`Normal`, `Multiply`, `Screen`, `Overlay`, `Darken`, `Lighten`, `Add`, `Difference`, `Exclusion`).
4. **Canvas Tab (`ImageMatrixCanvasInspector`)**:
   - 5 canvas resolution presets (`1080×1080`, `1080×1350`, `1920×1080`, `1080×1920`, `2048×2048`).
   - Background fill hex input + quick color swatches + `Clear All` canvas reset (undoable).
5. **Export Dialog (`ImageMatrixExportDialog`)**:
   - Format selection (`PNG`, `JPG`, `WEBP`) backed by `ImageFormatMatrix` and `ImageIO.encode`.
   - Configurable JPEG/WEBP quality (`50%`–`100%`).
   - Records history in Room (`IMG-47`) and provides **Open**, **Share**, **Save** (SAF or configured default folder), and **Upload to Cloud** actions.
