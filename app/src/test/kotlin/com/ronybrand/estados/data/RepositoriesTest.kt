package com.ronybrand.estados.data

import com.ronybrand.estados.core.ApiException
import com.ronybrand.estados.core.MalformedResponseException
import com.ronybrand.estados.core.TOKEN_KEY
import com.ronybrand.estados.support.InMemoryKeyValueStore
import com.ronybrand.estados.support.TestApi
import com.ronybrand.estados.support.json
import com.ronybrand.estados.support.jwtValidFor
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockWebServer
import java.time.Instant
import java.time.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoriesTest {
    private val server = MockWebServer().apply { start() }
    private val keychain = InMemoryKeyValueStore()
    private val test = TestApi(server, com.ronybrand.estados.core.SecureTokenStore(keychain))
    private val states = ApiStateRepository(test.api)

    @AfterTest
    fun tearDown() = server.close()

    private fun unit(id: Int, name: String, abbreviation: String) =
        """{"id":$id,"nome":"$name","sigla":"$abbreviation","dataHoraCadastro":"2026-01-10T09:00:00",""" +
            """"dataHoraUltimaAtualizacao":null}"""

    @Test
    fun `lists the first page, which holds the whole dataset`() {
        server.enqueue(json("""{"content":[${unit(1, "Acre", "AC")},${unit(2, "Bahia", "BA")}]}"""))

        val list = runBlocking { states.list() }

        assertEquals(listOf("AC", "BA"), list.map { it.abbreviation })
        val url = server.takeRequest().url
        assertEquals("/api/estado/paginado", url.encodedPath)
        assertEquals("100", url.queryParameter("size"))
        assertNull(url.queryParameter("busca"))
        assertNull(url.queryParameter("sort"))
    }

    @Test
    fun `search and sort enter the query only when given`() {
        server.enqueue(json("""{"content":[]}"""))
        server.enqueue(json("""{"content":[]}"""))

        runBlocking {
            states.list(search = "minas", sort = "nome,desc")
            states.list(search = "", sort = "")
        }

        val first = server.takeRequest().url
        assertEquals("minas", first.queryParameter("busca"))
        assertEquals("nome,desc", first.queryParameter("sort"))
        val second = server.takeRequest().url
        assertNull(second.queryParameter("busca"))
        assertNull(second.queryParameter("sort"))
    }

    @Test
    fun `a list without content is a malformed response`() {
        server.enqueue(json("""{"items":[]}"""))
        assertFailsWith<MalformedResponseException> { runBlocking { states.list() } }
    }

    @Test
    fun `a list that is not an object is a malformed response`() {
        server.enqueue(json("""[1]"""))
        assertFailsWith<MalformedResponseException> { runBlocking { states.list() } }
    }

    @Test
    fun `gets one unit by id`() {
        server.enqueue(json(unit(7, "Acre", "AC")))

        val found = runBlocking { states.get(7) }

        assertEquals("Acre", found.name)
        assertEquals("/api/estado/7", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `creates with a POST to the collection, without an id in the body`() {
        server.enqueue(json(unit(9, "Bahia", "BA"), code = 201))

        val created = runBlocking { states.create(NewFederativeUnit("BA", "Bahia")) }

        assertEquals(9, created.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/estado/", request.url.encodedPath)
        assertEquals("""{"nome":"Bahia","sigla":"BA"}""", request.body?.utf8())
    }

    @Test
    fun `updates with a PUT to the unit's own url`() {
        server.enqueue(json(unit(9, "Bahia!", "BA")))
        val existing = FederativeUnit(9, "Bahia!", "BA", LocalDateTime.of(2026, 1, 10, 9, 0, 0), null)

        runBlocking { states.update(existing) }

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/api/estado/9", request.url.encodedPath)
        assertEquals("""{"nome":"Bahia!","sigla":"BA"}""", request.body?.utf8())
    }

    @Test
    fun `deletes with a DELETE`() {
        server.enqueue(json("", code = 204))

        runBlocking { states.delete(9) }

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/estado/9", request.url.encodedPath)
    }

    @Test
    fun `a failed write surfaces as an api error with its status`() {
        server.enqueue(json("""{"message":"duplicate"}""", code = 409))
        assertEquals(409, assertFailsWith<ApiException> { runBlocking { states.create(NewFederativeUnit("AC", "Acre")) } }.status)
    }

    @Test
    fun `login stores the token only once the backend issued it`() {
        val token = jwtValidFor()
        server.enqueue(json("""{"token":"$token","expiresInSeconds":3600}"""))
        val auth = ApiAuthRepository(test.api, test.tokens)

        val result = runBlocking { auth.login("admin", "secret") }

        assertEquals(token, result.token)
        assertEquals(3600, result.expiresInSeconds)
        assertEquals(token, test.tokens.current)
        assertEquals(token, keychain.values[TOKEN_KEY])
        val request = server.takeRequest()
        assertEquals("/api/auth/login", request.url.encodedPath)
        assertEquals("""{"username":"admin","password":"secret"}""", request.body?.utf8())
    }

    @Test
    fun `a refused login stores nothing`() {
        server.enqueue(json("""{"message":"bad credentials"}""", code = 401))
        val auth = ApiAuthRepository(test.api, test.tokens)

        assertEquals(401, assertFailsWith<ApiException> { runBlocking { auth.login("admin", "wrong") } }.status)

        assertNull(test.tokens.current)
        assertTrue(keychain.values.isEmpty())
    }

    @Test
    fun `a login answer without a token is malformed and stores nothing`() {
        server.enqueue(json("""{"expiresInSeconds":3600}"""))
        val auth = ApiAuthRepository(test.api, test.tokens)

        assertFailsWith<MalformedResponseException> { runBlocking { auth.login("admin", "secret") } }
        assertNull(test.tokens.current)
    }

    @Test
    fun `logout forgets the token`() {
        test.tokens.save(jwtValidFor())

        ApiAuthRepository(test.api, test.tokens).logout()

        assertNull(test.tokens.current)
    }

    @Test
    fun `reads the backend build from the actuator`() {
        server.enqueue(json("""{"build":{"commit":"abc1234","time":"2026-10-01T12:00:00.000Z"}}"""))

        val info = runBlocking { ApiInfoRepository(test.api).backendInfo() }

        assertEquals("abc1234", info.commit)
        assertEquals(Instant.parse("2026-10-01T12:00:00Z"), info.time.atZone(java.time.ZoneId.systemDefault()).toInstant())
        assertEquals("/api/actuator/info", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `an actuator answer without build info is malformed`() {
        server.enqueue(json("""{"git":{}}"""))
        assertFailsWith<MalformedResponseException> { runBlocking { ApiInfoRepository(test.api).backendInfo() } }
    }
}
