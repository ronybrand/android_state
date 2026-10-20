package com.ronybrand.estados.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ronybrand.estados.R
import com.ronybrand.estados.core.attempt
import com.ronybrand.estados.core.correlationId
import com.ronybrand.estados.core.isApiUnavailable
import com.ronybrand.estados.core.userMessage
import com.ronybrand.estados.data.FederativeUnit
import com.ronybrand.estados.data.NewFederativeUnit
import com.ronybrand.estados.state.StatesStore
import com.ronybrand.estados.ui.components.FormPage
import com.ronybrand.estados.ui.components.LoadingIndicator
import com.ronybrand.estados.ui.components.StateForm
import com.ronybrand.estados.ui.components.rememberErrorMessage
import kotlinx.coroutines.launch

@Composable
fun CreateStateScreen(states: StatesStore, onClose: () -> Unit) {
    val errors = rememberErrorMessage()
    val scope = rememberCoroutineScope()
    val addStateError = stringResource(R.string.add_state_error)
    val apiUnavailableMessage = stringResource(R.string.api_unavailable_message)
    var saving by remember { mutableStateOf(false) }
    val existing = states.state.collectAsStateWithLifecycle().value.units.orEmpty().map { it.abbreviation }

    FormPage(
        title = stringResource(R.string.new_state_title),
        error = errors.error,
        requestId = errors.requestId,
        onBack = onClose,
        apiUnavailable = errors.apiUnavailable,
    ) {
        StateForm(
            disabled = saving,
            existingAbbreviations = existing,
            onSubmit = { unit ->
                saving = true
                scope.launch {
                    attempt { states.create(unit) }
                        .onSuccess { onClose() }
                        .onFailure {
                            saving = false
                            errors.show(
                                it.userMessage(addStateError, apiUnavailableMessage),
                                it.correlationId,
                                it.isApiUnavailable,
                            )
                        }
                }
            },
        )
    }
}

private sealed interface Loaded {
    data object Loading : Loaded

    data class Ready(val unit: FederativeUnit) : Loaded

    data class Failed(val error: Throwable) : Loaded
}

@Composable
fun EditStateScreen(states: StatesStore, rawId: String?, onClose: () -> Unit) {
    // A path segment is user-controllable (deep links): validate before it reaches the API.
    val id = rawId?.toIntOrNull()?.takeIf { it > 0 }
    if (id == null) {
        FormPage(
            title = stringResource(R.string.edit_state_title),
            error = stringResource(R.string.invalid_state),
            requestId = null,
            onBack = onClose,
        ) {}
        return
    }

    val errors = rememberErrorMessage()
    val scope = rememberCoroutineScope()
    val loadStateError = stringResource(R.string.load_state_error)
    val updateStateError = stringResource(R.string.update_state_error)
    val apiUnavailableMessage = stringResource(R.string.api_unavailable_message)
    var saving by remember { mutableStateOf(false) }
    var loaded by remember(id) { mutableStateOf<Loaded>(Loaded.Loading) }
    LaunchedEffect(id) {
        loaded = attempt { states.load(id) }.fold(onSuccess = Loaded::Ready, onFailure = Loaded::Failed)
    }
    val existing = states.state.collectAsStateWithLifecycle().value.units.orEmpty().map { it.abbreviation }
    val failure = (loaded as? Loaded.Failed)?.error

    FormPage(
        title = stringResource(R.string.edit_state_title),
        error = errors.error ?: failure?.userMessage(loadStateError, apiUnavailableMessage),
        requestId = if (errors.error != null) errors.requestId else failure?.correlationId,
        onBack = onClose,
        apiUnavailable = errors.apiUnavailable || failure?.isApiUnavailable == true,
    ) {
        when (val current = loaded) {
            Loaded.Loading -> LoadingIndicator(Modifier)

            is Loaded.Failed -> Unit

            is Loaded.Ready -> StateForm(
                initialValues = NewFederativeUnit(current.unit.abbreviation, current.unit.name),
                disabled = saving,
                existingAbbreviations = existing,
                onSubmit = { values ->
                    saving = true
                    scope.launch {
                        attempt {
                            states.update(current.unit.copy(name = values.name, abbreviation = values.abbreviation))
                        }
                            .onSuccess { onClose() }
                            .onFailure {
                                saving = false
                                errors.show(
                                    it.userMessage(updateStateError, apiUnavailableMessage),
                                    it.correlationId,
                                    it.isApiUnavailable,
                                )
                            }
                    }
                },
            )
        }
    }
}
