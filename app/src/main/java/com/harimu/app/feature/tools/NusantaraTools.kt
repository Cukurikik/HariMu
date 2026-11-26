package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import com.harimu.app.core.toolapi.DedicatedScreenRoute
import com.harimu.app.core.toolapi.Tool
import com.harimu.app.core.toolapi.ToolGroup

/** Tool grup Nusantara: data publik Indonesia tanpa kunci (BMKG, wilayah, referensi, doa). */
fun nusantaraTools(): List<Tool> = listOf(
    DedicatedTool(
        id = "NUS-01",
        group = ToolGroup.NUSANTARA,
        engineLabel = "BMKG",
        icon = Icons.Filled.Warning,
        dedicatedRoute = DedicatedScreenRoute.EARTHQUAKE,
        requiresNetwork = true,
        titleId = "Gempa Terkini",
        titleEn = "Latest Earthquakes",
        descId = "Gempa terbaru dan daftar gempa terkini langsung dari feed resmi BMKG.",
        descEn = "The newest quake and a recent-earthquake list straight from BMKG's official feed."
    ),
    DedicatedTool(
        id = "NUS-02",
        group = ToolGroup.NUSANTARA,
        engineLabel = "BMKG",
        icon = Icons.Filled.Cloud,
        dedicatedRoute = DedicatedScreenRoute.BMKG_WEATHER,
        requiresNetwork = true,
        titleId = "Cuaca BMKG",
        titleEn = "BMKG Weather",
        descId = "Prakiraan cuaca per desa dari BMKG: pilih provinsi sampai desa, lihat 3 hari ke depan.",
        descEn = "Village-level BMKG forecast: pick a province down to a village and read three days ahead."
    ),
    DedicatedTool(
        id = "NUS-03",
        group = ToolGroup.NUSANTARA,
        engineLabel = "STATIC",
        icon = Icons.Filled.Map,
        dedicatedRoute = DedicatedScreenRoute.REGIONS,
        requiresNetwork = true,
        titleId = "Wilayah Indonesia",
        titleEn = "Indonesian Regions",
        descId = "Telusuri provinsi, kabupaten/kota, kecamatan, dan desa beserta kode adm4-nya.",
        descEn = "Browse provinces, cities, districts, and villages with their adm4 codes."
    ),
    DedicatedTool(
        id = "NUS-04",
        group = ToolGroup.NUSANTARA,
        engineLabel = "STATIC",
        icon = Icons.Filled.Star,
        dedicatedRoute = DedicatedScreenRoute.HEROES,
        requiresNetwork = true,
        titleId = "Pahlawan Nasional",
        titleEn = "National Heroes",
        descId = "Daftar pahlawan nasional Indonesia: tahun lahir, wafat, penetapan, dan jasanya.",
        descEn = "Indonesian national heroes with birth, death, declaration years, and their deeds."
    ),
    DedicatedTool(
        id = "NUS-05",
        group = ToolGroup.NUSANTARA,
        engineLabel = "STATIC",
        icon = Icons.Filled.Landscape,
        dedicatedRoute = DedicatedScreenRoute.VOLCANOES,
        requiresNetwork = true,
        titleId = "Gunung Berapi Indonesia",
        titleEn = "Indonesian Volcanoes",
        descId = "Daftar gunung berapi Indonesia: bentuk, tinggi, letusan terakhir, dan koordinat.",
        descEn = "Indonesian volcanoes with type, height, last eruption, and coordinates."
    ),
    DedicatedTool(
        id = "NUS-06",
        group = ToolGroup.NUSANTARA,
        engineLabel = "REST",
        icon = Icons.Filled.MenuBook,
        dedicatedRoute = DedicatedScreenRoute.DUAS,
        requiresNetwork = true,
        titleId = "Doa Harian",
        titleEn = "Daily Duas",
        descId = "Kumpulan doa harian beserta teks Arab, latin, dan artinya; bisa dicari.",
        descEn = "Everyday duas with Arabic text, transliteration, and meaning; searchable."
    ),
    DedicatedTool(
        id = "NUS-07",
        group = ToolGroup.NUSANTARA,
        engineLabel = "SEKOLAH",
        icon = Icons.Filled.School,
        dedicatedRoute = DedicatedScreenRoute.SCHOOLS,
        requiresNetwork = true,
        titleId = "Cari Sekolah",
        titleEn = "Find a School",
        descId = "Cari sekolah se-Indonesia berdasarkan nama: NPSN, jenjang, status, alamat, dan koordinat.",
        descEn = "Search Indonesian schools by name: NPSN, level, status, address, and coordinates."
    ),
    DedicatedTool(
        id = "NUS-08",
        group = ToolGroup.NUSANTARA,
        engineLabel = "SUNDA",
        icon = Icons.Filled.Translate,
        dedicatedRoute = DedicatedScreenRoute.SUNDANESE,
        requiresNetwork = true,
        titleId = "Kamus Basa Sunda",
        titleEn = "Sundanese Dictionary",
        descId = "Arti kata Sunda dalam undak usuk basa: sorangan, batur, loma, Indonesia, dan Inggris.",
        descEn = "Sundanese word meanings across speech levels: sorangan, batur, loma, Indonesian, English."
    ),
    DedicatedTool(
        id = "NUS-09",
        group = ToolGroup.NUSANTARA,
        engineLabel = "TTS",
        icon = Icons.Filled.Extension,
        dedicatedRoute = DedicatedScreenRoute.CROSSWORD,
        requiresNetwork = true,
        titleId = "Bantu TTS",
        titleEn = "Crossword Helper",
        descId = "Cari kemungkinan jawaban TTS dari petunjuknya, lengkap dengan tingkat kecocokan.",
        descEn = "Find possible crossword answers from a clue, ranked by how well they fit."
    ),
    DedicatedTool(
        id = "NUS-10",
        group = ToolGroup.NUSANTARA,
        engineLabel = "QURAN",
        icon = Icons.Filled.MenuBook,
        dedicatedRoute = DedicatedScreenRoute.QURAN,
        requiresNetwork = true,
        titleId = "Al-Qur'an",
        titleEn = "Qur'an Reader",
        descId = "Baca 114 surah: teks Arab, transliterasi, terjemahan Indonesia dan Inggris.",
        descEn = "Read all 114 surahs with Arabic text, transliteration, and Indonesian/English translation."
    ),
    DedicatedTool(
        id = "NUS-11",
        group = ToolGroup.NUSANTARA,
        engineLabel = "BOLA",
        icon = Icons.Filled.EmojiEvents,
        dedicatedRoute = DedicatedScreenRoute.FOOTBALL,
        requiresNetwork = true,
        titleId = "Klasemen Sepak Bola",
        titleEn = "Football Standings",
        descId = "Klasemen liga sepak bola dunia per musim: main, menang, seri, kalah, selisih gol, poin.",
        descEn = "World football league tables by season: played, wins, draws, losses, goal difference, points."
    ),
    DedicatedTool(
        id = "NUS-12",
        group = ToolGroup.NUSANTARA,
        engineLabel = "PESANTREN",
        icon = Icons.Filled.AccountBalance,
        dedicatedRoute = DedicatedScreenRoute.PESANTREN,
        requiresNetwork = true,
        titleId = "Data Pesantren",
        titleEn = "Pesantren Directory",
        descId = "Telusuri pesantren per provinsi dan kabupaten/kota: nama, NSPP, alamat, dan kiai.",
        descEn = "Browse pesantren by province and regency: name, NSPP, address, and cleric."
    )
)
