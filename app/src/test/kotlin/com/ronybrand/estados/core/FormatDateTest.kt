package com.ronybrand.estados.core

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatDateTest {
    @Test
    fun `formats dates with the requested locale`() {
        assertEquals(
            "Jan 10, 2026, 9:00:00\u202fAM",
            formatDate(LocalDateTime.of(2026, 1, 10, 9, 0, 0), Locale.US),
        )
        assertEquals(
            "10 de jan. de 2026 09:00:00",
            formatDate(LocalDateTime.of(2026, 1, 10, 9, 0, 0), Locale.forLanguageTag("pt-BR")),
        )
        assertEquals(
            "Dec 31, 2026, 11:05:09\u202fPM",
            formatDate(LocalDateTime.of(2026, 12, 31, 23, 5, 9), Locale.US),
        )
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
