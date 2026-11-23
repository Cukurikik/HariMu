package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import com.harimu.app.core.files.ModelRegistry
import com.harimu.app.core.toolapi.InputSpec
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup
import com.harimu.app.core.toolapi.ToolParam

fun stubVideoTools(): List<Tool> = listOf(
    StubTool(
        id = "VID-03", group = ToolGroup.VIDEO, engineLabel = "M3", plannedPhase = "P4",
        icon = Icons.Filled.Crop, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.DropdownParam("ratio", "Rasio", "Aspect Ratio", listOf("1:1", "16:9", "9:16"), "1:1")),
        titleId = "Crop Video", titleEn = "Crop Video",
        descId = "Potong bingkai video ke rasio 1:1, 16:9, atau 9:16.", descEn = "Crop video frame to 1:1, 16:9, or 9:16."
    ),
    StubTool(
        id = "VID-04", group = ToolGroup.VIDEO, engineLabel = "M3", plannedPhase = "P4",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.DropdownParam("res", "Resolusi", "Resolution", listOf("1080p", "720p", "480p", "360p"), "720p")),
        titleId = "Ubah Ukuran Video", titleEn = "Resize Video",
        descId = "Ubah resolusi video ke 1080p/720p/480p/360p.", descEn = "Resize video resolution to 1080p/720p/480p/360p."
    ),
    StubTool(
        id = "VID-05", group = ToolGroup.VIDEO, engineLabel = "M3/FF", plannedPhase = "P4",
        icon = Icons.Filled.Speed, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.SliderParam("speed", "Kecepatan", "Speed", 0.25f, 4.0f, 1.5f, 0.25f, "x")),
        titleId = "Ubah Kecepatan Video", titleEn = "Change Video Speed",
        descId = "Percepat atau perlambat pemutaran video.", descEn = "Speed up or slow down video playback."
    ),
    StubTool(
        id = "VID-06", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P4",
        icon = Icons.Filled.BurstMode, inputSpec = InputSpec.SingleVideo,
        titleId = "Video ke GIF", titleEn = "Video to GIF",
        descId = "Konversi klip video menjadi GIF animasi dengan palet optimal.", descEn = "Convert video clips into palette-optimized animated GIF."
    ),
    StubTool(
        id = "VID-07", group = ToolGroup.VIDEO, engineLabel = "ML/FF", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleVideo,
        requiresModel = ModelRegistry.realEsrganLite,
        titleId = "Video Upscaler (1.5x–4x)", titleEn = "Video Upscaler (1.5x–4x)",
        descId = "Tingkatkan ketajaman resolusi video dengan model AI ringan.", descEn = "Upscale video resolution with lightweight AI model."
    ),
    StubTool(
        id = "VID-09", group = ToolGroup.VIDEO, engineLabel = "M3", plannedPhase = "P4",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.DropdownParam("deg", "Sudut", "Angle", listOf("90", "180", "270"), "90")),
        titleId = "Putar Video", titleEn = "Rotate Video",
        descId = "Putar orientasi video 90°, 180°, atau 270°.", descEn = "Rotate video orientation 90, 180, or 270 degrees."
    ),
    StubTool(
        id = "VID-10", group = ToolGroup.VIDEO, engineLabel = "M3/FF", plannedPhase = "P4",
        icon = Icons.Filled.Flip, inputSpec = InputSpec.SingleVideo,
        titleId = "Balik Video (Flip)", titleEn = "Flip Video",
        descId = "Cerminkan video secara horizontal atau vertikal.", descEn = "Mirror video horizontally or vertically."
    ),
    StubTool(
        id = "VID-11", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P5",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        titleId = "Stabilisasi Video", titleEn = "Stabilize Video",
        descId = "Kurangi guncangan kamera pada rekaman video.", descEn = "Reduce camera shake in recorded footage."
    ),
    StubTool(
        id = "VID-12", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P4",
        icon = Icons.Filled.Loop, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.SliderParam("loops", "Jumlah Loop", "Loops", 2f, 10f, 3f, 1f, "x")),
        titleId = "Loop Video", titleEn = "Loop Video",
        descId = "Ulangi klip video beberapa kali secara mulus.", descEn = "Repeat a video clip seamlessly N times."
    ),
    StubTool(
        id = "VID-13", group = ToolGroup.VIDEO, engineLabel = "M3/FF", plannedPhase = "P4",
        icon = Icons.Filled.VolumeUp, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.SliderParam("vol", "Volume", "Volume", 0f, 200f, 120f, 10f, "%")),
        titleId = "Ubah Volume Video", titleEn = "Change Video Volume",
        descId = "Atur tingkat volume track audio pada video.", descEn = "Adjust audio track volume in video."
    ),
    StubTool(
        id = "VID-14", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P5",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        titleId = "Hapus Logo (Blur/Delogo)", titleEn = "Remove Logo (Blur/Delogo)",
        descId = "Samarkan watermark atau logo pada area tertentu video.", descEn = "Blur or mask logos on a chosen video region."
    ),
    StubTool(
        id = "VID-15", group = ToolGroup.VIDEO, engineLabel = "M3", plannedPhase = "P5",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.TextParam("caption", "Teks", "Overlay Text", "HariMu")),
        titleId = "Tambah Teks ke Video", titleEn = "Add Text to Video",
        descId = "Tempel teks judul atau caption di atas video.", descEn = "Overlay title or caption text onto video."
    ),
    StubTool(
        id = "VID-17", group = ToolGroup.VIDEO, engineLabel = "M3", plannedPhase = "P5",
        icon = Icons.Filled.Image, inputSpec = InputSpec.SingleVideo,
        titleId = "Tambah Gambar ke Video", titleEn = "Add Image to Video",
        descId = "Tempel logo atau stiker gambar pada video.", descEn = "Overlay a logo or image sticker on video."
    ),
    StubTool(
        id = "VID-18", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P4",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.DropdownParam("fmt", "Format", "Format", listOf("MP4", "WebM", "AVI", "MKV"), "MP4")),
        titleId = "Konverter Video", titleEn = "Video Converter",
        descId = "Konversi format MP4, WebM, AVI, dan MKV.", descEn = "Convert between MP4, WebM, AVI, and MKV."
    ),
    StubTool(
        id = "VID-19", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P4",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        titleId = "Putar Balik Video", titleEn = "Reverse Video",
        descId = "Balik arah pemutaran video dan audio dari akhir ke awal.", descEn = "Play video and audio backwards."
    ),
    StubTool(
        id = "VID-20", group = ToolGroup.VIDEO, engineLabel = "M3/FF", plannedPhase = "P4",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        params = listOf(ToolParam.SliderParam("crf", "Kompresi", "Compression", 18f, 35f, 24f, 1f, "")),
        titleId = "Kompresor Video", titleEn = "Video Compressor",
        descId = "Perkecil ukuran file video dengan kontrol bitrate/CRF.", descEn = "Reduce video size with bitrate/CRF control."
    ),
    StubTool(
        id = "VID-23", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P5",
        icon = Icons.Filled.Subtitles, inputSpec = InputSpec.SingleVideo,
        titleId = "Tambah Subtitle (SRT)", titleEn = "Add Subtitles (SRT)",
        descId = "Bakar file subtitle .srt permanen ke dalam video.", descEn = "Burn .srt subtitles directly onto video."
    ),
    StubTool(
        id = "VID-25", group = ToolGroup.VIDEO, engineLabel = "ML", plannedPhase = "P5",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        titleId = "Blur Wajah Video", titleEn = "Blur Faces",
        descId = "Deteksi dan samarkan wajah otomatis per frame.", descEn = "Automatically detect and blur faces per frame."
    ),
    StubTool(
        id = "VID-26", group = ToolGroup.VIDEO, engineLabel = "M3", plannedPhase = "P5",
        icon = Icons.Filled.Tune, inputSpec = InputSpec.SingleVideo,
        titleId = "Color Grading Video", titleEn = "Color Grading",
        descId = "Atur eksposur, kontras, saturasi, dan tone warna video.", descEn = "Adjust video exposure, contrast, saturation, and tone."
    ),
    StubTool(
        id = "VID-27", group = ToolGroup.VIDEO, engineLabel = "M3/FF", plannedPhase = "P5",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.MultipleVideos,
        titleId = "Transisi Video", titleEn = "Video Transitions",
        descId = "Tambahkan transisi fade atau dissolve antar klip.", descEn = "Add fade or dissolve transitions between clips."
    ),
    StubTool(
        id = "VID-28", group = ToolGroup.VIDEO, engineLabel = "FF/GL", plannedPhase = "P5",
        icon = Icons.Filled.Videocam, inputSpec = InputSpec.SingleVideo,
        titleId = "Green Screen (Chroma Key)", titleEn = "Green Screen (Chroma Key)",
        descId = "Ganti latar hijau/biru pada video dengan latar baru.", descEn = "Replace green/blue screen background on video."
    ),
    StubTool(
        id = "VID-29", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P5",
        icon = Icons.Filled.BurstMode, inputSpec = InputSpec.MultipleImages,
        titleId = "Stop Motion", titleEn = "Stop Motion",
        descId = "Rangkai kumpulan foto menjadi video stop motion.", descEn = "Assemble photos into a stop motion video."
    ),
    StubTool(
        id = "VID-30", group = ToolGroup.VIDEO, engineLabel = "FF", plannedPhase = "P5",
        icon = Icons.Filled.Speed, inputSpec = InputSpec.SingleVideo,
        titleId = "Time Lapse", titleEn = "Time Lapse",
        descId = "Padatkan rekaman panjang menjadi video time lapse cepat.", descEn = "Compress long footage into a smooth time lapse."
    )
)

