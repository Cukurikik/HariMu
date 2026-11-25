# Desain Upgrade IMG-12 — AI Background Remover (FASE K7a)

Dokumen ini merangkum hasil audit implementasi `IMG-12` saat ini dan dua opsi teknis untuk menggantikan/meningkatkan algoritma flood-fill heuristik menjadi segmentasi subjek penuh dengan antarmuka `BackgroundSegmenter` dan fallback otomatis.

---

## 1. Ringkasan Audit Awal

- **`core/ai/AiLocalEngines.kt` (`removeBackground`)**: memakai flood fill 4-arah dari keempat tepi kanvas berdasarkan jarak warna RGB terhadap rata-rata warna piksel tepi (`threshold = 12..132`), lalu melembutkan alfa lewat `boxBlurAlpha` dan mengisi latar warna/transparan. Batas resolusi: `8 MP` (`MAX_BLUR_PIXELS`). Keterbatasan: subjek yang menyentuh tepi atau latar berpola/gradien ikut terpotong.
- **`feature/tools/AiLocalTools.kt` (`IMG-12`)**: memakai parameter `tolerance` (5–80%), `feather` (0–8 px), dan `background` (`Transparan`, `Putih`, `Hitam`, `#FFE082`) dan memanggil `AiLocalEngines.removeBackground` secara langsung tanpa antarmuka pemisah engine.
- **`core/files/ModelManager.kt` & `core/toolapi/ToolContract.kt`**: `ModelSpec` dan `ModelRegistry.u2NetSeg` (`id = "u2net_seg"`, `42 MB`) sudah ada, namun `ModelRegistry.downloadUrls` sengaja kosong dan belum ada runtime inferensi TFLite/LiteRT/ONNX di dependensi Gradle.
- **Pemanggil Terkait**: `IMG-43` (`ImageForgeBatch2Tools.kt`, operasi batch `"Hapus Latar"`) dan `IMG-47` (`ImageMatrixViewModel.kt` / `ImageMatrixScreen.kt`, aksi `"Hapus latar"` pada layer gambar) sebelumnya juga memanggil `AiLocalEngines.removeBackground` secara langsung.

---

## 2. Perbandingan Dua Opsi

### Opsi (a) — Google ML Kit Subject Segmentation *(Dipilih)*

| Aspek | Rincian |
|---|---|
| **Dependency Baru** | `com.google.android.gms:play-services-mlkit-subject-segmentation` (`versi: verifikasi saat build CI`; kandidat `16.0.0-beta1` dicatat *belum diverifikasi, bergantung hasil CI*) + metadata manifest `com.google.mlkit.vision.DEPENDENCIES = subject_segment` |
| **Lisensi** | Apache-2.0 / Google APIs Terms of Service |
| **Ukuran Unduhan** | Klien tipis di APK (±beberapa ratus KB); modul model Play Services (±beberapa MB) diunduh otomatis oleh Google Play Services saat aplikasi terpasang atau pemanggilan pertama |
| **Keterbatasan** | Memerlukan Google Play Services di perangkat (tidak jalan di AOSP tanpa GMS); butuh internet saat unduh modul pertama kali; artefak `play-services-mlkit-subject-segmentation` berstatus beta; tepi rambut/detail tipis tetap terbantu oleh `featherPx` |

### Opsi (b) — Model Segmentasi via `ModelManager` + Runtime Inferensi Baru

| Aspek | Rincian |
|---|---|
| **Dependency Baru** | Runtime inferensi on-device baru — LiteRT/TensorFlow Lite (`com.google.ai.edge.litert:litert` atau `org.tensorflow:tensorflow-lite`, `versi: verifikasi saat build CI`) atau ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android`, `versi: verifikasi saat build CI`) |
| **Lisensi** | Apache-2.0 (LiteRT/TFLite) atau MIT (ONNX Runtime) + lisensi berkas model (mis. Apache-2.0 untuk U²-Net/MODNet) |
| **Ukuran Unduhan** | Penambahan `.so` runtime di APK/AAB (±4–12 MB per ABI) + unduhan berkas model `u2net_seg` (±42 MB per `ModelRegistry.u2NetSeg`) ke `noBackupFilesDir/models` |
| **Keterbatasan** | Beban unduhan model besar (42+ MB); membutuhkan URL CDN/R2 & SHA-256 resmi di `ModelRegistry.downloadUrls` (saat ini kosong); butuh kode pra/pasca-pemrosesan tensor khusus per arsitektur model |

---

## 3. Keputusan Pemilik & Rancangan Antarmuka

Pemilik memilih **Opsi (a) Google ML Kit Subject Segmentation** dengan persetujuan penambahan dependency (`belum diverifikasi, bergantung hasil CI`) dan perilaku:

1. **Antarmuka `BackgroundSegmenter`** (`core/ai/BackgroundSegmenter.kt`):
   - **Implementasi Dasar (`BasicFloodFillSegmenter`)**: mempertahankan algoritma flood-fill tepi yang sudah ada.
   - **Implementasi Penuh (`MlKitSubjectSegmenter`)**: memakai `SubjectSegmentation.getClient(...)` dengan `enableForegroundConfidenceMask()`, mengonversi `FloatBuffer` confidence (0.0–1.0) menjadi masker alfa, lalu memakai ulang `boxBlurAlpha` & kompositing latar yang sama.
   - **Orkestrator Fallback (`ResilientBackgroundSegmenter`)**: menjalankan **Penuh (ML Kit)** secara default dan otomatis *fallback* ke **Dasar** jika modul Play Services belum tersedia, perangkat tanpa GMS, atau runtime ML Kit gagal (tanpa menelan `CancellationException`).
2. **Keterhubungan IMG-12, IMG-43, dan IMG-47**:
   - Ketiganya memakai `BackgroundSegmenters.default` dengan default **Penuh (ML Kit)**, opsi toggle **Dasar (Heuristik)**, dan pencatatan status engine/fallback pada ringkasan hasil.
