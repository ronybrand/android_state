package com.ronybrand.estados.core

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDateTest {
    @Test
    fun `formats like the web clients, with a regular space before AM and PM`() {
        assertEquals("01/10/2026, 09:00:00 AM", formatDate(LocalDateTime.of(2026, 1, 10, 9, 0, 0)))
        assertEquals("12/31/2026, 11:05:09 PM", formatDate(LocalDateTime.of(2026, 12, 31, 23, 5, 9)))
        assertEquals("03/04/2026, 12:00:00 PM", formatDate(LocalDateTime.of(2026, 3, 4, 12, 0, 0)))
        assertEquals("03/04/2026, 12:00:00 AM", formatDate(LocalDateTime.of(2026, 3, 4, 0, 0, 0)))
    }

    @Test
    fun `a missing date is a dash`() {
        assertEquals("-", formatDate(null))
    }

    @Test
    fun `a timestamp without a zone is shown as written`() {
        assertEquals(LocalDateTime.of(2026, 1, 10, 9, 0, 0), parseTimestamp("2026-01-10T09:00:00"))
        assertEquals(LocalDateTime.of(2026, 1, 10, 9, 0, 0, 123_456_000), parseTimestamp("2026-01-10T09:00:00.123456"))
    }

    @Test
    fun `a timestamp with a zone is moved to the device zone`() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0, 0), parseTimestamp("2026-10-01T12:00:00Z", saoPaulo))
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0, 0), parseTimestamp("2026-10-01T12:00:00.000+00:00", saoPaulo))
    }
}
