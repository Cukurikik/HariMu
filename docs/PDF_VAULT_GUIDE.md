# PDF Vault & PDF Document Studio Guide (HariMu)

## 1. Architecture Overview

HariMu's **PDF Vault** pairs a document-oriented hub (`PdfVaultScreen`) with a multi-tab interactive workbench (`PdfStudioScreen` backed by `PdfStudioViewModel`) while keeping generic `ToolScreen` routes functional for standalone conversions and direct catalog deep-links.

### UI & Design Layer
- **Hub (`feature/studios/PdfVaultScreen.kt`)**:
  - Dedicated landing experience routed directly from Home `ToolGroup.PDF` cards and accessible from `CatalogScreen` (`catalog_open_pdf_vault_btn`).
  - Organizes all 21 `PDF-*` tools into 5 workflow categories (`PAGES_ORGANIZE`, `SECURITY_PRIVACY`, `OCR_AI`, `CONVERT_COMPRESS`, `FORMS_FINISHING`) plus the 3 PDF bridge tools (`CNV-13` Office→PDF, `CNV-14` PDF→Images, `CNV-15` Scan→Searchable PDF).
  - Real-time bilingual search (`ID`/`EN`) and capability-mode badges (`Lossless`, `Raster`, `Basic OOXML`, `Tesseract OCR`, `AI Cloud`).
- **Interactive Document Studio (`feature/studios/PdfStudioScreen.kt` + `PdfStudioViewModel.kt`)**:
  - Responsive layout switching automatically at `maxWidth >= 720.dp`: side-by-side persistent preview + tabbed tools on wide screens (`pdf_studio_wide_workspace`) vs compact tabbed workflow on handhelds (`pdf_studio_compact_workspace`).
  - Five specialized workspaces:
    1. **Inspect & OCR (`INSPECT`)**: File metadata chips, extractable text preview, compression DPI/JPEG quality controls (`PDF-09`), 8-language Tesseract OCR (`PDF-15`), and AI structure analysis (`PDF-17`, gated by explicit cloud consent).
    2. **Pages & Merge (`PAGES_MERGE`)**: Accessible page strip organizer (`PdfPageStripOrganizer`) with `48dp` targets, All/Odd/Even/Clear shortcuts, Move Left/Right reordering (`PDF-04`), multi-page extraction (`PDF-05`), batch rotation with live preview (`PDF-03`), ZIP split (`PDF-02`), multi-file drag-free queue merging (`PDF-01`), and Image-to-PDF builder (`PDF-08`).
    3. **Security & Redact (`SECURITY_REDACT`)**: AES-256 / AES-128 / RC4-128 encryption (`PDF-10`), password unlock & decryption (`PDF-11`), text & image watermark placement with live diagonal/opacity preview (`PDF-12`), object-only watermark scanner (`PDF-13`), and two-step **Scan -> Preview Overlay -> Flatten & Verify Redaction** (`PDF-14`).
    4. **Forms & Finishing (`FORMS_META`)**: Live metadata editor (`PDF-19`), AcroForm field inspector/filler (`PDF-21`), and pattern-based page numbering (`PDF-18`).
    5. **Compare A/B (`COMPARE`)**: Side-by-side Document A / B visual diff preview with highlighted change grid (`PDF-20`) plus full text/image ZIP diff export.
- **Shared PDF Components (`core/design/pdf/`)**:
  - `PdfDocumentPreview.kt`: Real Android `PdfRenderer` preview card supporting encrypted temporary unlock (`prepareRenderableTemp`), prev/next page navigation, full-screen zoom modal, live watermark preview overlay, redaction bounding-box overlay, rotation transform preview, and file metadata badges.
  - `PdfPageOrganizerComponents.kt`: `PdfMergeQueueCard` and `PdfPageStripOrganizer`.
  - `PdfVaultLauncherCard.kt`: `PdfVaultHeroCard`, `PdfSearchBar`, `PdfCategoryFilterRow`, `PdfCategorySection`, and `PdfToolLauncherCard` with `PdfCapabilityMode` badges.

---

## 2. Engine Capabilities & Honest Disclosures

All core document operations run on **PDFBox-Android (Apache-2.0)** (`PdfVaultEngine`), **Android `PdfRenderer`**, **Tesseract4Android (`PdfOcrEngine`)**, and **Apache POI / Basic OOXML (`OfficeConvertEngine`)**.

