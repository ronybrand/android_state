package com.ronybrand.estados.state

import com.ronybrand.estados.data.NewFederativeUnit
import com.ronybrand.estados.support.FakeStateRepository
import com.ronybrand.estados.support.unit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StatesStoreTest {
    private val repository = FakeStateRepository()

    private fun TestScope.store() = StatesStore(repository, backgroundScope)

    @Test
    fun `loads the whole list on creation, with no search and no ordering`() = runTest {
        val store = store()
        assertTrue(store.state.value.loading)
        assertNull(store.state.value.units)

        runCurrent()

        assertEquals(listOf("SP", "RJ", "MG"), store.state.value.units?.map { it.abbreviation })
        assertTrue(!store.state.value.loading)
        assertNull(store.state.value.error)
        assertEquals(listOf<Pair<String?, String?>>(null to null), repository.listCalls)
    }

    @Test
    fun `a search reloads with the text, and clearing it asks for no filter`() = runTest {
        val store = store()
        runCurrent()

        store.setSearch("minas")
        runCurrent()
        assertEquals(listOf("MG"), store.state.value.units?.map { it.abbreviation })
        store.setSearch("")
        runCurrent()

        assertEquals(listOf<Pair<String?, String?>>(null to null, "minas" to null, null to null), repository.listCalls)
        assertEquals("", store.search.value)
    }

    @Test
    fun `the same search twice does not reload`() = runTest {
        val store = store()
        runCurrent()

        store.setSearch("sp")
        runCurrent()
        store.setSearch("sp")
        runCurrent()

        assertEquals(2, repository.listCalls.size)
    }

    @Test
    fun `pressing the active sort field flips its direction, another field starts ascending`() = runTest {
        val store = store()
        runCurrent()

        store.toggleSort(SortField.Name)
        assertEquals("nome,asc", store.sort.value.wire)
        store.toggleSort(SortField.Name)
        assertEquals("nome,desc", store.sort.value.wire)
        store.toggleSort(SortField.Abbreviation)
        assertEquals("sigla,asc", store.sort.value.wire)
        runCurrent()

        assertEquals("sigla,asc", repository.listCalls.last().second)
        assertEquals(SortState().wire, null)
    }

    @Test
    fun `a sorted list comes back in that order`() = runTest {
        val store = store()
        runCurrent()

        store.toggleSort(SortField.Abbreviation)
        runCurrent()
        assertEquals(listOf("MG", "RJ", "SP"), store.state.value.units?.map { it.abbreviation })

        store.toggleSort(SortField.Abbreviation)
        runCurrent()
        assertEquals(listOf("SP", "RJ", "MG"), store.state.value.units?.map { it.abbreviation })
    }

    @Test
    fun `a failed reload keeps the rows already shown and reports the error`() = runTest {
        val store = store()
        runCurrent()
        val failure = IOException("down")
        repository.failList = failure

        store.setSearch("x")
        runCurrent()

        assertEquals(failure, store.state.value.error)
        assertNotNull(store.state.value.units)
        assertTrue(!store.state.value.loading)

        repository.failList = null
        store.setSearch("sp")
        runCurrent()
        assertNull(store.state.value.error)
    }

    @Test
    fun `a failed first load has no rows and an error`() = runTest {
        repository.failList = IOException("down")
        val store = store()

        runCurrent()

        assertNull(store.state.value.units)
        assertNotNull(store.state.value.error)
        assertTrue(!store.state.value.loading)
    }

    @Test
    fun `an older slow answer never overwrites a newer one`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val store = store()
        runCurrent() // first request is waiting on the gate

        repository.gate = null
        store.setSearch("rio")
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(listOf("RJ"), store.state.value.units?.map { it.abbreviation })
    }

    @Test
    fun `refresh waits for the reload to finish`() = runTest {
        val store = store()
        runCurrent()
        repository.units += unit(4, "Bahia", "BA")
        var done = false

        backgroundScope.launch {
            store.refresh()
            done = true
        }
        runCurrent()

        assertTrue(done)
        assertEquals(4, store.state.value.units?.size)
    }

    @Test
    fun `a write goes to the backend and then the list is fetched again`() = runTest {
        val store = store()
        runCurrent()

        val created = store.create(NewFederativeUnit("BA", "Bahia"))
        runCurrent()
        assertEquals(4, store.state.value.units?.size)

        store.update(created.copy(name = "Bahia!"))
        runCurrent()
        assertTrue(store.state.value.units.orEmpty().any { it.name == "Bahia!" })

        store.delete(created.id)
        runCurrent()
        assertEquals(3, store.state.value.units?.size)
        assertEquals(listOf("create BA", "update ${created.id}", "delete ${created.id}"), repository.calls)
        assertEquals(4, repository.listCalls.size)
    }

    @Test
    fun `a failed write is rethrown and does not reload`() = runTest {
        val store = store()
        runCurrent()
        repository.failWrite = IOException("conflict")

        assertFailsWith<IOException> { store.create(NewFederativeUnit("BA", "Bahia")) }
        assertFailsWith<IOException> { store.update(unit(1, "x", "SP")) }
        assertFailsWith<IOException> { store.delete(1) }
        runCurrent()

        assertEquals(1, repository.listCalls.size)
    }

    @Test
    fun `loads one unit by id`() = runTest {
        val store = store()

        assertEquals("Rio de Janeiro", store.load(2).name)
        assertEquals(listOf("get 2"), repository.calls)
    }
}
