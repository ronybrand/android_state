package com.ronybrand.estados.core

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Decodes the `exp` claim client-side, no round trip. This is a UI hint only (should the login screen show?), not an
 * authorization check: the signature is never verified, and the backend authorizes every real request. Do not reuse
 * this to read claims such as a role for access decisions.
 */
fun tokenExpiry(token: String): Instant? {
    val payload = token.split('.').getOrNull(1) ?: return null
    return runCatching {
        val decoded = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        val exp = (Json.parseToJsonElement(decoded) as? JsonObject)?.get("exp") as? JsonPrimitive
        exp?.takeUnless { it.isString }?.doubleOrNull?.let { Instant.ofEpochSecond(it.toLong()) }
    }.getOrNull()
}

fun isTokenValid(token: String, now: Instant = Instant.now()): Boolean = tokenExpiry(token)?.isAfter(now) == true
