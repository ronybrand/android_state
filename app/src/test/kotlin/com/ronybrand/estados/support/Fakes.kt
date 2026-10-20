package com.ronybrand.estados.support

import com.ronybrand.estados.data.AskRepository
import com.ronybrand.estados.data.AskResponse
import com.ronybrand.estados.data.AuthRepository
import com.ronybrand.estados.data.BackendInfo
import com.ronybrand.estados.data.FederativeUnit
import com.ronybrand.estados.data.InfoRepository
import com.ronybrand.estados.data.LoginResult
import com.ronybrand.estados.data.NewFederativeUnit
import com.ronybrand.estados.data.StateRepository
import kotlinx.coroutines.CompletableDeferred
import java.time.LocalDateTime

fun unit(id: Int, name: String, abbreviation: String, updated: LocalDateTime? = null) =
    FederativeUnit(id, name, abbreviation, LocalDateTime.of(2026, 1, 10, 9, 0, 0), updated)

val SEED = listOf(unit(1, "São Paulo", "SP"), unit(2, "Rio de Janeiro", "RJ"), unit(3, "Minas Gerais", "MG"))

/** An in-memory backend: it searches and sorts like the real one closely enough for the screens, and it records calls. */
class FakeStateRepository(initial: List<FederativeUnit> = SEED) : StateRepository {
    val units = initial.toMutableList()
    val listCalls = mutableListOf<Pair<String?, String?>>()
    val calls = mutableListOf<String>()

    /** When set, the next call of each kind fails with it. */
    var failList: Throwable? = null
    var failGet: Throwable? = null
    var failWrite: Throwable? = null

    /** When set, [list] waits for it before answering: a slow request. */
    var gate: CompletableDeferred<Unit>? = null

    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    override suspend fun list(search: String?, sort: String?): List<FederativeUnit> {
        listCalls += search to sort
        gate?.await()
        failList?.let { throw it }
        val matching = units.filter {
            search == null || it.name.contains(search, ignoreCase = true) ||
                it.abbreviation.contains(search, ignoreCase = true)
        }
        val field = sort?.substringBefore(',')
        val sorted = when (field) {
            "sigla" -> matching.sortedBy { it.abbreviation }
            "nome" -> matching.sortedBy { it.name }
            else -> matching
        }
        return if (sort?.endsWith(",desc") == true) sorted.reversed() else sorted
    }

    override suspend fun get(id: Int): FederativeUnit {
        calls += "get $id"
        failGet?.let { throw it }
        return units.first { it.id == id }
    }

    override suspend fun create(unit: NewFederativeUnit): FederativeUnit {
        calls += "create ${unit.abbreviation}"
        failWrite?.let { throw it }
        return unit(nextId++, unit.name, unit.abbreviation).also { units += it }
    }

    override suspend fun update(unit: FederativeUnit): FederativeUnit {
        calls += "update ${unit.id}"
        failWrite?.let { throw it }
        units[units.indexOfFirst { it.id == unit.id }] = unit
        return unit
    }

    override suspend fun delete(id: Int) {
        calls += "delete $id"
        failWrite?.let { throw it }
        units.removeAll { it.id == id }
    }
}

class FakeAuthRepository(private val token: () -> String = { jwtValidFor() }) : AuthRepository {
    var failure: Throwable? = null
    var logouts = 0
    val logins = mutableListOf<Pair<String, String>>()
    var onLogin: () -> Unit = {}

    override suspend fun login(username: String, password: String): LoginResult {
        logins += username to password
        failure?.let { throw it }
        onLogin()
        return LoginResult(token(), 3600)
    }

    override fun logout() {
        logouts++
    }
}

class FakeAskRepository(
    private val answer: String = "27 states.",
    private val pendingAnswer: CompletableDeferred<String>? = null,
    private val failure: Throwable? = null,
) : AskRepository {
    val questions = mutableListOf<Pair<String, String>>()

    override suspend fun ask(question: String, language: String): AskResponse {
        questions += question to language
        failure?.let { throw it }
        return AskResponse(pendingAnswer?.await() ?: answer)
    }
}

class FakeInfoRepository(var info: BackendInfo? = BackendInfo("abc1234", LocalDateTime.of(2026, 10, 1, 12, 0, 0))) :
    InfoRepository {
    override suspend fun backendInfo(): BackendInfo = info ?: error("backend unreachable")
}
