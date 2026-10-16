package com.ronybrand.estados.auth

import com.ronybrand.estados.core.SecureTokenStore
import com.ronybrand.estados.core.SessionEvents
import com.ronybrand.estados.support.FakeAuthRepository
import com.ronybrand.estados.support.InMemoryKeyValueStore
import com.ronybrand.estados.support.jwtExpiring
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class AuthTest {
    private val start = Instant.parse("2026-10-01T12:00:00Z")
    private val tokens = SecureTokenStore(InMemoryKeyValueStore())
    private val events = SessionEvents()
    private val repository = FakeAuthRepository(token = { jwtExpiring(start.plusSeconds(3600)) })

    private fun TestScope.clock(): () -> Instant = { start.plusMillis(testScheduler.currentTime) }

    private fun TestScope.controller() = AuthController(tokens, repository, events, backgroundScope, clock())

    @Test
    fun `starts signed out without a token`() = runTest {
        assertEquals(AuthStatus.Unauthenticated, controller().status.value)
    }

    @Test
    fun `starts signed in when a token was already loaded`() = runTest {
        tokens.save(jwtExpiring(start.plusSeconds(60)))

        assertEquals(AuthStatus.Authenticated, controller().status.value)
    }

    @Test
    fun `signing in signs in and arms the expiry`() = runTest {
        val auth = controller()
        // The repository stores the token like the real one does.
        repository.onLogin = { tokens.save(jwtExpiring(start.plusSeconds(3600))) }

        auth.signIn("admin", "secret")

        assertEquals(AuthStatus.Authenticated, auth.status.value)
        assertEquals(listOf("admin" to "secret"), repository.logins)
        advanceTimeBy(3_599_000)
        assertEquals(AuthStatus.Authenticated, auth.status.value)
        advanceTimeBy(2_000)
        assertEquals(AuthStatus.Unauthenticated, auth.status.value)
        assertEquals(1, repository.logouts)
    }

    @Test
    fun `a refused login leaves the user signed out and rethrows`() = runTest {
        val auth = controller()
        repository.failure = IllegalStateException("401")

        assertFailsWith<IllegalStateException> { auth.signIn("admin", "wrong") }

        assertEquals(AuthStatus.Unauthenticated, auth.status.value)
    }

    @Test
    fun `signing out forgets the session and stops the timer`() = runTest {
        tokens.save(jwtExpiring(start.plusSeconds(60)))
        val auth = controller()

        auth.signOut()
        advanceTimeBy(120_000)

        assertEquals(AuthStatus.Unauthenticated, auth.status.value)
        assertEquals(1, repository.logouts)
    }

    @Test
    fun `a 401 announced by the http client ends the session`() = runTest {
        tokens.save(jwtExpiring(start.plusSeconds(3600)))
        val auth = controller()
        runCurrent()

        events.notifyExpired()
        runCurrent()

        assertEquals(AuthStatus.Unauthenticated, auth.status.value)
        assertEquals(1, repository.logouts)
    }

    @Test
    fun `the timer ends the session on time without waiting for a request`() = runTest {
        tokens.save(jwtExpiring(start.plusSeconds(60)))
        val auth = controller()

        advanceTimeBy(59_000)
        assertEquals(AuthStatus.Authenticated, auth.status.value)
        advanceTimeBy(2_000)
        assertEquals(AuthStatus.Unauthenticated, auth.status.value)
    }

    @Test
    fun `a token that is already expired ends the session at once`() = runTest {
        tokens.save(jwtExpiring(start.minusSeconds(5)))
        val fired = mutableListOf<Unit>()
        ExpiryTimer(tokens, { fired += Unit }, backgroundScope, clock()).schedule()

        runCurrent()

        assertEquals(1, fired.size)
    }

    @Test
    fun `no token or an unreadable one schedules nothing`() = runTest {
        val fired = mutableListOf<Unit>()
        val timer = ExpiryTimer(tokens, { fired += Unit }, backgroundScope, clock())

        timer.schedule()
        tokens.save("garbage")
        timer.schedule()
        advanceTimeBy(10_000_000)

        assertEquals(0, fired.size)
    }

    @Test
    fun `scheduling again replaces the previous timer, and cancel stops it`() = runTest {
        val fired = mutableListOf<Instant>()
        val clock = clock()
        val timer = ExpiryTimer(tokens, { fired += clock() }, backgroundScope, clock)
        tokens.save(jwtExpiring(start.plusSeconds(10)))
        timer.schedule()
        tokens.save(jwtExpiring(start.plusSeconds(100)))
        timer.schedule()

        advanceTimeBy(50_000)
        assertEquals(0, fired.size)
        advanceTimeBy(60_000)
        assertEquals(1, fired.size)

        tokens.save(jwtExpiring(start.plusSeconds(500)))
        timer.schedule()
        timer.cancel()
        advanceTimeBy(1_000_000)
        assertEquals(1, fired.size)
    }

    @Test
    fun `a timer that fires early because the device clock moved back re-arms instead of expiring`() = runTest {
        var skew = 0L
        val fired = mutableListOf<Unit>()
        val clock: () -> Instant = { start.plusMillis(testScheduler.currentTime).plusSeconds(skew) }
        tokens.save(jwtExpiring(start.plusSeconds(60)))
        val timer = ExpiryTimer(tokens, { fired += Unit }, backgroundScope, clock)
        timer.schedule()

        skew = -120 // the clock is set back after the timer was armed: the token has 180 s left
        advanceTimeBy(61_000)
        assertEquals(0, fired.size)

        skew = 0
        advanceTimeBy(130_000)
        assertEquals(1, fired.size)
    }
}
