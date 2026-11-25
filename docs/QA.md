# QA Checklist & Test Matrix — HariMu (163 Tool)

Dokumen ini berisi daftar uji untuk seluruh **163 tool** yang terdaftar di `ToolRegistry` dan `02-KATALOG-FITUR.md`, dikelompokkan ke dalam 6 grup.

## Petunjuk Pengisian
- **Kolom `Hasil Aktual`** dan **`Perangkat`** sengaja dibiarkan kosong untuk diisi oleh pemilik proyek atau runner CI pada perangkat nyata / emulator.
- Format penamaan perangkat yang disarankan saat mengisi: `Pixel 7 (Android 15, arm64-v8a)` atau `Redmi Note 12 (Android 13, arm64-v8a)`.
- Untuk tool berlabel **`CL` (AI Gateway)**, pastikan URL Worker valid di **Pengaturan → AI Gateway** dan berikan persetujuan unggah pertama kali. Bila Worker berjalan pada `PROVIDER_MODE=mock`, catat label `MOCK` pada kolom hasil aktual.
- Untuk berkas besar uji ketahanan memori: gunakan video `1080p` berdurasi `>= 2 menit` dan gambar beresolusi `>= 12 MP` (≥ 4000×3000 px).

---

## 1. Core (9 Tool: CORE-01 .. CORE-09)

| ID | Nama Tool | File Sampel Dibutuhkan | Hasil yang Diharapkan | Hasil Aktual | Perangkat |
|---|---|---|---|---|---|
| CORE-01 | Pro Video Editor | 3 klip video `sample_1080p.mp4` (`H.264/AAC`) masing-masing 10–30 detik | Klip tersusun di timeline, trim/kecepatan/rotasi/mute dapat diubah, proyek tersimpan ke Room dan bisa dirender jadi 1 MP4 yang dapat diputar | | |
| CORE-02 | Prompt Video Gen | Teks prompt video (contoh: `"Ombak pantai saat senja, gerak lambat"`) + AI Gateway aktif | Job dikirim, polling berjalan hingga status selesai, video MP4 terunduh (atau ditandai MOCK jika Worker mock); gagal dengan pesan jelas jika gateway belum diisi | | |
| CORE-03 | Generate Speech (TTS) | Teks uji 1–2 kalimat (ID untuk mode Perangkat, EN untuk mode Gateway) | Mode Perangkat menghasilkan `.wav` offline; mode Gateway (English, ≤4000 kar.) menghasilkan `.wav` dari Worker | | |
| CORE-04 | Analyze Images | 1–2 foto `.jpg`/`.png` + pertanyaan teks + AI Gateway aktif | Laporan analisis `.txt` dan ringkasan teks dihasilkan tanpa error; pesan jelas jika tanpa izin unggah atau tanpa URL gateway | | |
| CORE-05 | Screen Recorder | Tidak butuh file input (akses izin `MediaProjection` + opsional `RECORD_AUDIO`/`POST_NOTIFICATIONS`) | Merekam layar ke MP4 H.264/AAC (atau WebM VP8/Opus di API 29+), kontrol jeda/lanjut/stop muncul di notifikasi, file tersimpan ke riwayat & bisa diputar | | |
| CORE-06 | Voice Recorder | Tidak butuh file input (akses izin `RECORD_AUDIO` + `POST_NOTIFICATIONS`) | Spektrum FFT 40 pita bergerak live, jeda/lanjut berfungsi, jeda otomatis saat fokus audio hilang, hasil WAV/M4A/WebM tersimpan dan bisa diputar | | |
| CORE-07 | Key Vault Kripto | 1 berkas uji kecil (`test_doc.pdf` / `note.txt`) untuk enkripsi & tanda tangan | Kunci AES-256 (Keystore non-ekspor), RSA-2048/4096, & ECDSA dibuat; enkripsi/dekripsi AES-GCM dan sign/verify lulus; ekspor PEM melewati gerbang kunci layar perangkat | | |
| CORE-08 | Backup Workspace (.omni) | Minimal 1 entri riwayat, favorit, dan setelan aktif + kata sandi ≥ 8 karakter | Ekspor menghasilkan arsip `.omni` (`OMNIENC2`); setelah hapus/ubah data, impor dengan kata sandi memulihkan setelan, favorit, & riwayat secara atomik | | |
| CORE-09 | Setelan & Koneksi Cloud | Kredensial uji (S3/R2, Drive OAuth PKCE, Dropbox OAuth PKCE, atau REST kustom) + 1 file hasil | Tes koneksi berhasil, kredensial tersimpan terenkripsi (`SecretBox`), unggah dari `ResultCard` masuk antrean dan terunggah atau retry 3× saat offline | | |

---

## 2. Video Forge (30 Tool: VID-01 .. VID-30)

