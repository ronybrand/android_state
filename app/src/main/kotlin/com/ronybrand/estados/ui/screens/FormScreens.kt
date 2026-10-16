package com.ronybrand.estados.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ronybrand.estados.core.attempt
import com.ronybrand.estados.core.correlationId
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
    var saving by remember { mutableStateOf(false) }
    val existing = states.state.collectAsState().value.units.orEmpty().map { it.abbreviation }

    FormPage(title = "New state", error = errors.error, requestId = errors.requestId, onBack = onClose) {
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
                            errors.show("Failed to create state.", it.correlationId)
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
        FormPage(title = "Edit state", error = "Invalid state.", requestId = null, onBack = onClose) {}
        return
    }

    val errors = rememberErrorMessage()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var loaded by remember(id) { mutableStateOf<Loaded>(Loaded.Loading) }
    LaunchedEffect(id) {
        loaded = attempt { states.load(id) }.fold(onSuccess = Loaded::Ready, onFailure = Loaded::Failed)
    }
    val existing = states.state.collectAsState().value.units.orEmpty().map { it.abbreviation }
    val failure = (loaded as? Loaded.Failed)?.error

    FormPage(
        title = "Edit state",
        error = errors.error ?: failure?.let { "Failed to fetch state." },
        requestId = if (errors.error != null) errors.requestId else failure?.correlationId,
        onBack = onClose,
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
                                errors.show("Failed to update state.", it.correlationId)
                            }
                    }
                },
            )
        }
    }
}