fun stubAudioTools(): List<Tool> = listOf(
    StubTool(
        id = "AUD-01", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P7",
        icon = Icons.Filled.GraphicEq, inputSpec = InputSpec.SingleAudio,
        titleId = "Mastering Hub", titleEn = "Mastering Hub",
        descId = "Rantai mastering audio: EQ, kompresor, dan limiter.", descEn = "Audio mastering chain: EQ, compressor, and limiter."
    ),
    StubTool(
        id = "AUD-03", group = ToolGroup.AUDIO, engineLabel = "FF/ML", plannedPhase = "P7",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        titleId = "Reduksi Derau AI", titleEn = "AI Noise Reduction",
        descId = "Kurangi hiss dan noise latar pada rekaman suara.", descEn = "Reduce background hiss and noise in voice recordings."
    ),
    StubTool(
        id = "AUD-04", group = ToolGroup.AUDIO, engineLabel = "FF/ML", plannedPhase = "P7",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        titleId = "Penghapus Vokal", titleEn = "Vocal Remover",
        descId = "Pisahkan vokal dan instrumen menggunakan filter kanal/AI.", descEn = "Separate vocals and instrumental track."
    ),
    StubTool(
        id = "AUD-05", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P7",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        titleId = "8D Audio Engine", titleEn = "8D Audio Engine",
        descId = "Buat efek suara memutar spasial 360 derajat.", descEn = "Create 360-degree rotating spatial audio effect."
    ),
    StubTool(
        id = "AUD-06", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P6",
        icon = Icons.Filled.Tune, inputSpec = InputSpec.SingleAudio,
        params = listOf(ToolParam.SliderParam("semitones", "Nada (Semitone)", "Pitch (Semitones)", -12f, 12f, 2f, 1f, "st")),
        titleId = "Ubah Pitch Audio", titleEn = "Change Audio Pitch",
        descId = "Naikkan atau turunkan nada tanpa mengubah tempo.", descEn = "Shift pitch up or down without changing tempo."
    ),
    StubTool(
        id = "AUD-07", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P6",
        icon = Icons.Filled.Speed, inputSpec = InputSpec.SingleAudio,
        params = listOf(ToolParam.SliderParam("speed", "Kecepatan", "Speed", 0.5f, 2.0f, 1.25f, 0.05f, "x")),
        titleId = "Ubah Kecepatan Audio", titleEn = "Change Audio Speed",
        descId = "Atur tempo audio tanpa merusak kualitas suara.", descEn = "Adjust audio tempo cleanly."
    ),
    StubTool(
        id = "AUD-11", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P6",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        params = listOf(ToolParam.DropdownParam("bitrate", "Bitrate", "Bitrate", listOf("64k", "128k", "192k", "320k"), "128k")),
        titleId = "Kompresor Audio", titleEn = "Audio Compressor (32k–320k)",
        descId = "Kecilkan ukuran file audio dengan pemilihan bitrate.", descEn = "Shrink audio files with selectable bitrate."
    ),
    StubTool(
        id = "AUD-12", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P6",
        icon = Icons.Filled.VolumeUp, inputSpec = InputSpec.SingleAudio,
        titleId = "Normalisasi Loudness (-16 LUFS)", titleEn = "Loudness Normalizer (-16 LUFS)",
        descId = "Standarkan kenyaringan podcast/musik ke -16 LUFS.", descEn = "Normalize loudness to broadcast -16 LUFS standard."
    ),
    StubTool(
        id = "AUD-13", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P6",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        params = listOf(ToolParam.DropdownParam("format", "Format", "Format", listOf("MP3", "WAV", "OGG", "FLAC"), "MP3")),
        titleId = "Konverter Audio", titleEn = "Audio Converter",
        descId = "Konversi audio antar MP3, WAV, OGG, dan FLAC.", descEn = "Convert audio between MP3, WAV, OGG, and FLAC."
    ),
    StubTool(
        id = "AUD-14", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P6",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        params = listOf(ToolParam.DropdownParam("rate", "Sample Rate", "Sample Rate", listOf("16000", "44100", "48000", "96000"), "48000")),
        titleId = "Resampler Audio (8–96 kHz)", titleEn = "Audio Resampler (8–96 kHz)",
        descId = "Ubah sample rate audio dari 8 kHz hingga 96 kHz.", descEn = "Resample audio between 8 kHz and 96 kHz."
    ),
    StubTool(
        id = "AUD-18", group = ToolGroup.AUDIO, engineLabel = "LIB", plannedPhase = "P6",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        params = listOf(ToolParam.TextParam("artist", "Artis", "Artist", "")),
        titleId = "Editor Metadata Audio (ID3)", titleEn = "Audio Metadata Editor (ID3)",
        descId = "Sunting tag judul, artis, album, dan sampul lagu.", descEn = "Edit title, artist, album, and cover tags."
    ),
    StubTool(
        id = "AUD-19", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P7",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        titleId = "Watermark Audio", titleEn = "Audio Watermark",
        descId = "Sisipkan tanda audio berkala untuk proteksi demo.", descEn = "Mix periodic audio watermark for demo protection."
    ),
    StubTool(
        id = "AUD-20", group = ToolGroup.AUDIO, engineLabel = "ML/CL", plannedPhase = "P7",
        icon = Icons.Filled.GraphicEq, inputSpec = InputSpec.SingleAudio,
        requiresModel = ModelRegistry.demucsLite,
        titleId = "AI Stem Splitter (4 Track)", titleEn = "AI Stem Splitter (4 Tracks)",
        descId = "Pisahkan vokal, drum, bass, dan instrumen lain.", descEn = "Split audio into vocals, drums, bass, and other."
    ),
    StubTool(
        id = "AUD-21", group = ToolGroup.AUDIO, engineLabel = "NAT+FF", plannedPhase = "P8",
        icon = Icons.Filled.GraphicEq, inputSpec = InputSpec.MultipleAudios,
        titleId = "Audio Studio Pro (DAW)", titleEn = "Audio Studio Pro (Multitrack DAW)",
        descId = "Mixer multitrack dengan kontrol gain, pan, dan trim.", descEn = "Multitrack mixer with per-track gain, pan, and trim."
    ),
    StubTool(
        id = "AUD-22", group = ToolGroup.AUDIO, engineLabel = "FF/ML", plannedPhase = "P7",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleAudio,
        titleId = "Audio Enhancer", titleEn = "Audio Enhancer",
        descId = "Tingkatkan kejernihan vokal dan keseimbangan spektrum.", descEn = "Enhance vocal clarity and spectral balance."
    ),
    StubTool(
        id = "AUD-23", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P6",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        titleId = "Pemecah Audio", titleEn = "Audio Splitter",
        descId = "Pecah file audio panjang menjadi beberapa segmen.", descEn = "Split long audio into equal parts."
    ),
    StubTool(
        id = "AUD-25", group = ToolGroup.AUDIO, engineLabel = "FF", plannedPhase = "P7",
        icon = Icons.Filled.GraphicEq, inputSpec = InputSpec.SingleAudio,
        titleId = "Tambah Efek Reverb", titleEn = "Add Reverb",
        descId = "Tambahkan gaung ruangan akustik ke rekaman.", descEn = "Apply room acoustic reverb to audio."
    ),
    StubTool(
        id = "AUD-27", group = ToolGroup.AUDIO, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AudioFile, inputSpec = InputSpec.SingleAudio,
        requiresModel = ModelRegistry.whisperTiny,
        titleId = "Speech to Text", titleEn = "Speech to Text",
        descId = "Transkrip audio ucapan menjadi teks otomatis.", descEn = "Transcribe spoken audio into text automatically."
    )
)

