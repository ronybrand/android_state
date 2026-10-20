package com.ronybrand.estados.core

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private val HAS_ZONE = Regex("(Z|[+-][0-9]{2}:?[0-9]{2})$")

/** Formats dates using the user's locale while retaining seconds for the timestamps shown by the backend. */
fun formatDate(date: LocalDateTime?, locale: Locale = Locale.getDefault()): String =
    date?.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale)) ?: "-"

/**
 * The backend writes most timestamps without a zone, and those are shown as written; one with a zone (the build time)
 * is moved to the device's zone.
 */
fun parseTimestamp(text: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    if (HAS_ZONE.containsMatchIn(text)) {
        OffsetDateTime.parse(text).atZoneSameInstant(zone).toLocalDateTime()
    } else {
        LocalDateTime.parse(text)
    }

/** [parseTimestamp], or null when the text is not a timestamp at all. */
fun parseTimestampOrNull(text: String, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime? =
    try {
        parseTimestamp(text, zone)
    } catch (_: DateTimeParseException) {
        null
    }
