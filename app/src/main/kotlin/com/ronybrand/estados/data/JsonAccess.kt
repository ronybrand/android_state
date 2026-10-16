package com.ronybrand.estados.data

import com.ronybrand.estados.core.MalformedResponseException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

// The backend's answers are read defensively: a missing field, a null or a value of the wrong type is a
// MalformedResponseException that says which payload was wrong, never a crash somewhere else later.

internal fun malformed(what: String, detail: String? = null): Nothing =
    throw MalformedResponseException("Unexpected $what payload" + detail?.let { ": $it" }.orEmpty())

internal fun JsonElement?.asObject(what: String): JsonObject = this as? JsonObject ?: malformed(what)

internal fun JsonObject.array(key: String, what: String): JsonArray = this[key] as? JsonArray ?: malformed(what, key)

internal fun JsonObject.obj(key: String, what: String): JsonObject = this[key] as? JsonObject ?: malformed(what, key)

/** A string field, or null when it is absent, null or not a string. */
internal fun JsonObject.stringOrNull(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.string(key: String, what: String): String = stringOrNull(key) ?: malformed(what, key)

internal fun JsonObject.int(key: String, what: String): Int =
    (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: malformed(what, key)

internal fun JsonObject.isNullOrAbsent(key: String): Boolean = this[key].let { it == null || it is JsonNull }
