package com.ronybrand.estados.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ValidationTest {
    @Test
    fun `normalizes the abbreviation`() {
        assertEquals("SP", normalizeAbbreviation("  sp "))
    }

    @Test
    fun `an abbreviation needs exactly two letters`() {
        val message = "Enter the state abbreviation with 2 letters."
        assertEquals(message, validateAbbreviation(null))
        assertEquals(message, validateAbbreviation(""))
        assertEquals(message, validateAbbreviation("S"))
        assertEquals(message, validateAbbreviation("SPP"))
        assertNull(validateAbbreviation("sp"))
    }

    @Test
    fun `an abbreviation of two characters must be letters`() {
        val message = "Enter the state abbreviation with 2 uppercase letters."
        assertEquals(message, validateAbbreviation("S1"))
        assertEquals(message, validateAbbreviation("--"))
        assertEquals(message, validateAbbreviation("ÃO"))
    }

    @Test
    fun `an abbreviation already registered is refused`() {
        val message = "A state with this abbreviation already exists."
        assertEquals(message, validateAbbreviation("sp", existing = listOf("RJ", "SP")))
        assertNull(validateAbbreviation("MG", existing = listOf("RJ", "SP")))
    }

    @Test
    fun `the unit being edited may keep its own abbreviation`() {
        assertNull(validateAbbreviation("SP", existing = listOf("RJ", "SP"), current = "SP"))
        assertEquals(
            "A state with this abbreviation already exists.",
            validateAbbreviation("RJ", existing = listOf("RJ", "SP"), current = "SP"),
        )
    }

    @Test
    fun `a name needs between 3 and 100 characters once trimmed`() {
        assertEquals("Enter the state name with at least 3 characters.", validateName(null))
        assertEquals("Enter the state name with at least 3 characters.", validateName("  ab  "))
        assertNull(validateName("Acre"))
        assertNull(validateName("a".repeat(100)))
        assertEquals("Enter the state name with at most 100 characters.", validateName("a".repeat(101)))
    }
}