| ID | Nama Tool | File Sampel Dibutuhkan | Hasil yang Diharapkan | Hasil Aktual | Perangkat |
|---|---|---|---|---|---|
| VID-01 | Trim Video | 1 video `.mp4` durasi ≥ 30 detik (`1080p`) | Strip thumbnail muncul, rentang mulai–selesai memotong video menjadi `.mp4` akurat tanpa memotong audio di luar batas | | |
| VID-02 | Merge Video | 2–3 klip `.mp4` beresolusi sama atau berbeda | Seluruh klip tergabung berurutan menjadi 1 berkas `.mp4` mulus dengan sinkronisasi audio terjaga | | |
| VID-03 | Crop Video | 1 video `.mp4` (`16:9` atau `9:16`) | Video terpotong sesuai rasio target (`1:1`, `16:9`, `9:16`) ke berkas `.mp4` baru tanpa distorsi aspek | | |
| VID-04 | Resize Video | 1 video `1080p.mp4` | Video di-encode ulang ke target (`720p`/`480p`/`360p`) dengan dimensi proporsional genap & ukuran lebih ringkas | | |
| VID-05 | Change Video Speed | 1 video `.mp4` dengan audio (10–20 detik) | Kecepatan berubah (`0.5x`–`2.0x`), pitch/tempo audio mengikuti atau disesuaikan tanpa crash | | |
| VID-06 | Video to GIF | 1 video `.mp4` pendek (3–10 detik) | Menghasilkan `.gif` animasi 2-pass (`palettegen`/`paletteuse`) dengan warna halus dan ukuran wajar | | |
| VID-07 | Video Upscaler (1.5x–2x) | 1 video `.mp4` `480p`/`720p` (≤ 60 detik, input ≤ 1080p) | Ketajaman `0`: MP4 H.264 lewat Media3 (hingga 2160p); ketajaman `>0`: jalur FFmpeg `mpeg4`+`unsharp` (cek kualitas & pesan batas bila >60s/>1080p) | | |
| VID-08 | Video to Audio (MP3) | 1 video `.mp4` yang memiliki trek audio | Mengekstrak trek audio menjadi `.mp3` yang dapat diputar dan durasinya sama dengan video sumber | | |
| VID-09 | Rotate Video | 1 video `.mp4` | Video berputar `90°`/`180°`/`270°` dengan orientasi tampilan dan dimensi benar saat diputar | | |
| VID-10 | Flip Video | 1 video `.mp4` (punya teks/asimetris agar terlihat) | Video terbalik horizontal/vertikal pada `.mp4` hasil | | |
| VID-11 | Stabilize Video | 1 video `.mp4` rekaman handheld bergoyang | Video diproses filter `deshake` LGPL menjadi `.mp4` lebih stabil tanpa crash codec | | |
| VID-12 | Loop Video | 1 video `.mp4` pendek (3–5 detik) + kartu batas pengaman 10 menit | Video terulang sesuai jumlah loop (hingga batas aman 10 menit) dalam 1 berkas `.mp4` | | |
| VID-13 | Change Video Volume | 1 video `.mp4` beraudio | Volume audio berubah sesuai pengali dB/% tanpa merusak stream video | | |
| VID-14 | Remove Logo (blur) | 1 video `.mp4` dengan watermark pojok + pratinjau kotak ROI | Kotak ROI pratinjau cocok dengan koordinat; area target pada video `.mp4` hasil terblur rapi | | |
| VID-15 | Add Text to Video | 1 video `.mp4` + teks label | Teks tampil pada video `.mp4` hasil pada posisi/ukuran yang dipilih | | |
| VID-16 | Add Audio to Video | 1 video `.mp4` + 1 file `.mp3`/`.wav` sekunder | Audio latar masuk ke video (mengganti atau mencampur audio asli sesuai mode) | | |
| VID-17 | Add Image to Video | 1 video `.mp4` + 1 gambar `.png` transparan sekunder | Gambar overlay menempel di posisi & skala yang ditentukan sepanjang video | | |
| VID-18 | Video Converter | 1 video `.mp4` atau `.mkv` | Transcoding ke format tujuan (`MP4`, `WebM`, `AVI`, `MKV`) berhasil dan berkas dapat dibuka | | |
| VID-19 | Reverse Video | 1 video `.mp4` (uji 8 detik dan uji >20 detik dengan kartu pengaman RAM) | Video & audio terputar mundur; klip >20 detik otomatis diproses per segmen 12 detik tanpa OOM (batas maks 3 menit) | | |
| VID-20 | Video Compressor | 1 video `1080p.mp4` (`≥ 2 menit`) | Ukuran berkas `.mp4` hasil mengecil sesuai target bitrate/kualitas tanpa error memori | | |
| VID-21 | Video Splitter | 1 video `.mp4` durasi ≥ 30 detik | Video terpecah per rentang pilihan atau segmen N detik menjadi berkas terpisah / `.zip` | | |
| VID-22 | Extract Frames | 1 video `.mp4` (10 detik) | Frame diekstrak ke gambar PNG/JPG pada interval/jumlah terpilih dalam arsip `.zip` | | |
| VID-23 | Add Subtitles (SRT) | 1 video `.mp4` + 1 berkas `.srt` / `.vtt` sekunder | Subtitle terbakar (hardsub) atau terpasang rapi sesuai sinkronisasi waktu `.srt` | | |
| VID-24 | Remove Audio | 1 video `.mp4` beraudio | Menghasilkan `.mp4` tanpa trek audio (ikon/probe menunjukkan 0 trek audio) | | |
| VID-25 | Blur Faces | 1 video `.mp4` dengan wajah jelas menghadap kamera + tombol pratinjau wajah | Pratinjau mendeteksi kotak wajah (`ML Kit`); video hasil mengaburkan wajah secara otomatis | | |
| VID-26 | Color Grading | 1 video `.mp4` | Penyesuaian kecerahan, kontras, saturasi, atau preset terlihat di `.mp4` hasil | | |
| VID-27 | Video Transitions | 2–3 klip video `.mp4` | Klip digabungkan dengan efek transisi (fade/dissolve/slide) antar segmen | | |
| VID-28 | Green Screen (Chroma Key) | 1 video subjek latar hijau `.mp4` + 1 gambar/video latar opsional | Latar hijau diganti latar pilihan (atau warna solid/transparan sesuai mode) pada `.mp4` | | |
| VID-29 | Stop Motion | 5–10 foto `.jpg`/`.png` berurutan | Foto dirangkai menjadi video `.mp4` dengan FPS yang diatur | | |
| VID-30 | Time Lapse | 1 video `.mp4` ≥ 30 detik | Video dipercepat menjadi time-lapse singkat `.mp4` dengan frame halus | | |

