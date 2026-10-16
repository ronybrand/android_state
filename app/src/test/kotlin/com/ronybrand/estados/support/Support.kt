package com.ronybrand.estados.support

import com.ronybrand.estados.core.ApiClient
import com.ronybrand.estados.core.KeyValueStore
import com.ronybrand.estados.core.SecureTokenStore
import com.ronybrand.estados.core.buildHttpClient
import java.time.Instant
import java.util.Base64
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

/** A keychain that lives in a map, so the token store can be tested without a device. */
class InMemoryKeyValueStore(
    val values: MutableMap<String, String> = mutableMapOf(),
    var failReads: Boolean = false,
    var failWrites: Boolean = false,
    var failDeletes: Boolean = false,
) : KeyValueStore {
    override fun read(key: String): String? {
        check(!failReads) { "keychain unreadable" }
        return values[key]
    }

    override fun write(key: String, value: String) {
        check(!failWrites) { "keychain not writable" }
        values[key] = value
    }

    override fun delete(key: String) {
        check(!failDeletes) { "keychain not writable" }
        values.remove(key)
    }
}

/** A JWT-shaped token (unsigned: the app only reads `exp`) that expires at [expiry]. */
fun jwtExpiring(expiry: Instant): String {
    val payload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"sub":"admin","exp":${expiry.epochSecond}}""".toByteArray())
    return "header.$payload.signature"
}

fun jwtValidFor(seconds: Long = 3600, now: Instant = Instant.now()): String = jwtExpiring(now.plusSeconds(seconds))

fun json(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

/** A client wired like the app's, aimed at [server] under `/api`, with a recording sleeper instead of real waits. */
class TestApi(val server: MockWebServer, val tokens: SecureTokenStore = SecureTokenStore(InMemoryKeyValueStore())) {
    val sleeps = mutableListOf<Long>()
    var expiredCount = 0
    val baseUrl: String = server.url("/api").toString().trimEnd('/')
    val api = ApiClient(
        baseUrl,
        buildHttpClient(tokens, onSessionExpired = { expiredCount++ }, sleep = { sleeps += it }),
    )
}
