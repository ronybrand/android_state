package com.ronybrand.estados.auth

import com.ronybrand.estados.core.SessionEvents
import com.ronybrand.estados.core.TokenStore
import com.ronybrand.estados.data.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

enum class AuthStatus { Authenticated, Unauthenticated }

/**
 * The session, as the UI sees it. The initial status comes from the token already loaded from the keystore in
 * Application.onCreate, so there is no "loading" state for a guard to misjudge.
 */
class AuthController(
    private val tokens: TokenStore,
    private val repository: AuthRepository,
    events: SessionEvents,
    scope: CoroutineScope,
    now: () -> Instant = Instant::now,
) {
    private val state = MutableStateFlow(statusOf(tokens))
    val status: StateFlow<AuthStatus> = state.asStateFlow()

    private val expiry = ExpiryTimer(tokens, ::endSession, scope, now).also { it.schedule() }

    init {
        // The HTTP client's 401 handler clears the token and announces here, which updates what the navigation reads.
        scope.launch { events.expired.collect { endSession() } }
    }

    suspend fun signIn(username: String, password: String) {
        repository.login(username, password)
        state.value = AuthStatus.Authenticated
        expiry.schedule()
    }

    fun signOut() = endSession()

    private fun endSession() {
        expiry.cancel()
        state.value = AuthStatus.Unauthenticated
        repository.logout()
    }

    private companion object {
        fun statusOf(tokens: TokenStore) =
            if (tokens.current != null) AuthStatus.Authenticated else AuthStatus.Unauthenticated
    }
}
