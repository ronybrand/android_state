package com.ronybrand.estados.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UuidTest {
    @Test
    fun `generates a version 4 uuid in the canonical shape`() {
        val id = newUuid()
        assertTrue(isUuid(id), id)
        assertEquals('4', id[14])
        assertTrue(id[19] in "89ab")
    }

    @Test
    fun `two generated ids differ`() {
        assertNotEquals(newUuid(), newUuid())
    }

    @Test
    fun `accepts upper case and rejects anything that is not a uuid`() {
        assertTrue(isUuid("123E4567-E89B-42D3-A456-426614174000"))
        assertFalse(isUuid(""))
        assertFalse(isUuid("not-a-uuid"))
        assertFalse(isUuid("123e4567-e89b-42d3-a456-42661417400"))
        assertFalse(isUuid("123e4567-e89b-42d3-a456-4266141740000"))
        assertFalse(isUuid("<script>alert(1)</script>"))
    }
}
