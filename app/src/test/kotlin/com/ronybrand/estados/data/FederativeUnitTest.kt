package com.ronybrand.estados.data

import com.ronybrand.estados.core.MalformedResponseException
import java.time.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FederativeUnitTest {
    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    @Test
    fun `maps the Portuguese payload of the backend to the English model`() {
        val unit = FederativeUnit.fromJson(
            json(
                """{"id":1,"nome":"São Paulo","sigla":"SP","dataHoraCadastro":"2026-01-10T09:00:00",
                    "dataHoraUltimaAtualizacao":"2026-02-11T10:30:00"}""",
            ),
        )
        assertEquals(1, unit.id)
        assertEquals("São Paulo", unit.name)
        assertEquals("SP", unit.abbreviation)
        assertEquals(LocalDateTime.of(2026, 1, 10, 9, 0, 0), unit.createdAt)
        assertEquals(LocalDateTime.of(2026, 2, 11, 10, 30, 0), unit.updatedAt)
    }

    @Test
    fun `a unit never updated has no update date`() {
        val withNull = FederativeUnit.fromJson(
            json(
                """{"id":2,"nome":"Acre","sigla":"AC","dataHoraCadastro":"2026-01-10T09:00:00",
                    "dataHoraUltimaAtualizacao":null}""",
            ),
        )
        val absent = FederativeUnit.fromJson(
            json("""{"id":2,"nome":"Acre","sigla":"AC","dataHoraCadastro":"2026-01-10T09:00:00"}"""),
        )
        assertNull(withNull.updatedAt)
        assertNull(absent.updatedAt)
    }

    @Test
    fun `an unexpected payload is reported, not half-read`() {
        val payloads = listOf(
            """{}""",
            """{"id":"1","nome":"Acre","sigla":"AC","dataHoraCadastro":"2026-01-10T09:00:00"}""",
            """{"id":1,"nome":"Acre","sigla":"AC","dataHoraCadastro":"yesterday"}""",
            """{"id":1,"nome":null,"sigla":"AC","dataHoraCadastro":"2026-01-10T09:00:00"}""",
            """{"id":1.5,"nome":"Acre","sigla":"AC","dataHoraCadastro":"2026-01-10T09:00:00"}""",
        )
        for (payload in payloads) {
            assertFailsWith<MalformedResponseException>(payload) { FederativeUnit.fromJson(json(payload)) }
        }
    }

    @Test
    fun `the request body carries the name and abbreviation but not the id`() {
        val unit = FederativeUnit(7, "Acre", "AC", LocalDateTime.of(2026, 1, 10, 9, 0, 0), null)
        assertEquals("""{"nome":"Acre","sigla":"AC"}""", unit.toRequestJson().toString())
        assertEquals("""{"nome":"Bahia","sigla":"BA"}""", NewFederativeUnit("BA", "Bahia").toRequestJson().toString())
    }

    @Test
    fun `copy changes only the edited fields`() {
        val unit = FederativeUnit(7, "Acre", "AC", LocalDateTime.of(2026, 1, 10, 9, 0, 0), null)
        val edited = unit.copy(name = "Acre!", abbreviation = "AX")
        assertEquals(7, edited.id)
        assertEquals("Acre!", edited.name)
        assertEquals("AX", edited.abbreviation)
        assertEquals(unit.createdAt, edited.createdAt)
    }
}
