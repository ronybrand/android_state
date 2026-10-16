package com.ronybrand.estados.core

import java.util.concurrent.atomic.AtomicReference

const val TOKEN_KEY = "estado_jwt"

/** The tiny slice of a keychain this app uses, so tests can stand in for it. */
interface KeyValueStore {
    fun read(key: String): String?

    fun write(key: String, value: String)

    fun delete(key: String)
}

interface TokenStore {
    /** The token, read without waiting (the HTTP interceptor must not block on storage). */
    val current: String?

    /** Called once at app start. */
    fun load(): String?

    fun save(token: String)

    /** Forgets the token; true when there was one, so a caller racing another one can tell who ended the session. */
    fun clear(): Boolean
}

/**
 * The Keystore is the right home for a credential on a phone; the value is mirrored in memory and written through, so
 * the HTTP client always sees the current state at once.
 */
class SecureTokenStore(private val keychain: KeyValueStore) : TokenStore {
    private val memory = AtomicReference<String?>(null)

    override val current: String? get() = memory.get()

    /**
     * An unreadable keychain is treated as "logged out", never as a crash on launch. An expired or malformed token is
     * dropped and deleted.
     */
    override fun load(): String? {
        val stored = runCatching { keychain.read(TOKEN_KEY) }.getOrNull()
        val valid = stored?.takeIf { isTokenValid(it) }
        memory.set(valid)
        if (stored != null && valid == null) clear()
        return valid
    }

    /**
     * The memory copy is updated first, so concurrent requests see the new state at once; a failing keychain write only
     * costs persistence across restarts, not the current session.
     */
    override fun save(token: String) {
        memory.set(token)
        runCatching { keychain.write(TOKEN_KEY, token) }
    }

    override fun clear(): Boolean {
        val hadToken = memory.getAndSet(null) != null
        runCatching { keychain.delete(TOKEN_KEY) }
        return hadToken
    }
}
