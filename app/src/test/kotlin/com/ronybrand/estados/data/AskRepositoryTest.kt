package com.ronybrand.estados.data

import com.ronybrand.estados.support.TestApi
import com.ronybrand.estados.support.json
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals

class AskRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private val testApi = TestApi(server)
    private val repository = ApiAskRepository(testApi.api)

    @After
    fun tearDown() = server.close()

    @Test
    fun `asks the backend in the selected language and returns the answer`() {
        server.enqueue(json("""{"answer":"27 states."}"""))

        val answer = runBlocking {
            repository.ask("How many states are there?", "en")
        }

        assertEquals("27 states.", answer.answer)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/ask", request.url.encodedPath)
        assertEquals("en", request.url.queryParameter("lang"))
        assertEquals("""{"question":"How many states are there?"}""", request.body?.utf8())
    }

    @Test
    fun `waits longer than the normal API timeout for the AI agent`() {
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Type", "application/json")
                .body("""{"answer":"Resposta"}""")
                .bodyDelay(16, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )

        assertEquals("Resposta", runBlocking { repository.ask("Pergunta", "pt") }.answer)
    }
}
