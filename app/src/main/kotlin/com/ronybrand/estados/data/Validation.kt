package com.ronybrand.estados.data

// The same rules and messages as the web clients' zod schema.

private const val ABBREVIATION_LENGTH = 2
private const val NAME_MIN = 3
private const val NAME_MAX = 100
private val UPPERCASE_LETTERS = Regex("^[A-Z]{2}$")

enum class ValidationError {
    AbbreviationLength,
    AbbreviationFormat,
    AbbreviationDuplicate,
    NameMinLength,
    NameMaxLength,
}

fun normalizeAbbreviation(raw: String): String = raw.trim().uppercase()

/**
 * Returns the message to show, or null when the value is valid. [existing] are the abbreviations already registered;
 * [current] is the one the unit being edited already owns (it may keep it).
 */
fun validateAbbreviation(
    raw: String?,
    existing: List<String> = emptyList(),
    current: String? = null,
): ValidationError? {
    val value = normalizeAbbreviation(raw.orEmpty())
    return when {
        value.length != ABBREVIATION_LENGTH -> ValidationError.AbbreviationLength
        !UPPERCASE_LETTERS.matches(value) -> ValidationError.AbbreviationFormat
        value != current && value in existing -> ValidationError.AbbreviationDuplicate
        else -> null
    }
}

fun validateName(raw: String?): ValidationError? {
    val length = raw.orEmpty().trim().length
    return when {
        length < NAME_MIN -> ValidationError.NameMinLength
        length > NAME_MAX -> ValidationError.NameMaxLength
        else -> null
    }
}
