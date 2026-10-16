package com.ronybrand.estados.core

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// A custom pattern, not a localized one: since JDK 20 the localized en_US pattern puts a narrow no-break space before
// AM/PM, and the web clients print a regular one.
private val FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy, hh:mm:ss a", Locale.US)

private val HAS_ZONE = Regex("(Z|[+-][0-9]{2}:?[0-9]{2})$")

/** The same format as the web apps. */
fun formatDate(date: LocalDateTime?): String = date?.format(FORMATTER) ?: "-"

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
