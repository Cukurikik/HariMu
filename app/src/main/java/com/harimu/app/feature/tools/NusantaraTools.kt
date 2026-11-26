package com.harimu.app.feature.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
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
    )
)
