package com.ronybrand.estados.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiUnavailableTest {
    @Test
    fun `no response at all is unavailable`() {
        assertTrue(ApiException(null, null, "Network error").isApiUnavailable)
    }

    @Test
    fun `gateway statuses are unavailable`() {
        listOf(502, 503, 504).forEach {
            assertEquals(API_UNAVAILABLE_MESSAGE, ApiException(it, null, "HTTP $it").userMessage("Failed."))
        }
    }

    @Test
    fun `ordinary backend failures keep the fallback`() {
        listOf(400, 401, 404, 500).forEach {
            assertEquals("Failed.", ApiException(it, null, "HTTP $it").userMessage("Failed."))
        }
    }

    @Test
    fun `a non-api exception keeps the fallback`() {
        assertFalse(IllegalStateException("boom").isApiUnavailable)
        assertEquals("Failed.", IllegalStateException("boom").userMessage("Failed."))
    }

    @Test
    fun `the start link opens the english page of the start-api lambda`() {
        assertTrue(START_API_URL.startsWith("https://"))
        assertTrue(START_API_URL.endsWith("?lang=en"))
    }
}
