package com.ronybrand.estados.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronybrand.estados.AppDependencies
import com.ronybrand.estados.R
import com.ronybrand.estados.auth.AuthStatus
import com.ronybrand.estados.core.attempt
import com.ronybrand.estados.core.correlationId
import com.ronybrand.estados.core.isApiUnavailable
import com.ronybrand.estados.core.userMessage
import com.ronybrand.estados.data.FederativeUnit
import com.ronybrand.estados.state.StatesStore
import com.ronybrand.estados.state.StatesUiState
import com.ronybrand.estados.ui.AppColors
import com.ronybrand.estados.ui.components.AppFooter
import com.ronybrand.estados.ui.components.BrandBarColors
import com.ronybrand.estados.ui.components.ErrorBanner
import com.ronybrand.estados.ui.components.ErrorMessageState
import com.ronybrand.estados.ui.components.LoadingIndicator
import com.ronybrand.estados.ui.components.SortBar
import com.ronybrand.estados.ui.components.StateListItem
import com.ronybrand.estados.ui.components.rememberErrorMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEBOUNCE_MS = 300L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StateListScreen(
    deps: AppDependencies,
    onLogin: () -> Unit,
    onNew: () -> Unit,
    onEdit: (FederativeUnit) -> Unit,
    onAskAi: () -> Unit,
) {
    val states = deps.states
    val state by states.state.collectAsStateWithLifecycle()
    val sort by states.sort.collectAsStateWithLifecycle()
    val authenticated = deps.auth.status.collectAsStateWithLifecycle().value == AuthStatus.Authenticated
    val errors = rememberListErrors(state.error)
    val scope = rememberCoroutineScope()
    val delete = rememberDeleteFlow(states, errors, scope)
    var refreshing by remember { mutableStateOf(false) }

    fun refresh() {
        refreshing = true
        scope.launch {
            states.refresh()
            refreshing = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.states_title)) }, colors = BrandBarColors) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("list"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Header(
                            deps = deps,
                            authenticated = authenticated,
                            errors = errors,
                            onLogin = onLogin,
                            onNew = onNew,
                            onAskAi = onAskAi,
                        )
                    }
                    rows(
                        state = state,
                        deleting = delete.running,
                        onEdit = onEdit,
                        onDelete = { delete.pending = it },
                        onNew = onNew,
                        onRetry = ::refresh,
                    )
                    item { AppFooter(deps.appVersion, deps.info) }
                }
            }
        }
    }

    delete.pending?.let { unit ->
        ConfirmDeleteDialog(unit, onCancel = { delete.pending = null }, onConfirm = delete::confirm)
    }
}

@Composable
private fun rememberListErrors(error: Throwable?): ErrorMessageState {
    val errors = rememberErrorMessage()
    val loadStatesError = stringResource(R.string.load_states_error)
    val apiUnavailableMessage = stringResource(R.string.api_unavailable_message)

    LaunchedEffect(error, loadStatesError, apiUnavailableMessage) {
        error?.let {
            errors.show(
                it.userMessage(loadStatesError, apiUnavailableMessage),
                it.correlationId,
                it.isApiUnavailable,
            )
        }
    }
    return errors
}

@Composable
private fun rememberDeleteFlow(
    states: StatesStore,
    errors: ErrorMessageState,
    scope: CoroutineScope,
): DeleteFlow {
    val deleteStateError = stringResource(R.string.delete_state_error)
    val apiUnavailableMessage = stringResource(R.string.api_unavailable_message)
    return remember(states, errors, scope, deleteStateError, apiUnavailableMessage) {
        DeleteFlow(states, errors, scope, deleteStateError, apiUnavailableMessage)
    }
}

/** The delete a user is about to confirm, and the one in flight (which disables the row buttons). */
@Stable
private class DeleteFlow(
    private val states: StatesStore,
    private val errors: ErrorMessageState,
    private val scope: CoroutineScope,
    private val deleteStateError: String,
    private val apiUnavailableMessage: String,
) {
    var pending by mutableStateOf<FederativeUnit?>(null)
    var running by mutableStateOf(false)
        private set

    fun confirm() {
        val unit = pending ?: return
        pending = null
        running = true
        scope.launch {
            attempt { states.delete(unit.id) }
                .onFailure {
                    errors.show(
                        it.userMessage(deleteStateError, apiUnavailableMessage),
                        it.correlationId,
                        it.isApiUnavailable,
                    )
                }
            running = false
        }
    }
}

// A dialog instead of deleting on tap: the destructive choice is explicit.
@Composable
private fun ConfirmDeleteDialog(unit: FederativeUnit, onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.delete_state_title)) },
        text = { Text(stringResource(R.string.confirm_delete_message, unit.abbreviation)) },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Danger),
                modifier = Modifier.testTag("confirm-delete"),
            ) {
                Text(stringResource(R.string.delete))
            }
        },
    )
}

private fun LazyListScope.rows(
    state: StatesUiState,
    deleting: Boolean,
    onEdit: (FederativeUnit) -> Unit,
    onDelete: (FederativeUnit) -> Unit,
    onNew: () -> Unit,
    onRetry: () -> Unit,
) {
    val units = state.units
    when {
        units == null && state.error != null -> item { LoadFailed(onRetry) }

        units == null -> item { LoadingIndicator() }

        units.isEmpty() -> item { Empty(onNew) }

        else -> items(units, key = { it.id }) { unit ->
            StateListItem(unit, onEdit = onEdit, onDelete = onDelete, deleting = deleting)
        }
    }
}

@Composable
private fun Header(
    deps: AppDependencies,
    authenticated: Boolean,
    errors: ErrorMessageState,
    onLogin: () -> Unit,
    onNew: () -> Unit,
    onAskAi: () -> Unit,
) {
    val states = deps.states
    val sort by states.sort.collectAsStateWithLifecycle()
    // The text is the field's own state; the store only hears of it after the user stopped typing.
    var text by rememberSaveable { mutableStateOf(states.search.value) }
    LaunchedEffect(text) {
        delay(DEBOUNCE_MS)
        states.setSearch(text)
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ErrorBanner(
            errors.error,
            errors.requestId,
            Modifier.padding(bottom = 0.dp),
            errors.apiUnavailable,
        )
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            if (authenticated) {
                OutlinedButton(onClick = deps.auth::signOut, modifier = Modifier.testTag("log-out")) {
                    Text(stringResource(R.string.log_out))
                }
            } else {
                OutlinedButton(onClick = onLogin, modifier = Modifier.testTag("log-in")) {
                    Text(stringResource(R.string.log_in))
                }
            }
            Button(
                onClick = onNew,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success),
                modifier = Modifier.testTag("new-state"),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.new_state), modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = onAskAi, modifier = Modifier.testTag("ask-ai")) {
                Text(stringResource(R.string.ask_ai_button))
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.search_label)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                autoCorrectEnabled = false,
                imeAction = ImeAction.Search,
            ),
            modifier = Modifier.fillMaxWidth().testTag("search"),
        )
        SortBar(sort, onSort = states::toggleSort)
    }
}

// The banner says what went wrong; this is the way out. Without it a failed first load would leave nothing on screen
// but a spinner or a lie ("empty").
@Composable
private fun LoadFailed(onRetry: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("retry")) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.try_again), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Empty(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.no_states), color = AppColors.Muted)
        Button(onClick = onNew, colors = ButtonDefaults.buttonColors(containerColor = AppColors.Success)) {
            Text(stringResource(R.string.create_first_state))
        }
    }
}
