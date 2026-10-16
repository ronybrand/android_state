package com.ronybrand.estados.core

import com.ronybrand.estados.support.InMemoryKeyValueStore
import com.ronybrand.estados.support.jwtExpiring
import com.ronybrand.estados.support.jwtValidFor
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenStoreTest {
    private val keychain = InMemoryKeyValueStore()
    private val store = SecureTokenStore(keychain)

    @Test
    fun `starts empty`() {
        assertNull(store.current)
        assertNull(store.load())
    }

    @Test
    fun `loads a stored token that has not expired`() {
        val token = jwtValidFor()
        keychain.values[TOKEN_KEY] = token

        assertEquals(token, store.load())
        assertEquals(token, store.current)
    }

    @Test
    fun `drops and deletes a stored token that has expired`() {
        keychain.values[TOKEN_KEY] = jwtExpiring(Instant.now().minusSeconds(60))

        assertNull(store.load())
        assertNull(store.current)
        assertFalse(TOKEN_KEY in keychain.values)
    }

    @Test
    fun `drops a stored token that is not a jwt`() {
        keychain.values[TOKEN_KEY] = "garbage"

        assertNull(store.load())
        assertFalse(TOKEN_KEY in keychain.values)
    }

    @Test
    fun `an unreadable keychain means signed out, not a crash`() {
        keychain.failReads = true

        assertNull(store.load())
        assertNull(store.current)
    }

    @Test
    fun `save is visible at once and persisted`() {
        val token = jwtValidFor()
        store.save(token)

        assertEquals(token, store.current)
        assertEquals(token, keychain.values[TOKEN_KEY])
    }

    @Test
    fun `a failing keychain write keeps the session for this run only`() {
        keychain.failWrites = true
        val token = jwtValidFor()

        store.save(token)

        assertEquals(token, store.current)
        assertFalse(TOKEN_KEY in keychain.values)
    }

    @Test
    fun `clear forgets the token and says whether there was one`() {
        store.save(jwtValidFor())

        assertTrue(store.clear())
        assertNull(store.current)
        assertFalse(TOKEN_KEY in keychain.values)
        assertFalse(store.clear())
    }

    @Test
    fun `clear survives a keychain that cannot delete`() {
        store.save(jwtValidFor())
        keychain.failDeletes = true

        assertTrue(store.clear())
        assertNull(store.current)
    }
}
