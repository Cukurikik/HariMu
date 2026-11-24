package com.harimu.app.core.cloud

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Menyalurkan deep link `harimu://oauth?code=…&state=…` dari MainActivity ke layar Pengaturan.
 * Hanya URI yang sudah divalidasi skema/host-nya yang masuk ke sini.
 */
object OAuthCallbackBus {
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun publish(uri: String) {
        _events.tryEmit(uri)
    }
}
