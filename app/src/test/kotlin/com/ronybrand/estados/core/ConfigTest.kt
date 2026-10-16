package com.ronybrand.estados.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfigTest {
    @Test
    fun `falls back to the public deployment when nothing is configured`() {
        assertEquals(DEFAULT_API_URL, resolveApiBaseUrl(null))
        assertEquals(DEFAULT_API_URL, resolveApiBaseUrl(""))
        assertEquals(DEFAULT_API_URL, resolveApiBaseUrl("   "))
    }

    @Test
    fun `trims the value and drops trailing slashes`() {
        assertEquals("https://api.example.com/v1", resolveApiBaseUrl("  https://api.example.com/v1//  "))
        assertEquals("http://10.0.2.2:8080", resolveApiBaseUrl("http://10.0.2.2:8080/"))
    }

    @Test
    fun `accepts http and https in any case`() {
        assertEquals("HTTP://localhost:4010", resolveApiBaseUrl("HTTP://localhost:4010"))
    }

    @Test
    fun `rejects anything that is not an absolute http url`() {
        val bad =
            listOf("api.example.com", "ftp://host/x", "/relative", "https://", "https:///path", "javascript:alert(1)")
        for (value in bad) {
            assertFailsWith<IllegalArgumentException>(value) { resolveApiBaseUrl(value) }
        }
    }
}
