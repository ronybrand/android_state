package com.ronybrand.estados.data

import com.ronybrand.estados.core.parseTimestampOrNull
import java.time.LocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val WHAT = "federative unit"

/**
 * A Brazilian federative unit (a "state"). Named after the domain term because it avoids a clash with the many things
 * called `State` on Android and in Compose.
 *
 * The backend's JSON uses Portuguese names (`nome`, `sigla`, ...); that is the real wire contract and is not translated.
 * [fromJson] and [toRequestJson] are the anti-corruption layer, so the rest of the app only ever sees English.
 */
data class FederativeUnit(
    val id: Int,
    val name: String,
    val abbreviation: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
) {
    /** The id identifies the resource through the url (PUT /estado/{id}), so it is not part of the body. */
    fun toRequestJson(): JsonObject = requestJson(name, abbreviation)

    companion object {
        fun fromJson(json: JsonObject): FederativeUnit = FederativeUnit(
            id = json.int("id", WHAT),
            name = json.string("nome", WHAT),
            abbreviation = json.string("sigla", WHAT),
            createdAt = json.timestamp("dataHoraCadastro"),
            updatedAt = if (json.isNullOrAbsent("dataHoraUltimaAtualizacao")) null else json.timestamp("dataHoraUltimaAtualizacao"),
        )

        private fun JsonObject.timestamp(key: String): LocalDateTime =
            parseTimestampOrNull(string(key, WHAT)) ?: malformed(WHAT, key)
    }
}

/** What the form produces: validated, trimmed, upper-cased. */
data class NewFederativeUnit(val abbreviation: String, val name: String) {
    fun toRequestJson(): JsonObject = requestJson(name, abbreviation)
}

private fun requestJson(name: String, abbreviation: String) = buildJsonObject {
    put("nome", name)
    put("sigla", abbreviation)
}
