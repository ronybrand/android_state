package com.ronybrand.estados.auth

import com.ronybrand.estados.core.TokenStore
import com.ronybrand.estados.core.isTokenValid
import com.ronybrand.estados.core.tokenExpiry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Nothing on the device announces that a JWT's `exp` has passed, and the app can stay open (or in the background) well
 * past it: a timer ends the session on time instead of waiting for the next request to fail with a 401.
 */
class ExpiryTimer(
    private val tokens: TokenStore,
    private val onExpired: () -> Unit,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
) {
    private var job: Job? = null

    fun schedule() {
        cancel()
        val expiry = tokens.current?.let(::tokenExpiry) ?: return
        val remaining = Duration.between(now(), expiry).toMillis().coerceAtLeast(0)
        job = scope.launch {
            delay(remaining)
            // The device clock can move while the timer sleeps: only expire if the token really is done.
            val current = tokens.current
            if (current != null && isTokenValid(current, now())) schedule() else onExpired()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
