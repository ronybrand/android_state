package com.ronybrand.estados

import android.content.Context
import com.ronybrand.estados.auth.AuthController
import com.ronybrand.estados.core.ApiClient
import com.ronybrand.estados.core.KeystoreKeyValueStore
import com.ronybrand.estados.core.SecureTokenStore
import com.ronybrand.estados.core.SessionEvents
import com.ronybrand.estados.core.buildHttpClient
import com.ronybrand.estados.core.resolveApiBaseUrl
import com.ronybrand.estados.data.ApiAskRepository
import com.ronybrand.estados.data.ApiAuthRepository
import com.ronybrand.estados.data.ApiInfoRepository
import com.ronybrand.estados.data.ApiStateRepository
import com.ronybrand.estados.data.InfoRepository
import com.ronybrand.estados.state.StatesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The real wiring, built once when the process starts. */
class AppContainer(context: Context) : AppDependencies {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // The session is read from the keystore before the first frame, so the app starts already knowing whether anyone is
    // signed in and no guard can bounce a signed-in user to the login because it was "not loaded yet".
    private val tokens = SecureTokenStore(KeystoreKeyValueStore(context)).also { it.load() }
    private val events = SessionEvents()
    private val api = ApiClient(resolveApiBaseUrl(BuildConfig.API_URL), buildHttpClient(tokens, events::notifyExpired))

    override val auth = AuthController(tokens, ApiAuthRepository(api, tokens), events, scope)
    override val ask = ApiAskRepository(api)
    override val states = StatesStore(ApiStateRepository(api), scope)
    override val info: InfoRepository = ApiInfoRepository(api)
    override val appVersion: String = BuildConfig.VERSION_NAME
}
