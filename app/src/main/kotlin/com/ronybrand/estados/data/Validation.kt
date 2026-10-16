package com.ronybrand.estados.data

// The same rules and messages as the web clients' zod schema.

private const val ABBREVIATION_LENGTH = 2
private const val NAME_MIN = 3
private const val NAME_MAX = 100
private val UPPERCASE_LETTERS = Regex("^[A-Z]{2}$")

fun normalizeAbbreviation(raw: String): String = raw.trim().uppercase()

/**
 * Returns the message to show, or null when the value is valid. [existing] are the abbreviations already registered;
 * [current] is the one the unit being edited already owns (it may keep it).
 */
fun validateAbbreviation(raw: String?, existing: List<String> = emptyList(), current: String? = null): String? {
    val value = normalizeAbbreviation(raw.orEmpty())
    return when {
        value.length != ABBREVIATION_LENGTH -> "Enter the state abbreviation with 2 letters."
        !UPPERCASE_LETTERS.matches(value) -> "Enter the state abbreviation with 2 uppercase letters."
        value != current && value in existing -> "A state with this abbreviation already exists."
        else -> null
    }
}

fun validateName(raw: String?): String? {
    val length = raw.orEmpty().trim().length
    return when {
        length < NAME_MIN -> "Enter the state name with at least 3 characters."
        length > NAME_MAX -> "Enter the state name with at most 100 characters."
        else -> null
    }
}
