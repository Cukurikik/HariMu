package com.harimu.app.feature.tools

import com.harimu.app.core.toolapi.Tool

/**
 * FASE 11: seluruh tool media yang dulu berupa stub (VID-07, AUD-20, AUD-27, IMG-11..IMG-22)
 * sudah punya implementasi nyata di [aiLocalTools] dan [aiCloudTools]. Fungsi-fungsi ini
 * dipertahankan kosong supaya `ToolRegistry` tidak perlu diubah lagi pada fase berikutnya.
 */
fun stubVideoTools(): List<Tool> = emptyList()

fun stubAudioTools(): List<Tool> = emptyList()

fun stubImageTools(): List<Tool> = emptyList()
