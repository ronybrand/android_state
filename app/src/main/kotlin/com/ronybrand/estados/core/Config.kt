package com.ronybrand.estados.core

/**
 * Public (testing) deployment of the same API, reached through the web app's /api proxy. Fine for trying the app;
 * build with `-PapiUrl=...` to point it at your own backend for anything real.
 */
const val DEFAULT_API_URL = "https://react-state-flax.vercel.app/api"

private val ABSOLUTE_HTTP_URL = Regex("^https?://[^\\s/]+", RegexOption.IGNORE_CASE)

/**
 * A native app has no origin and no CORS, so unlike the web frontends it talks to the API directly: no BFF proxy. The
 * value is compiled into the binary (BuildConfig), which is why nothing secret may be passed this way.
 */
fun resolveApiBaseUrl(raw: String?): String {
    val configured = raw?.trim().orEmpty()
    if (configured.isEmpty()) return DEFAULT_API_URL
    require(ABSOLUTE_HTTP_URL.containsMatchIn(configured)) {
        "API_URL must be an absolute http(s) url, got \"$configured\""
    }
    return configured.trimEnd('/')
}