---

## 3. Audio Forge & Audio Studio (28 Tool: AUD-01 .. AUD-28)

| ID | Nama Tool | File Sampel Dibutuhkan | Hasil yang Diharapkan | Hasil Aktual | Perangkat |
|---|---|---|---|---|---|
| AUD-01 | Mastering Hub | 1 file `.mp3` / `.wav` stereo | Preset studio + kompresor + limiter menghasilkan audio `.wav`/`.mp3` lebih padat tanpa clipping | | |
| AUD-02 | Pro Equalizer 10-band | 1 file `.mp3` / `.wav` stereo (layar `EqualizerScreen`) | Pratinjau live (`DynamicsProcessing`/`Equalizer`) + tombol Bypass bekerja real-time; ekspor FFmpeg menghasilkan kurva setara & bisa dibandingkan di pratinjau Before/After | | |
| AUD-03 | AI Noise Reduction | 1 rekaman suara `.wav`/`.m4a` dengan derau latar | Derau latar berkurang sesuai slider kekuatan (`afftdn`) tanpa merusak suara utama | | |
| AUD-04 | Vocal Remover | 1 lagu `.mp3`/`.wav` stereo (vokal di tengah) | Mode cepat (kanselasi kanal tengah / bandpass) menekan vokal; label keterbatasan mode AI jujur | | |
| AUD-05 | 8D Audio Engine | 1 lagu `.mp3`/`.wav` stereo (uji dengan headphone) | Efek rotasi spasial 360° & kecepatan putar terasa berpindah kiri–kanan | | |
| AUD-06 | Change Audio Pitch | 1 file `.wav` / `.mp3` | Nada naik/turun (semitone) tanpa merusak durasi tempo | | |
| AUD-07 | Change Audio Speed | 1 file `.wav` / `.mp3` | Tempo cepat/lambat (`0.5x`–`2.0x`) sesuai faktor kecepatan | | |
| AUD-08 | Change Audio Volume | 1 file `.wav` / `.mp3` | Amplitudo/volume berubah sesuai pengaturan dB | | |
| AUD-09 | Trim Audio | 1 file `.mp3`/`.wav` ≥ 20 detik + kartu waveform | Waveform tampil; pemilihan rentang mulai–akhir memotong audio secara presisi | | |
| AUD-10 | Merge Audio | 2–3 file `.mp3`/`.wav` | Mode urut menyambung berurutan; mode mix mencampur secara bersamaan ke 1 file output | | |
| AUD-11 | Audio Compressor (32k–320k) | 1 file `.wav` besar | Ukuran file MP3/AAC mengecil sesuai pilihan bitrate `32k`–`320k` | | |
| AUD-12 | Loudness Normalizer (-16 LUFS) | 1 file `.wav`/`.mp3` pelan | Normalisasi 2-pass `loudnorm` membawa loudness mendekati `-16 LUFS` | | |
| AUD-13 | Audio Converter | 1 file `.wav` atau `.mp3` | Konversi ke format pilihan (`MP3`, `WAV`, `OGG`, `FLAC`, `M4A`, `OPUS`) berhasil dan dapat diputar | | |
| AUD-14 | Audio Resampler (8–96 kHz) | 1 file `.wav` 44.1 kHz | Sample rate file output berubah sesuai pilihan (mis. `48000 Hz` / `96000 Hz` / `16000 Hz`) | | |
| AUD-15 | Audio Channel Manipulator | 1 file `.wav` stereo | Konversi stereo → mono / swap L-R / isolasi L atau R menghasilkan berkas sesuai kanal | | |
| AUD-16 | Audio Silence Remover | 1 rekaman suara `.wav` dengan jeda hening panjang (>2 detik) | Bagian hening di awal/tengah/akhir terpangkas sehingga durasi memendek | | |
| AUD-17 | Reverse Audio | 1 file `.wav` / `.mp3` ucapan | Audio terputar terbalik dari akhir ke awal | | |
| AUD-18 | Audio Metadata Editor (ID3) | 1 file `.mp3` + 1 gambar sampul `.jpg` opsional | Tag ID3 (judul, artis, album, tahun, genre) terisi otomatis saat dipilih & tersimpan di `.mp3` hasil | | |
| AUD-19 | Audio Watermark | 1 file `.wav` utama + 1 suara penanda `.wav`/`.mp3` opsional | Suara penanda disisipkan berulang sesuai interval yang ditentukan tanpa klip | | |
| AUD-20 | AI Stem Splitter | 1 lagu `.mp3`/`.wav` stereo | Menghasilkan 2 jalur mid/side DSP (bukan Demucs AI 4-stem; ringkasan menjelaskan batas secara jujur) | | |
| AUD-21 | Audio Studio Pro (DAW multitrack) | 4 file audio × 3 klip per track (layar `AudioStudioScreen`) | Track & klip tersusun di timeline dengan waveform, zoom, pan/volume/mute/solo per track, playhead real-time lancar tanpa glitch berat, undo/redo + Room autosave, ekspor ke WAV/MP3/FLAC | | |
| AUD-22 | Audio Enhancer | 1 rekaman suara `.wav`/`.mp3` | Kejernihan vokal/spektrum meningkat sesuai preset/kekuatan | | |
| AUD-23 | Audio Splitter | 1 file `.mp3`/`.wav` ≥ 30 detik | Audio terpotong per durasi N detik atau jumlah bagian ke dalam arsip `.zip` | | |
| AUD-24 | Add Echo | 1 file `.wav` ucapan | Efek pantulan gema terdengar sesuai delay & decay | | |
| AUD-25 | Add Reverb | 1 file `.wav` vokal/instrumen | Efek ruang gema (hall/room/plate) terdengar halus tanpa clipping | | |
| AUD-26 | Audio Fade | 1 file `.mp3`/`.wav` | Volume masuk (fade-in) dan keluar (fade-out) halus sesuai durasi detik | | |
| AUD-27 | Speech to Text | 1 rekaman ucapan `.wav`/`.mp3` jernih + AI Gateway aktif | Transkrip teks / `.txt` dihasilkan lewat AI Gateway; tanpa URL gateway/izin unggah gagal dengan pesan jelas | | |
| AUD-28 | Audio Visualizer | 1 file `.mp3`/`.wav` (10–20 detik) | Video `.mp4` merender gelombang/spektrum/vectorscope sesuai warna & gaya pilihan serta tersinkron audio | | |