fun stubImageTools(): List<Tool> = listOf(
    StubTool(
        id = "IMG-06", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P9",
        icon = Icons.Filled.Crop, inputSpec = InputSpec.SingleImage,
        titleId = "Koreksi Perspektif", titleEn = "Perspective Corrector",
        descId = "Luruskan dokumen/foto miring 4 titik lewat Matrix.", descEn = "Rectify 4-point perspective distortion via Matrix."
    ),
    StubTool(
        id = "IMG-08", group = ToolGroup.IMAGE, engineLabel = "ML", plannedPhase = "P9",
        icon = Icons.Filled.Crop, inputSpec = InputSpec.SingleImage,
        titleId = "Smart Auto-Crop", titleEn = "Smart Auto-Crop",
        descId = "Deteksi subjek utama dan crop otomatis.", descEn = "Detect primary subject and auto-crop cleanly."
    ),
    StubTool(
        id = "IMG-09", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P9",
        icon = Icons.Filled.Image, inputSpec = InputSpec.MultipleImages,
        titleId = "Penyelaras Gambar", titleEn = "Image Alignment",
        descId = "Selaraskan beberapa foto bertumpuk secara otomatis.", descEn = "Align stacked photos automatically."
    ),
    StubTool(
        id = "IMG-10", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P9",
        icon = Icons.Filled.Crop, inputSpec = InputSpec.SingleImage,
        titleId = "Crop Multi-Layer", titleEn = "Multi-layer Crop",
        descId = "Ekspor beberapa area crop sekaligus ke ZIP.", descEn = "Export multiple crop regions at once into ZIP."
    ),
    StubTool(
        id = "IMG-11", group = ToolGroup.IMAGE, engineLabel = "ML", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        requiresModel = ModelRegistry.realEsrganLite,
        titleId = "AI Image Upscaler", titleEn = "AI Image Upscaler",
        descId = "Perbesar resolusi foto 2x–4x dengan super-resolution.", descEn = "Upscale photo resolution 2x–4x with super-resolution."
    ),
    StubTool(
        id = "IMG-12", group = ToolGroup.IMAGE, engineLabel = "ML", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        requiresModel = ModelRegistry.u2NetSeg,
        titleId = "AI Background Remover", titleEn = "AI Background Remover",
        descId = "Hapus latar belakang foto ke PNG transparan.", descEn = "Remove photo background into transparent PNG."
    ),
    StubTool(
        id = "IMG-13", group = ToolGroup.IMAGE, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        requiresNetwork = true,
        titleId = "AI Object Remover", titleEn = "AI Object Remover",
        descId = "Hapus objek yang ditandai dari foto dengan inpainting.", descEn = "Erase marked objects from photos via inpainting."
    ),
    StubTool(
        id = "IMG-14", group = ToolGroup.IMAGE, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        titleId = "AI Photo Enhancer", titleEn = "AI Photo Enhancer",
        descId = "Tingkatkan detail dan pencahayaan foto secara otomatis.", descEn = "Automatically enhance photo clarity and lighting."
    ),
    StubTool(
        id = "IMG-15", group = ToolGroup.IMAGE, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        requiresNetwork = true,
        titleId = "AI Face Restorer", titleEn = "AI Face Restorer",
        descId = "Pulihkan detail wajah pada foto lama atau buram.", descEn = "Restore facial details on blurry or old photos."
    ),
    StubTool(
        id = "IMG-16", group = ToolGroup.IMAGE, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        titleId = "AI Style Transfer", titleEn = "AI Style Transfer",
        descId = "Ubah gaya visual foto menjadi lukisan artistik.", descEn = "Restyle photos into artistic painting styles."
    ),
    StubTool(
        id = "IMG-17", group = ToolGroup.IMAGE, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        titleId = "AI Image Colorizer", titleEn = "AI Image Colorizer",
        descId = "Warnai foto hitam-putih secara realistis.", descEn = "Colorize black-and-white photos realistically."
    ),
    StubTool(
        id = "IMG-18", group = ToolGroup.IMAGE, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        requiresNetwork = true,
        titleId = "AI Sky Replacement", titleEn = "AI Sky Replacement",
        descId = "Ganti langit foto dengan kondisi cuaca baru.", descEn = "Replace sky regions with realistic weather presets."
    ),
    StubTool(
        id = "IMG-19", group = ToolGroup.IMAGE, engineLabel = "ML", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        requiresModel = ModelRegistry.u2NetSeg,
        titleId = "AI Portrait Bokeh", titleEn = "AI Portrait Bokeh",
        descId = "Tambahkan efek blur latar potret realistis.", descEn = "Add realistic depth-of-field portrait bokeh."
    ),
    StubTool(
        id = "IMG-20", group = ToolGroup.IMAGE, engineLabel = "ML/CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        titleId = "AI Cartoonizer", titleEn = "AI Cartoonizer",
        descId = "Ubah foto menjadi ilustrasi bergaya kartun/anime.", descEn = "Turn photos into cartoon or anime illustrations."
    ),
    StubTool(
        id = "IMG-21", group = ToolGroup.IMAGE, engineLabel = "CL", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.TextInput("Tulis prompt gambar...", "Write image prompt..."),
        requiresNetwork = true,
        titleId = "AI Image Generator", titleEn = "AI Image Generator",
        descId = "Buat gambar dari teks melalui Cloudflare AI Gateway.", descEn = "Generate images from text prompts via AI Gateway."
    ),
    StubTool(
        id = "IMG-22", group = ToolGroup.IMAGE, engineLabel = "ML/LIB", plannedPhase = "P11",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        titleId = "AI Image Denoiser", titleEn = "AI Image Denoiser",
        descId = "Kurangi noise ISO tinggi pada foto malam hari.", descEn = "Reduce high-ISO grain in low-light photos."
    ),
    StubTool(
        id = "IMG-27", group = ToolGroup.IMAGE, engineLabel = "NAT+LIB", plannedPhase = "P9",
        icon = Icons.Filled.Image, inputSpec = InputSpec.SingleImage,
        titleId = "Optimizer Gambar Web", titleEn = "Web Image Optimizer",
        descId = "Buat varian gambar responsif dan WebP/AVIF.", descEn = "Produce responsive WebP/AVIF web image bundles."
    ),
    StubTool(
        id = "IMG-30", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P9",
        icon = Icons.Filled.Image, inputSpec = InputSpec.SingleImage,
        titleId = "Analisis Ukuran File Gambar", titleEn = "Image File Size Analyzer",
        descId = "Bandingkan estimasi ukuran antar format PNG/JPG/WebP.", descEn = "Compare estimated file sizes across formats."
    ),
    StubTool(
        id = "IMG-32", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P10",
        icon = Icons.Filled.Image, inputSpec = InputSpec.MultipleImages,
        titleId = "Overlay / Blender Gambar", titleEn = "Image Overlay / Blender",
        descId = "Gabungkan dua gambar dengan mode blending dan opasitas.", descEn = "Composite two images with blend modes and opacity."
    ),
    StubTool(
        id = "IMG-37", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P10",
        icon = Icons.Filled.Image, inputSpec = InputSpec.SingleImage,
        titleId = "Efek Bayangan & Glow", titleEn = "Shadow & Glow Effects",
        descId = "Tambahkan drop shadow atau outer glow lembut pada objek.", descEn = "Add soft drop shadow or outer glow effects."
    ),
    StubTool(
        id = "IMG-38", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P10",
        icon = Icons.Filled.Image, inputSpec = InputSpec.SingleImage,
        titleId = "Overlay Stiker & Emoji", titleEn = "Sticker / Emoji Overlay",
        descId = "Tempel stiker atau emoji vektor di atas gambar.", descEn = "Place stickers or vector emojis onto images."
    ),
    StubTool(
        id = "IMG-39", group = ToolGroup.IMAGE, engineLabel = "GL", plannedPhase = "P10",
        icon = Icons.Filled.Tune, inputSpec = InputSpec.SingleImage,
        titleId = "Import Filter LUT (.cube)", titleEn = "LUT Color Filter Import",
        descId = "Terapkan file profil warna .cube/.png ke foto.", descEn = "Apply .cube or .png color lookup tables to photos."
    ),
    StubTool(
        id = "IMG-40", group = ToolGroup.IMAGE, engineLabel = "GL/NAT", plannedPhase = "P10",
        icon = Icons.Filled.Tune, inputSpec = InputSpec.SingleImage,
        titleId = "Simulasi Film Vintage", titleEn = "Vintage / Film Simulation",
        descId = "Preset film analog lengkap dengan grain dan vignette.", descEn = "Analog film presets with grain and vignette."
    ),
    StubTool(
        id = "IMG-41", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P10",
        icon = Icons.Filled.AutoFixHigh, inputSpec = InputSpec.SingleImage,
        titleId = "Generator Lens Flare", titleEn = "Lens Flare Generator",
        descId = "Tambahkan kilau cahaya lensa sinematik ke foto.", descEn = "Overlay cinematic optical lens flare effects."
    ),
    StubTool(
        id = "IMG-43", group = ToolGroup.IMAGE, engineLabel = "Job", plannedPhase = "P10",
        icon = Icons.Filled.BurstMode, inputSpec = InputSpec.MultipleImages,
        titleId = "Batch Processing Gambar", titleEn = "Batch Image Processing",
        descId = "Terapkan resize, kompresi, dan watermark ke banyak foto sekaligus.", descEn = "Apply resize, compression, and watermark in batch."
    ),
    StubTool(
        id = "IMG-45", group = ToolGroup.IMAGE, engineLabel = "FF/LIB", plannedPhase = "P10",
        icon = Icons.Filled.BurstMode, inputSpec = InputSpec.MultipleImages,
        titleId = "Pembuat GIF Animasi", titleEn = "Animated GIF Maker",
        descId = "Gabungkan urutan foto menjadi GIF animasi.", descEn = "Combine a sequence of photos into animated GIF."
    ),
    StubTool(
        id = "IMG-46", group = ToolGroup.IMAGE, engineLabel = "NAT", plannedPhase = "P10",
        icon = Icons.Filled.BurstMode, inputSpec = InputSpec.SingleVideo, aliasOfId = "VID-22",
        titleId = "Ekstraktor Frame Video", titleEn = "Video Frame Extractor",
        descId = "Alias VID-22: ambil frame PNG dari file video.", descEn = "Alias VID-22: extract PNG frames from video."
    )
)
