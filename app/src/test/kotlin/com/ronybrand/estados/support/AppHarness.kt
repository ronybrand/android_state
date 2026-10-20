package com.ronybrand.estados.support

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ronybrand.estados.AppDependencies
import com.ronybrand.estados.auth.AuthController
import com.ronybrand.estados.core.SecureTokenStore
import com.ronybrand.estados.core.SessionEvents
import com.ronybrand.estados.data.AskRepository
import com.ronybrand.estados.state.StatesStore
import com.ronybrand.estados.ui.EstadosRoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The app wired to in-memory fakes. The scope is unconfined, so the stores react at once and a test only has to move
 * Compose's clock (for the search debounce and the banner timeout), never wait for threads.
 */
class TestDeps(
    val repository: FakeStateRepository = FakeStateRepository(),
    signedIn: Boolean = false,
    val authRepository: FakeAuthRepository = FakeAuthRepository(),
    override val ask: AskRepository = FakeAskRepository(),
    val infoRepository: FakeInfoRepository = FakeInfoRepository(),
    override val appVersion: String = "1.2.3",
) : AppDependencies {
    val tokens = SecureTokenStore(InMemoryKeyValueStore())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    init {
        if (signedIn) tokens.save(jwtValidFor())
        // Like the real repository, a successful login leaves the token in the store.
        authRepository.onLogin = { tokens.save(jwtValidFor()) }
    }

    override val auth = AuthController(tokens, authRepository, SessionEvents(), scope)
    override val states = StatesStore(repository, scope)
    override val info = infoRepository
}

/** Shows the whole app and hands back the navigation controller, so a test can open a screen directly. */
class AppUnderTest(rule: ComposeContentTestRule, val deps: TestDeps = TestDeps()) {
    lateinit var nav: NavHostController
        private set

    init {
        rule.setContent { Root(deps) }
    }

    @Composable
    private fun Root(deps: TestDeps) {
        nav = rememberNavController()
        EstadosRoot(deps, nav)
    }
}