---

## 4. Image Forge & Image Matrix (47 Tool: IMG-01 .. IMG-47)

| ID | Nama Tool | File Sampel Dibutuhkan | Hasil yang Diharapkan | Hasil Aktual | Perangkat |
|---|---|---|---|---|---|
| IMG-01 | Image Cropper Pro | 1 foto `.jpg` (`≥ 12 MP`) | Foto terpotong sesuai rasio/koordinat tanpa OOM dan menyimpan resolusi penuh hasil crop | | |
| IMG-02 | Smart Image Resizer | 1 foto `.jpg` (`≥ 12 MP`) | Dimensi gambar berubah sesuai ukuran/persentase target dengan aspek terjaga | | |
| IMG-03 | Image Rotator | 1 foto `.jpg` berorientasi EXIF | Gambar terputar sesuai sudut (`90°`/`180°`/`270°`/bebas) dengan orientasi EXIF ditangani benar | | |
| IMG-04 | Image Flipper | 1 foto `.jpg` | Gambar terbalik horizontal atau vertikal | | |
| IMG-05 | Canvas Expander | 1 foto `.jpg` / `.png` | Kanvas diperluas dengan warna solid/transparan/blur dan foto diposisikan rapi | | |
| IMG-06 | Perspective Corrector | 1 foto dokumen miring `.jpg` + geser 4 titik pratinjau | 4 titik sudut interaktif dapat diseret; dokumen hasil `Matrix.setPolyToPoly` terurai tegak lurus | | |
| IMG-07 | Image Straightener | 1 foto horizon miring `.jpg` | Foto terputar halus (`-15°`..`+15°`) dengan crop otomatis bebas tepi hitam | | |
| IMG-08 | Smart Auto-Crop | 1 foto subjek menonjol `.jpg` | Subjek utama terdeteksi dan terpotong otomatis mendekati subjek | | |
| IMG-09 | Image Alignment | 2–4 foto `.png`/`.jpg` | Foto tersusun/diratakan (horizontal/vertikal/grid) menjadi 1 gambar komposit | | |
| IMG-10 | Multi-layer Crop | 1 foto `.jpg` (`≥ 12 MP`) | Menghasilkan arsip `.zip` berisi beberapa potongan rasio sekaligus (`1:1`, `16:9`, `9:16`, dll.) | | |
| IMG-11 | AI Image Upscaler / Super Resolution | 1 foto kecil (`400×400 px`) `.png`/`.jpg` | Skala gambar naik dengan penajaman heuristik (ringkasan jujur bahwa ini bukan model neural) | | |
| IMG-12 | AI Background Remover | 1 foto orang/produk latar kontras `.jpg` (uji dengan Play Services aktif & mode Dasar) | Mode Penuh memakai ML Kit Subject Segmentation menghasilkan PNG transparan tepi halus; bila Play Services tak tersedia otomatis fallback ke flood-fill Dasar dengan catatan jelas di ringkasan | | |
| IMG-13 | AI Object Remover | 1 foto `.jpg` + deskripsi objek + AI Gateway aktif | Foto hasil edit dikembalikan dari AI Gateway (atau ditandai MOCK); gagal jelas tanpa gateway | | |
| IMG-14 | AI Photo Enhancer | 1 foto gelap/buram `.jpg` + AI Gateway aktif | Foto perbaikan dikembalikan dari AI Gateway (atau ditandai MOCK) | | |
| IMG-15 | AI Face Restorer | 1 foto potret lama `.jpg` + AI Gateway aktif | Foto restorasi wajah dikembalikan dari AI Gateway (atau ditandai MOCK) | | |
| IMG-16 | AI Style Transfer | 1 foto `.jpg` + pilih preset gaya + AI Gateway aktif | Foto bergaya seni dikembalikan dari AI Gateway (atau ditandai MOCK) | | |
| IMG-17 | AI Image Colorizer | 1 foto hitam-putih `.jpg` + AI Gateway aktif | Foto berwarna dikembalikan dari AI Gateway (atau ditandai MOCK) | | |
| IMG-18 | AI Sky Replacement | 1 foto pemandangan langit `.jpg` + AI Gateway aktif | Foto dengan langit baru dikembalikan dari AI Gateway (atau ditandai MOCK) | | |
| IMG-19 | AI Portrait Bokeh | 1 foto potret `.jpg` | Latar luar radius potret kabur secara radial dengan fokus tetap di tengah | | |
| IMG-20 | AI Cartoonizer | 1 foto wajah/objek `.jpg` + AI Gateway aktif | Ilustrasi kartun dikembalikan dari AI Gateway (atau ditandai MOCK) | | |
| IMG-21 | AI Image Generator | Prompt teks (contoh: `"Kucing oren di jendela pagi"`) + AI Gateway aktif | Gambar PNG/JPG dihasilkan dari Worker (atau ditandai MOCK); tanpa gateway gagal dengan pesan jelas | | |
| IMG-22 | AI Image Denoiser | 1 foto ber-noise malam `.jpg` | Noise gambar berkurang memakai filter smoothing tepi | | |
| IMG-23 | Image Format Converter | 1 gambar sumber (`.jpg`/`.png`/`.webp`/`.bmp`, dst.) | Konversi ke JPG/PNG/WebP/HEIC/AVIF (sesuai API perangkat) berhasil; format tak didukung penuh ditolak dengan pesan jelas sesuai matriks `DECISIONS.md` | | |
| IMG-24 | Icon Generator | 1 ikon sumber kotak `.png` (`1024×1024`) | Menghasilkan `.zip` berisi semua ukuran Android mipmap, iOS AppIcon, dan `favicon.ico`/PNG | | |
| IMG-25 | Base64 Encoder/Decoder | 1 gambar kecil `.png` atau string Base64 | Encode menghasilkan teks `data:image/...;base64,...` (bisa disalin); Decode menghasilkan berkas gambar valid | | |
| IMG-26 | Smart Image Compressor | 1 foto `.jpg` (`≥ 12 MP`) | Perkiraan & hasil ukuran mengecil sesuai kualitas atau target KB tanpa OOM | | |
| IMG-27 | Web Image Optimizer | 1 foto `.jpg`/`.png` besar | Menghasilkan gambar WebP/JPG teroptimasi beserta laporan penghematan ukuran byte | | |
| IMG-28 | Color Palette Extractor (K-Means) | 1 foto penuh warna `.jpg` | Mengekstrak N warna dominan (kode HEX) beserta kartu pratinjau palet | | |
| IMG-29 | Image Metadata Stripper & EXIF Editor | 1 foto `.jpg` kamera dengan GPS/EXIF lengkap | Mode Strip menghapus GPS/EXIF sensitif; mode Edit memperbarui tag EXIF (Copyright/Artist/UserComment) dan menjaga orientasi | | |
| IMG-30 | Image File Size Analyzer | 1 foto `.jpg`/`.png` besar | Menampilkan laporan dimensi, megapiksel, estimasi RAM decoded, serta perbandingan ukuran JPG/PNG/WebP | | |
| IMG-31 | Watermark Inserter | 1 foto `.jpg` + teks watermark | Teks watermark (sudut/tile, opasitas, ukuran) tercetak rapi pada gambar hasil | | |
| IMG-32 | Image Overlay / Blender | 2 gambar `.png`/`.jpg` | Kedua gambar tergabung dengan mode blend (Normal/Multiply/Screen/Overlay) dan opasitas | | |
| IMG-33 | Advanced Filters | 1 foto `.jpg` + pratinjau GPU langsung | Pratinjau GPU (`FilterPreviewCard`) memperbarui warna real-time; ekspor menghasilkan gambar dengan matrix warna identik + blur/vignette | | |
| IMG-34 | Border & Frame Generator | 1 foto `.jpg` | Bingkai dengan lebar, sudut melengkung, & warna pilihan ditambahkan di sekeliling foto | | |
| IMG-35 | Text-to-Image Typography | Teks kutipan + parameter warna/latar | Poster gambar `.png` merender tipografi teks rata rapi di atas kanvas | | |
| IMG-36 | Gradient Overlay Generator | 1 foto `.jpg` | Gradien warna (linear/radial, arah, opasitas) melapisi foto | | |
| IMG-37 | Shadow & Glow Effects | 1 gambar `.png` transparan / stiker | Drop shadow atau outer glow muncul lembut di belakang subjek transparan | | |
| IMG-38 | Sticker / Emoji Overlay | 1 foto `.jpg` + pilih stiker/emoji | Stiker/emoji terpasang di koordinat & skala pilihan pada foto | | |
| IMG-39 | LUT Color Filter Import | 1 foto `.jpg` + 1 file `.cube`/`.png` LUT (atau fallback bawaan) | Profil warna LUT `.cube` diterapkan ke gambar hasil | | |
| IMG-40 | Vintage / Film Simulation | 1 foto `.jpg` | Efek film klasik (grain, fade, tone hangat/dingin) diterapkan ke foto | | |
| IMG-41 | Lens Flare Generator | 1 foto pemandangan `.jpg` | Efek kilau cahaya (flare) muncul pada koordinat dan intensitas pilihan | | |
| IMG-42 | Reflection / Mirror Effects | 1 foto `.jpg` | Efek refleksi air / cermin terpasang di sisi gambar pilihan | | |
| IMG-43 | Batch Processing | 3–5 foto `.jpg` sekaligus (termasuk aksi Hapus Latar) | Semua foto diproses sekaligus menjadi `.zip` + `laporan_batch.csv` (mencatat engine Penuh/Dasar bila hapus latar) | | |
| IMG-44 | Sprite Sheet Generator | 4–8 frame `.png` ukuran seragam | Frame disusun dalam grid sprite sheet `.png` (serta deskripsi koordinat) | | |
| IMG-45 | Animated GIF Maker | 3–6 gambar `.jpg`/`.png` | Gambar digabung menjadi 1 berkas `.gif` animasi dengan jeda FPS pilihan | | |
| IMG-46 | Video Frame Extractor | 1 video `.mp4` (alias `VID-22`) | Frame diekstrak ke `.zip` gambar PNG/JPG sesuai interval | | |
| IMG-47 | Image Matrix AI (Kanvas Editor) | 2 gambar `.jpg`/`.png` di layar `ImageMatrixScreen` | Multi-layer teks & gambar dapat dipindah, diatur urutan/visibilitas/blend, Hapus Latar layer memakai ML Kit Penuh + toggle Dasar, Undo/Redo, & ekspor PNG/JPG | | |

