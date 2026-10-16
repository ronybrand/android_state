package com.ronybrand.estados.core

import java.util.UUID

private val UUID_PATTERN =
    Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

/** A random (version 4) uuid from a secure generator: all a correlation id needs. */
fun newUuid(): String = UUID.randomUUID().toString()

fun isUuid(value: String): Boolean = UUID_PATTERN.matches(value)
