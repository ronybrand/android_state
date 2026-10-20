package com.ronybrand.estados.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.ronybrand.estados.AppDependencies
import com.ronybrand.estados.auth.AuthStatus
import com.ronybrand.estados.ui.screens.AskAiScreen
import com.ronybrand.estados.ui.screens.CreateStateScreen
import com.ronybrand.estados.ui.screens.EditStateScreen
import com.ronybrand.estados.ui.screens.LoginScreen
import com.ronybrand.estados.ui.screens.StateListScreen

object Routes {
    const val LIST = "list"
    const val ASK_AI = "ask-ai"
    const val LOGIN = "login"
    const val NEW = "state/new"
    const val EDIT = "state/{id}/edit"

    fun edit(id: Int) = "state/$id/edit"

    // Only the mutating screens require a session, matching the backend's "reads are public" rule. The guard is a UX
    // hint: the backend authorizes every request.
    fun isProtected(route: String?) = route == NEW || route == EDIT
}

/**
 * A form screen closes by popping off the stack, so the user lands where they came from. Opened cold (a deep link)
 * there is nothing to pop, so land on the list.
 */
private fun NavHostController.close() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Routes.LIST) { popUpTo(graph.id) { inclusive = true } }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EstadosRoot(deps: AppDependencies, navController: NavHostController = rememberNavController()) {
    val status by deps.auth.status.collectAsStateWithLifecycle()
    val route = navController.currentBackStackEntryAsState().value?.destination?.route

    // The session can end at any moment (an expired token, a 401): a protected screen goes back to the login at once.
    LaunchedEffect(status, route) {
        if (status == AuthStatus.Unauthenticated && Routes.isProtected(route)) {
            navController.navigate(Routes.LOGIN) { popUpTo(Routes.LIST) }
        }
    }

    EstadosTheme {
        // testTagsAsResourceId lets UI Automator and Maestro find the same tags the Compose tests use.
        Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
            NavHost(navController, startDestination = Routes.LIST) {
                composable(Routes.LIST) {
                    StateListScreen(
                        deps,
                        onLogin = { navController.navigate(Routes.LOGIN) },
                        onNew = { navController.navigate(Routes.NEW) },
                        onEdit = { navController.navigate(Routes.edit(it.id)) },
                        onAskAi = { navController.navigate(Routes.ASK_AI) },
                    )
                }
                composable(Routes.ASK_AI) {
                    AskAiScreen(deps.ask, onBack = navController::close)
                }
                composable(Routes.LOGIN) {
                    LoginScreen(
                        deps.auth,
                        onBack = navController::close,
                        onSignedIn = {
                            navController.navigate(Routes.LIST) { popUpTo(Routes.LIST) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.NEW, deepLinks = listOf(navDeepLink { uriPattern = "estados://state/new" })) {
                    CreateStateScreen(deps.states, onClose = navController::close)
                }
                composable(Routes.EDIT, deepLinks = listOf(navDeepLink { uriPattern = "estados://state/{id}/edit" })) {
                    EditStateScreen(deps.states, it.arguments?.getString("id"), onClose = navController::close)
                }
            }
        }
    }
}