---

## 5. PDF Vault (21 Tool: PDF-01 .. PDF-21)

| ID | Nama Tool | File Sampel Dibutuhkan | Hasil yang Diharapkan | Hasil Aktual | Perangkat |
|---|---|---|---|---|---|
| PDF-01 | Merge PDF | 2–3 berkas `.pdf` (teks vektor tetap tajam) | Tergabung berurutan menjadi 1 `.pdf` melalui PDFBox tanpa merasterisasi teks | | |
| PDF-02 | Split PDF (.zip) | 1 `.pdf` minimal 4 halaman | Terpecah setiap N halaman ke beberapa PDF di dalam `.zip` | | |
| PDF-03 | Rotate PDF | 1 `.pdf` + rentang halaman opsional | Halaman target berputar `90°`/`180°`/`270°` sesuai atribut rotasi PDF | | |
| PDF-04 | Reorder Pages | 1 `.pdf` 3–5 halaman + seret thumbnail pratinjau | Thumbnail halaman tampil, seret urutan memperbarui daftar, `.pdf` hasil tersusun sesuai urutan baru | | |
| PDF-05 | Extract Pages | 1 `.pdf` 4+ halaman + rentang mis. `1,3-4` | Menghasilkan `.pdf` baru hanya berisi halaman yang dipilih | | |
| PDF-06 | PDF to Word/Excel/PowerPoint | 1 `.pdf` berisi paragraf teks / tabel | Mode Dasar menghasilkan `.docx` / `.xlsx` / `.pptx` (PPTX berupa gambar halaman); ringkasan menjelaskan batas tata letak | | |
| PDF-07 | Word to PDF | 1 berkas `.docx` teks sederhana | Mode Dasar mengonversi paragraf `.docx` ke `.pdf` (tanpa tabel/gambar kompleks) | | |
| PDF-08 | Image to PDF | 2–4 foto `.jpg`/`.png` | Foto tersusun menjadi halaman `.pdf` (A4, Letter, atau Fit Gambar) | | |
| PDF-09 | Compress PDF | 1 `.pdf` berisi gambar beresolusi tinggi | PDF dirasterisasi & dikompres; menampilkan perbandingan ukuran sebelum vs sesudah | | |
| PDF-10 | Encrypt PDF | 1 `.pdf` terbuka + kata sandi | PDF hasil terkunci dengan AES-256/AES-128/RC4-128; membuka di viewer meminta kata sandi | | |
| PDF-11 | Decrypt PDF | 1 `.pdf` terenkripsi + kata sandi yang benar | PDF terbuka tanpa kata sandi setelah didekripsi; kata sandi salah ditolak dengan pesan jelas | | |
| PDF-12 | Add Watermark | 1 `.pdf` + teks atau gambar watermark sekunder | Watermark teks/gambar muncul pada halaman PDF hasil dengan opasitas & sudut sesuai | | |
| PDF-13 | Remove Watermark | 1 `.pdf` beranotasi/OCG watermark | Mode Dasar menghapus watermark yang berupa anotasi/OCG terpisah (jujur menolak/melaporkan bila watermark menyatu ke stream konten) | | |
| PDF-14 | Redact Content (permanen) | 1 `.pdf` teks + koordinat area redact | Kotak hitam permanen menutupi area dan halaman diratakan (teks tertutup tidak dapat disalin/ekstrak lagi) | | |
| PDF-15 | Scan PDF to Text (OCR) | 1 `.pdf` hasil pindai gambar (ID/EN) + koneksi untuk unduhan awal `tessdata` | Data bahasa `tessdata_fast` terunduh sekali (bila belum ada), menghasilkan `.txt` hasil OCR yang terbaca | | |
| PDF-16 | Image to Searchable PDF | 1–2 foto halaman dokumen `.jpg` | Menghasilkan `.pdf` dengan lapisan teks tak terlihat sehingga teks bisa disalin/dicari | | |
| PDF-17 | AI Document Recognition | 1 `.pdf` 1–2 halaman (faktur/surat) + AI Gateway aktif | Halaman dirender JPEG dan dianalisis Worker menghasilkan struktur JSON `.txt`; gagal jelas tanpa gateway | | |
| PDF-18 | Page Numbering | 1 `.pdf` 3+ halaman | Nomor halaman (`{n}/{total}`) tercetak pada posisi pilihan (4 sudut/tepi) | | |
| PDF-19 | Metadata Editor | 1 `.pdf` | Judul, penulis, subjek, & kata kunci PDF diperbarui di kamus metadata | | |
| PDF-20 | Document Comparison | 2 berkas `.pdf` dengan perbedaan minor teks/tata letak | Menghasilkan laporan teks diff per baris serta gambar diff visual per halaman | | |
| PDF-21 | Form Filler (AcroForm) | 1 `.pdf` memiliki field formulir AcroForm | Nilai field terisi dan tersimpan pada PDF hasil (formulir XFA ditolak secara jelas) | | |

