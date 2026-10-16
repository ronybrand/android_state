package com.ronybrand.estados.core

import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JwtTest {
    private val now = Instant.parse("2026-10-01T12:00:00Z")

    @Test
    fun `reads the expiry from the payload`() {
        assertEquals(Instant.ofEpochSecond(2_000_000_000), tokenExpiry(jwt("""{"exp":2000000000}""")))
    }

    @Test
    fun `accepts an expiry written as a decimal`() {
        assertEquals(Instant.ofEpochSecond(2_000_000_000), tokenExpiry(jwt("""{"exp":2000000000.0}""")))
    }

    @Test
    fun `has no expiry when the token is malformed`() {
        assertNull(tokenExpiry(""))
        assertNull(tokenExpiry("onlyonepart"))
        assertNull(tokenExpiry("a.%%%.c"))
        assertNull(tokenExpiry(jwt("not json")))
        assertNull(tokenExpiry(jwt("[1,2]")))
        assertNull(tokenExpiry(jwt("""{"sub":"admin"}""")))
        assertNull(tokenExpiry(jwt("""{"exp":"soon"}""")))
    }

    @Test
    fun `a token is valid only before its expiry`() {
        assertTrue(isTokenValid(jwt("""{"exp":${now.epochSecond + 60}}"""), now))
        assertFalse(isTokenValid(jwt("""{"exp":${now.epochSecond - 60}}"""), now))
        assertFalse(isTokenValid(jwt("""{"exp":${now.epochSecond}}"""), now))
        assertFalse(isTokenValid("garbage", now))
    }

    @Test
    fun `reads an unpadded base64url payload`() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1,"a":"x"}""".toByteArray())
        assertEquals(Instant.ofEpochSecond(1), tokenExpiry("h.$payload.s"))
    }

    private fun jwt(payloadJson: String): String {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.toByteArray())
        return "header.$payload.signature"
    }
}
