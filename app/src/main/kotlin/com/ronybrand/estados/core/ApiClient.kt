package com.ronybrand.estados.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val REQUEST_ID_HEADER = "X-Request-Id"
const val LOGIN_PATH = "/auth/login"
const val MAX_RETRIES = 2
const val RETRY_BASE_DELAY_MS = 500L
private const val TIMEOUT_SECONDS = 15L

/** A request the backend refused (a status) or that never got an answer (no status), with its correlation id. */
class ApiException(val status: Int?, val requestId: String?, message: String, cause: Throwable? = null) :
    IOException(message, cause)

/** The correlation id of a failed request, when the backend echoed one: what a user can quote to support. */
val Throwable.correlationId: String? get() = (this as? ApiException)?.requestId

const val API_UNAVAILABLE_MESSAGE =
    "The API is offline (paused to save cost). Turn it on with the link below; it takes about 1 minute."

/**
 * Page served by the start-api Lambda (backend repo, ADR 0026): anti-bot challenge, starts the EC2 and waits for the
 * API to come back. Opened in the browser, since the challenge widget needs one.
 */
const val START_API_URL = "https://pizowbnhivr3vholalz5r7ajta0mkvqn.lambda-url.us-east-1.on.aws/"

fun startApiUrl(locale: Locale): String {
    val language = if (locale.language == "pt") "pt" else "en"
    return "$START_API_URL?lang=$language"
}

private val GATEWAY_STATUSES = setOf(502, 503, 504)

/**
 * No usable backend answer: no response at all (network, DNS, timeout) or a gateway error. With the EC2 turned
 * off (deploy/aws-power.ps1 in the backend repo) this is exactly what the user sees.
 */
val Throwable.isApiUnavailable: Boolean
    get() = this is ApiException && (status == null || status in GATEWAY_STATUSES)

/** [fallback] for an ordinary failure, or [apiUnavailableMessage] when the API is unavailable. */
fun Throwable.userMessage(
    fallback: String,
    apiUnavailableMessage: String = API_UNAVAILABLE_MESSAGE,
): String = if (isApiUnavailable) apiUnavailableMessage else fallback

/** The backend answered, but not with the shape this client expects. */
class MalformedResponseException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Builds the one HTTP client the app uses.
 *
 * - every action carries an `X-Request-Id`, generated once and kept across retries so all attempts correlate as a
 *   single action in the backend logs;
 * - the bearer token goes on every request, which can only be aimed at the API: [ApiClient] builds each url from the
 *   base url, and a redirect to another host loses the header (OkHttp drops it);
 * - a failing GET is resent (twice, with exponential backoff);
 * - a 401 anywhere ends the session, and [onSessionExpired] is told once.
 */
fun buildHttpClient(
    tokens: TokenStore,
    onSessionExpired: () -> Unit,
    sleep: (Long) -> Unit = Thread::sleep,
): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
    .addInterceptor(ApiInterceptor(tokens, onSessionExpired, sleep))
    .build()

private class ApiInterceptor(
    private val tokens: TokenStore,
    private val onSessionExpired: () -> Unit,
    private val sleep: (Long) -> Unit,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header(REQUEST_ID_HEADER, newUuid())
            .apply { tokens.current?.let { header("Authorization", "Bearer $it") } }
            .build()

        var attempt = 0
        while (true) {
            val outcome = runCatching { chain.proceed(request) }
            val response = outcome.getOrNull()
            if (response?.code == HttpURLConnection.HTTP_UNAUTHORIZED) return endSessionOn401(request, response)
            if (!(isFailure(response) && canRetry(request, chain.call(), attempt))) {
                return response ?: outcome.getOrThrow()
            }
            response?.close()
            attempt++
            sleep(RETRY_BASE_DELAY_MS shl (attempt - 1))
        }
    }

    private fun isFailure(response: Response?) =
        response == null || response.code >= HttpURLConnection.HTTP_INTERNAL_ERROR

    // Only a GET is safe to send again; a write that failed halfway may already have happened.
    private fun canRetry(request: Request, call: Call, attempt: Int) =
        request.method == "GET" && attempt < MAX_RETRIES && !call.isCanceled()

    // The login request itself is excluded: a wrong password also answers 401, and that is a normal form error. Only
    // the first 401 to find a token announces the loss; concurrent ones see no token and do nothing.
    private fun endSessionOn401(request: Request, response: Response): Response {
        if (!request.url.encodedPath.endsWith(LOGIN_PATH) && tokens.clear()) onSessionExpired()
        return response
    }
}

/** The JSON-over-HTTP calls of the Estado API. Responses are parsed to a tree; the repositories map them. */
class ApiClient(private val baseUrl: String, private val http: OkHttpClient) {
    /** Sends [method] to [path] (resolved under the base url); the result is null for an empty body. */
    suspend fun send(
        method: String,
        path: String,
        query: List<Pair<String, String>> = emptyList(),
        body: JsonObject? = null,
    ): JsonElement? {
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply {
            query.forEach { (k, v) -> addQueryParameter(k, v) }
        }
        val payload = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(url.build()).method(method, payload ?: emptyBodyFor(method)).build()
        val response = http.newCall(request).await()
        // Reading the body is socket I/O, which Android forbids on the main thread.
        return withContext(Dispatchers.IO) { response.use { parse(it) } }
    }

    private fun parse(response: Response): JsonElement? {
        val text = response.body.string()
        if (!response.isSuccessful) {
            throw ApiException(response.code, requestIdFromBody(text), "HTTP ${response.code}")
        }
        if (text.isBlank()) return null
        return try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw MalformedResponseException("The response is not json", e)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // OkHttp insists on a body for these verbs; a DELETE or GET carries none.
        fun emptyBodyFor(method: String) = if (method == "POST" || method == "PUT" ||
            method == "PATCH"
        ) {
            "".toRequestBody()
        } else {
            null
        }
    }
}

/**
 * The backend only ever echoes a valid uuid, but the client should not blindly trust that either: a proxy in between
 * could answer something else on a gateway error. Validating the format avoids showing the user something that is not
 * really a correlation id.
 */
internal fun requestIdFromBody(body: String): String? = runCatching {
    val id = ((Json.parseToJsonElement(body) as? JsonObject)?.get("requestId") as? JsonPrimitive)?.contentOrNull?.trim()
    id?.takeIf(::isUuid)
}.getOrNull()

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = continuation.resume(response)

        override fun onFailure(call: Call, e: IOException) =
            continuation.resumeWithException(ApiException(null, null, e.message ?: "Network error", e))
    })
}
