package com.harimu.app.core.recording

import java.io.File

data class RecordingUiState(
    val running: Boolean = false,
    val paused: Boolean = false,
    val elapsedMs: Long = 0L,
    val output: File? = null,
    val error: String? = null,
    val spectrum: List<Float> = emptyList()
)
