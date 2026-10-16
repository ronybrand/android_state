package com.ronybrand.estados.state

import com.ronybrand.estados.data.FederativeUnit
import com.ronybrand.estados.data.NewFederativeUnit
import com.ronybrand.estados.data.StateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The wire values are the backend's `sort` contract (sigla, nome); the labels are what the user reads. */
enum class SortField(val wire: String, val label: String) {
    Abbreviation("sigla", "Abbreviation"),
    Name("nome", "Name"),
}

enum class SortDirection(val wire: String) { Ascending("asc"), Descending("desc") }

data class SortState(val active: SortField? = null, val direction: SortDirection = SortDirection.Ascending) {
    /** `nome,asc`, or null when nothing is sorted (the backend then applies no explicit ordering). */
    val wire: String? get() = active?.let { "${it.wire},${direction.wire}" }
}

/**
 * What the list screen shows. The rows stay while a search or sort reloads (`loading` with `units`), so there is no
 * spinner flash per keystroke, and they stay after a failed reload; a failed first load has an [error] and no rows.
 */
data class StatesUiState(
    val units: List<FederativeUnit>? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
)

/**
 * The list, its search and its sort: app-wide state, like the providers of the Flutter app. Changing the search or the
 * sort reloads; every write reloads too, so the list never shows what the backend no longer has.
 */
class StatesStore(private val repository: StateRepository, private val scope: CoroutineScope) {
    private val searchState = MutableStateFlow("")
    private val sortState = MutableStateFlow(SortState())
    private val uiState = MutableStateFlow(StatesUiState())
    private var loadJob: Job? = null

    val search: StateFlow<String> = searchState.asStateFlow()
    val sort: StateFlow<SortState> = sortState.asStateFlow()
    val state: StateFlow<StatesUiState> = uiState.asStateFlow()

    init {
        reload()
    }

    fun setSearch(text: String) {
        if (searchState.value == text) return
        searchState.value = text
        reload()
    }

    /** Pressing the active field flips the direction; another field starts ascending. */
    fun toggleSort(field: SortField) {
        sortState.update { current ->
            when {
                current.active != field -> SortState(field)
                current.direction == SortDirection.Ascending -> SortState(field, SortDirection.Descending)
                else -> SortState(field)
            }
        }
        reload()
    }

    /** Starts a reload, dropping any one still in flight: an older, slower answer must never win. */
    fun reload(): Job {
        loadJob?.cancel()
        uiState.update { it.copy(loading = true, error = null) }
        val search = searchState.value.ifEmpty { null }
        val sort = sortState.value.wire
        return scope.launch {
            try {
                val units = repository.list(search, sort)
                uiState.value = StatesUiState(units = units, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                uiState.update { it.copy(loading = false, error = e) }
            }
        }.also { loadJob = it }
    }

    suspend fun refresh() = reload().join()

    suspend fun load(id: Int): FederativeUnit = repository.get(id)

    suspend fun create(unit: NewFederativeUnit): FederativeUnit = repository.create(unit).also { reload() }

    suspend fun update(unit: FederativeUnit): FederativeUnit = repository.update(unit).also { reload() }

    suspend fun delete(id: Int) {
        repository.delete(id)
        reload()
    }
}
