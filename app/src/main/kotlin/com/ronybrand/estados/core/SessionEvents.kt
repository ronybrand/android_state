package com.ronybrand.estados.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * A tiny pub/sub between the HTTP client and the auth controller. The client's 401 handler runs on an OkHttp thread and
 * cannot depend on the controller (the controller depends on the repository, which depends on the client), so it
 * announces here and the controller listens.
 */
class SessionEvents {
    private val flow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val expired: SharedFlow<Unit> get() = flow

    fun notifyExpired() {
        flow.tryEmit(Unit)
    }
}