| ID | Tool | Engine / Mode | Output & Capability Notes |
|---|---|---|---|
| `PDF-01` | Merge PDF | PDFBox Lossless | Preserves vector text, fonts, and page structure across merged documents. |
| `PDF-02` | Split PDF | PDFBox Lossless | Splits into multi-page PDF chunks inside a `.zip` archive (unlocks with password if encrypted). |
| `PDF-03` | Rotate PDF | PDFBox Lossless | Updates `/Rotate` page dictionary entries (`90°`, `180°`, `270°`) without re-encoding content. |
| `PDF-04` | Reorder Pages | PDFBox Lossless | Re-imports pages in user-defined sequence without quality loss. |
| `PDF-05` | Extract Pages | PDFBox Lossless | Exports selected page ranges into a new PDF without altering page streams. |
| `PDF-06` | PDF to Word | **Basic OOXML (`OfficeConvertEngine`)** | Extracts raw text & paragraph breaks into `.docx`. Complex tables, columns, floating graphics, and scanned PDFs without OCR are not layout-reconstructed. |
| `PDF-07` | PDF to PPTX | **Basic OOXML (`OfficeConvertEngine`)** | Converts each page's extracted text into editable `.pptx` slides; visual artwork is not exported as editable shapes. |
| `PDF-08` | Images to PDF | PDFBox Image XObject | Packs photos into `Fit`, `A4`, or `Letter` PDF pages. |
| `PDF-09` | Compress PDF | **Rasterized Re-encode** | Re-renders pages via `PdfRenderer` and compresses to JPEG (`72–200 DPI`, quality `30–95%`). Text becomes non-selectable images; clearly disclosed in UI. |
| `PDF-10` | Protect / Encrypt | PDFBox `StandardProtectionPolicy` | Supports `AES-256`, `AES-128`, and legacy `RC4-128` with separate user/owner passwords. |
| `PDF-11` | Unlock / Decrypt | PDFBox Security Removal | Permanently strips protection when authorized user/owner password is supplied. |
| `PDF-12` | Watermark PDF | PDFBox Content Append | Appends vector text (angle, opacity, scale) or PNG image watermarks onto pages; previewed live before export. |
| `PDF-13` | Remove Watermark (Objects) | **Object-Only Removal** | Removes standalone PDF annotation stamps and Optional Content Groups (OCG). Burned-in watermarks flattened into page streams cannot be removed automatically; explicitly disclosed. |
| `PDF-14` | Permanent Redaction | **Flatten + Blackout + Verify** | Locates text coordinates via PDFBox `TextPosition`, previews redaction boxes visually, flattens pages to raster images at selected DPI, draws solid black masks, and runs post-export text extraction verification (`verified = true/false`). |
| `PDF-15` | OCR Extract Text | Tesseract4Android (8 Languages) | Runs on-device OCR across rendered pages; indicates whether `tessdata` is installed locally or requires first-run download. |
| `PDF-16` | Scan to Searchable PDF | Tesseract4Android + PDFBox | Combines page image layer with invisible `RenderingMode.NEITHER` OCR text layer. |
| `PDF-17` | AI Document Analysis | Cloudflare AI Gateway | Sends extracted text snippets (up to page cap) to HariMu AI Gateway only after explicit user upload confirmation. |
| `PDF-18` | Page Numbers | PDFBox Content Append | Adds templated labels (`{n}`, `{total}`) in 6 corner/center anchor positions. |
| `PDF-19` | Metadata Editor | PDFBox `PDDocumentInformation` | Reads existing document metadata automatically on file pick and updates Title, Author, Subject, Keywords. |
| `PDF-20` | Compare Documents | PDFBox Text Diff + Pixel Grid | Computes added/removed text lines, renders interactive page-by-page diff heatmaps, and exports a text report + PNG diff ZIP. |
| `PDF-21` | Fill AcroForm | PDFBox `PDAcroForm` | Auto-populates discovered `fieldTree` entries from the selected PDF for interactive editing and saving. |
| `CNV-13` | Office to PDF | **Basic OOXML/Text→PDF** | Converts `.docx`/`.xlsx`/`.pptx`/`.txt` text streams into paginated PDF pages without full Office layout rendering. |
| `CNV-14` | PDF to Images | **Rasterized Export** | Renders all pages via `PdfRenderer` into PNG/JPG images packed in a `.zip` (supports multi-output inspection). |
| `CNV-15` | OCR Converter Bridge | Tesseract4Android + PDFBox | Produces searchable PDFs directly from scanned documents/images. |

---

## 3. Hardening & Engine Fixes Implemented in P9

- **Multi-Output WorkManager Decoding (`ToolViewModel.kt`)**: `WorkInfo.State.SUCCEEDED` decodes full `KEY_OUTPUTS_JSON` + `KEY_TEXT_OUTPUT` via `ToolOutputCodec` on `Dispatchers.IO` instead of dropping secondary artifacts (fixing multi-output results such as `PDF-20` and `CNV-14`).
- **Dual-Input Preview (`ToolInputComponents.kt`)**: Added dedicated handling for `InputSpec.PdfAndImage` (`PDF-12`) so the primary PDF renders in `PdfDocumentPreviewCard` with live watermark overlay while the optional watermark image renders separately.
- **Non-Blocking Unlock Password Validation (`ToolScreen.kt`)**: Decryption/unlock password fields (`PDF-11`, `CNV-*`) no longer reject short legacy passwords (`< 4` chars); encryption password minimum length (`PDF-10`, `CNV-16`) warns non-blockingly while preserving owner-only or short passwords if desired.
- **IO-Safe Export & Auto-Hydration (`ToolScreen.kt` / `PdfStudioViewModel.kt`)**: SAF exports run on `Dispatchers.IO`; selecting a PDF for `PDF-19` or `PDF-21` automatically hydrates existing PDFBox metadata or AcroForm fields into form inputs.
- **Cipher Accuracy (`PdfVaultEngine.kt` & `PdfVaultTools.kt`)**: `PDF-10` forwards `preferAes = keyAlgo != "RC4-128"` cleanly to `StandardProtectionPolicy`.
