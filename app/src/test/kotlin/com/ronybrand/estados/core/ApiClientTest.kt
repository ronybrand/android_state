package com.ronybrand.estados.core

import com.ronybrand.estados.support.TestApi
import com.ronybrand.estados.support.json
import com.ronybrand.estados.support.jwtValidFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApiClientTest {
    private val server = MockWebServer().apply { start() }
    private val test = TestApi(server)
    private val api = test.api

    @AfterTest
    fun tearDown() = server.close()

    private fun send(method: String = "GET", path: String = "/estado/1", body: JsonObject? = null) =
        runBlocking { api.send(method, path, body = body) }

    @Test
    fun `every request carries a request id and the path is resolved under the base url`() {
        server.enqueue(json("{}"))

        send()

        val request = server.takeRequest()
        assertEquals("/api/estado/1", request.url.encodedPath)
        assertTrue(isUuid(request.headers["X-Request-Id"].orEmpty()))
    }

    @Test
    fun `attaches the bearer token once there is one, and none before`() {
        server.enqueue(json("{}"))
        server.enqueue(json("{}"))

        send()
        test.tokens.save(jwtValidFor())
        send()

        assertNull(server.takeRequest().headers["Authorization"])
        assertEquals("Bearer ${test.tokens.current}", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `sends the query parameters and a json body`() {
        server.enqueue(json("{}"))

        runBlocking {
            api.send(
                "POST",
                "/estado/",
                query = listOf("busca" to "são paulo", "size" to "100"),
                body = buildJsonObject { put("nome", "Acre") },
            )
        }

        val request = server.takeRequest()
        assertEquals("/api/estado/", request.url.encodedPath)
        assertEquals("são paulo", request.url.queryParameter("busca"))
        assertEquals("100", request.url.queryParameter("size"))
        assertEquals("""{"nome":"Acre"}""", request.body?.utf8())
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
    }

    @Test
    fun `an empty response is null`() {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertNull(send("DELETE"))
    }

    @Test
    fun `returns the parsed body`() {
        server.enqueue(json("""{"id":1}"""))

        assertEquals(JsonPrimitive(1), send()?.jsonObject?.get("id"))
    }

    @Test
    fun `a failing GET is resent twice with exponential backoff, under the same request id`() {
        server.enqueue(json("{}", code = 503))
        server.enqueue(json("{}", code = 500))
        server.enqueue(json("""{"ok":true}"""))

        val result = send()

        assertEquals(JsonPrimitive(true), result?.jsonObject?.get("ok"))
        assertEquals(listOf(500L, 1000L), test.sleeps)
        val ids = (1..3).map { server.takeRequest().headers["X-Request-Id"] }
        assertEquals(1, ids.toSet().size)
    }

    @Test
    fun `gives up after the retries and reports the last status`() {
        repeat(3) { server.enqueue(json("""{"message":"down"}""", code = 502)) }

        val error = assertFailsWith<ApiException> { send() }

        assertEquals(502, error.status)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(500L, 1000L), test.sleeps)
    }

    @Test
    fun `a dropped connection on a GET is retried`() {
        server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
        server.enqueue(json("""{"ok":true}"""))

        assertNotNull(send())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a connection that keeps dropping ends as an error without a status`() {
        repeat(3) { server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()) }

        val error = assertFailsWith<ApiException> { send() }

        assertNull(error.status)
        assertNull(error.requestId)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `a write is never resent`() {
        server.enqueue(json("{}", code = 500))

        val error = assertFailsWith<ApiException> { send("POST", "/estado/", buildJsonObject { put("nome", "Acre") }) }

        assertEquals(500, error.status)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a client error is not retried`() {
        server.enqueue(json("{}", code = 404))

        assertEquals(404, assertFailsWith<ApiException> { send() }.status)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a 401 with a token ends the session once and is not retried`() {
        test.tokens.save(jwtValidFor())
        server.enqueue(json("{}", code = 401))
        server.enqueue(json("{}", code = 401))

        assertEquals(401, assertFailsWith<ApiException> { send() }.status)
        assertEquals(401, assertFailsWith<ApiException> { send() }.status)

        assertNull(test.tokens.current)
        assertEquals(1, test.expiredCount)
        assertEquals(2, server.requestCount)
        assertTrue(test.sleeps.isEmpty())
    }

    @Test
    fun `a 401 from the login request is a form error, not a lost session`() {
        test.tokens.save(jwtValidFor())
        server.enqueue(json("{}", code = 401))

        assertFailsWith<ApiException> { send("POST", "/auth/login", buildJsonObject { put("username", "x") }) }

        assertNotNull(test.tokens.current)
        assertEquals(0, test.expiredCount)
    }

    @Test
    fun `a 401 without a token announces nothing`() {
        server.enqueue(json("{}", code = 401))

        assertFailsWith<ApiException> { send() }

        assertEquals(0, test.expiredCount)
    }

    @Test
    fun `the error carries the request id the backend echoed, only when it is a uuid`() {
        val id = "123e4567-e89b-42d3-a456-426614174000"
        server.enqueue(json("""{"message":"boom","requestId":"$id"}""", code = 400))
        server.enqueue(json("""{"message":"boom","requestId":"<b>not-a-uuid</b>"}""", code = 400))
        server.enqueue(MockResponse.Builder().code(400).body("<html>bad gateway</html>").build())
        server.enqueue(json("""["array"]""", code = 400))

        assertEquals(id, assertFailsWith<ApiException> { send() }.requestId)
        assertNull(assertFailsWith<ApiException> { send() }.requestId)
        assertNull(assertFailsWith<ApiException> { send() }.requestId)
        assertNull(assertFailsWith<ApiException> { send() }.requestId)
    }

    @Test
    fun `a success that is not json is a malformed response`() {
        server.enqueue(MockResponse.Builder().code(200).body("<html>captive portal</html>").build())

        assertFailsWith<MalformedResponseException> { send() }
    }

    @Test
    fun `cancelling the coroutine cancels the call`() {
        server.enqueue(MockResponse.Builder().code(200).body("{}").headersDelay(5, TimeUnit.SECONDS).build())

        runBlocking {
            val pending = async(Dispatchers.IO) { api.send("POST", "/estado/", body = JsonObject(emptyMap())) }
            server.takeRequest()
            pending.cancel()
            assertFailsWith<CancellationException> { pending.await() }
        }
    }

}
