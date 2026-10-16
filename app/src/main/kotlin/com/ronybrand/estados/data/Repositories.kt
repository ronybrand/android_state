package com.ronybrand.estados.data

import com.ronybrand.estados.core.ApiClient
import com.ronybrand.estados.core.TokenStore
import com.ronybrand.estados.core.parseTimestampOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime

/** Matches the backend's actual REST contract (/estado), not translated: renaming it here would break real calls. */
private const val BASE_PATH = "/estado"
private const val PAGE_SIZE = 100

interface StateRepository {
    suspend fun list(search: String? = null, sort: String? = null): List<FederativeUnit>

    suspend fun get(id: Int): FederativeUnit

    suspend fun create(unit: NewFederativeUnit): FederativeUnit

    suspend fun update(unit: FederativeUnit): FederativeUnit

    suspend fun delete(id: Int)
}

class ApiStateRepository(private val api: ApiClient) : StateRepository {
    /**
     * size=100 covers the whole dataset (27 units) in one page: the backend only exposes the paginated endpoint, and a
     * closed domain never needs a second page. `busca` and `sort` only enter the query string when provided; their
     * absence means "no filter" and "no explicit ordering".
     */
    override suspend fun list(search: String?, sort: String?): List<FederativeUnit> {
        val query = buildList {
            add("size" to PAGE_SIZE.toString())
            if (!search.isNullOrEmpty()) add("busca" to search)
            if (!sort.isNullOrEmpty()) add("sort" to sort)
        }
        val page = api.send("GET", "$BASE_PATH/paginado", query).asObject("page")
        return page.array("content", "page").map { FederativeUnit.fromJson(it.asObject("federative unit")) }
    }

    override suspend fun get(id: Int): FederativeUnit =
        FederativeUnit.fromJson(api.send("GET", "$BASE_PATH/$id").asObject("federative unit"))

    override suspend fun create(unit: NewFederativeUnit): FederativeUnit =
        FederativeUnit.fromJson(
            api.send("POST", "$BASE_PATH/", body = unit.toRequestJson()).asObject("federative unit"),
        )

    override suspend fun update(unit: FederativeUnit): FederativeUnit = FederativeUnit.fromJson(
        api.send("PUT", "$BASE_PATH/${unit.id}", body = unit.toRequestJson()).asObject("federative unit"),
    )

    override suspend fun delete(id: Int) {
        api.send("DELETE", "$BASE_PATH/$id")
    }
}

data class LoginResult(val token: String, val expiresInSeconds: Int)

interface AuthRepository {
    suspend fun login(username: String, password: String): LoginResult

    fun logout()
}

class ApiAuthRepository(private val api: ApiClient, private val tokens: TokenStore) : AuthRepository {
    /** Stores the token only once the backend has issued it. */
    override suspend fun login(username: String, password: String): LoginResult {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }
        val answer = api.send("POST", "/auth/login", body = body).asObject("login")
        val result = LoginResult(answer.string("token", "login"), answer.int("expiresInSeconds", "login"))
        tokens.save(result.token)
        return result
    }

    override fun logout() {
        tokens.clear()
    }
}

data class BackendInfo(val commit: String, val time: LocalDateTime)

interface InfoRepository {
    suspend fun backendInfo(): BackendInfo
}

class ApiInfoRepository(private val api: ApiClient) : InfoRepository {
    override suspend fun backendInfo(): BackendInfo {
        val build = api.send("GET", "/actuator/info").asObject("info").obj("build", "info")
        val time = parseTimestampOrNull(build.string("time", "build")) ?: malformed("build", "time")
        return BackendInfo(build.string("commit", "build"), time)
    }
}