---

## 6. Universal Converter (28 Tool: CNV-01 .. CNV-28)

| ID | Nama Tool | File Sampel Dibutuhkan | Hasil yang Diharapkan | Hasil Aktual | Perangkat |
|---|---|---|---|---|---|
| CNV-01 | Universal Video Transcoder | 1 video `.mp4` / `.mkv` (alias `VID-18`) | Transcoding ke MP4/WebM/AVI/MKV berhasil dan file hasil dapat diputar | | |
| CNV-02 | Video to Animated GIF | 1 video `.mp4` pendek (alias `VID-06`) | GIF animasi 2-pass tercipta tanpa banding warna berlebih | | |
| CNV-03 | Video to Audio Extractor | 1 video `.mp4` beraudio (perluasan `VID-08`) | Audio diekstrak ke pilihan format MP3, WAV, FLAC, atau AAC | | |
| CNV-04 | Video Upscaler (AI) 2x | 1 video `.mp4` ≤ 1080p & ≤ 60 detik (alias `VID-07`) | Media3 H.264 (atau `mpeg4`+`unsharp` bila ketajaman > 0) menghasilkan video 1.5x/2x | | |
| CNV-05 | Universal Audio Transcoder | 1 file `.wav` / `.mp3` (alias `AUD-13`) | Konversi ke MP3/WAV/OGG/FLAC/M4A/OPUS berhasil | | |
| CNV-06 | Hi-Res Lossless Converter | 1 file `.wav` / `.flac` | Ekspor FLAC/WAV 24-bit hingga 96 kHz valid; pesan jelas bahwa upsampling tidak menambah detail | | |
| CNV-07 | Voice to Text Converter | 1 file `.wav` / `.mp3` ucapan + AI Gateway aktif (alias `AUD-27`) | Transkrip teks dihasilkan oleh Worker AI Gateway | | |
| CNV-08 | 8D / Spatial Audio Generator | 1 lagu `.mp3` / `.wav` stereo (alias `AUD-05`) | Audio 8D berputar 360° dihasilkan | | |
| CNV-09 | Universal Image Converter | 1 gambar `.jpg` / `.png` / `.webp` (alias `IMG-23`) | Konversi ke PNG/JPG/WebP/AVIF (tergantung dukungan codec API perangkat) berhasil | | |
| CNV-10 | SVG to Raster (PNG/JPG) | 1 berkas vektor `.svg` | SVG dirender ke PNG transparan atau JPG solid hingga lebar 4096 px (filter/animasi diabaikan sesuai AndroidSVG) | | |
| CNV-11 | Icon Asset Generator | 1 ikon kotak `.png` `1024×1024` (alias `IMG-24`) | Arsip `.zip` berisi aset Android, iOS, & favicon | | |
| CNV-12 | Base64 Image Encoder | 1 gambar `.png` kecil atau string Base64 (alias `IMG-25`) | Konversi gambar ⇄ string Base64 berfungsi dua arah | | |
| CNV-13 | PDF to Word / Word to PDF | 1 `.pdf` atau 1 `.docx` (router `PDF-06`/`PDF-07`) | `.pdf` dirutekan menjadi `.docx`; `.docx` dirutekan menjadi `.pdf` mode Dasar | | |
| CNV-14 | PDF to Image Matrix | 1 `.pdf` 2–3 halaman | Semua halaman dirender lewat `PdfRenderer` ke PNG/JPG (skala 1–3x) dalam `.zip` | | |
| CNV-15 | OCR Document Scanner | 1 `.pdf` scan gambar (alias `PDF-15`) | Teks OCR diekstrak ke `.txt` memakai Tesseract 8 bahasa | | |
| CNV-16 | Universal Archive Extractor (.zip) | 1 arsip `.zip` normal & 1 arsip uji path `../` | ZIP normal diekstrak bersih; entri zip-slip disanitasi & batas ukuran 128 MB/entri + rasio 200× melindungi dari zip-bomb | | |
| CNV-17 | Cross-Archive Converter | 1 arsip `.7z`, `.tar.gz`, `.rar`, atau `.zip` | Seluruh format terbaca dan ditulis ulang menjadi arsip `.zip` bersih (RAR baca-saja) | | |
| CNV-18 | AES-256 Encrypted Archive | 2 file uji + kata sandi (uji enkripsi & dekripsi, termasuk `.hzm` lama) | Menghasilkan ZIP AES-256 valid (`zip4j`) kompatibel 7-Zip/WinRAR dan dapat didekripsi kembali | | |
| CNV-19 | EPUB Converter | 1 e-book `.epub` bebas DRM | Teks spine diekstrak ke TXT, HTML, atau PDF (EPUB ber-DRM ditolak) | | |
| CNV-20 | Comic Book Converter (CBZ/CBR) | 1 berkas `.cbz` dan 1 berkas `.cbr` | Halaman terurut alami dan dikemas menjadi PDF atau EPUB valid | | |
| CNV-21 | Universal Font Converter | 1 font `.ttf` dan 1 font `.woff` (uji juga `.woff2`/`.otf` negatif) | Konversi TTF/OTF ⇄ WOFF 1.0 berhasil; `.woff2` & OTF(CFF)⇄TTF ditolak dengan pesan alasan jelas | | |
| CNV-22 | Web-Font Optimizer (subsetting) | 1 font `.ttf` + teks subset karakter | Menghasilkan berkas font subset `.woff` berukuran jauh lebih kecil; CFF/WOFF2 ditolak jelas | | |
| CNV-23 | JSON ⇄ XML | Teks JSON dan teks XML valid | Konversi dua arah menghasilkan dokumen XML/JSON berindentasi rapi & valid | | |
| CNV-24 | CSV ⇄ TSV | Data CSV dengan tanda kutip & koma di dalam sel | Konversi dua arah mempertahankan kutip ganda & baris baru dalam sel (RFC-4180) | | |
| CNV-25 | Code & JSON Formatter | JSON minified & CSS tidak rapi | Pretty-print/minify JSON dan format CSS menghasilkan kode terindentasi & tervalidasi | | |
| CNV-26 | Markdown to HTML | Teks Markdown (heading, list, kode, link, plus link `javascript:` uji negatif) | HTML berdiri sendiri rapi; tautan berbahaya (`javascript:`/`data:`) ditolak/disanitasi | | |
| CNV-27 | GeoJSON to KML | GeoJSON valid (`Point`, `LineString`, `Polygon` berlubang) | Berkas `.kml` valid dengan koordinat & geometri utuh | | |
| CNV-28 | STL to OBJ | 1 model 3D `.stl` ASCII & 1 `.stl` binary | Konversi vertex/face ke berkas `.obj` valid pada kedua format | | |
