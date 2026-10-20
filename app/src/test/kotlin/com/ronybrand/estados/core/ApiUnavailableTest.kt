package com.ronybrand.estados.core

import java.util.Locale
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
    fun `an unavailable API uses the localized message supplied by the UI`() {
        assertEquals(
            "A API está indisponível.",
            ApiException(503, null, "HTTP 503").userMessage("Falha.", "A API está indisponível."),
        )
    }

    @Test
    fun `a non-api exception keeps the fallback`() {
        assertFalse(IllegalStateException("boom").isApiUnavailable)
        assertEquals("Failed.", IllegalStateException("boom").userMessage("Failed."))
    }

    @Test
    fun `the start link follows the app locale`() {
        assertTrue(START_API_URL.startsWith("https://"))
        assertTrue(START_API_URL.endsWith("/"))
        assertEquals("${START_API_URL}?lang=en", startApiUrl(Locale.US))
        assertEquals("${START_API_URL}?lang=pt", startApiUrl(Locale.forLanguageTag("pt-BR")))
    }
}
