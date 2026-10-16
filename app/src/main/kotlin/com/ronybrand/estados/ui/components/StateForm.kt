package com.ronybrand.estados.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ronybrand.estados.data.NewFederativeUnit
import com.ronybrand.estados.data.normalizeAbbreviation
import com.ronybrand.estados.data.validateAbbreviation
import com.ronybrand.estados.data.validateName

private const val ABBREVIATION_LENGTH = 2

/**
 * Create/edit form. Errors appear once a field has been left, then follow every keystroke (the "onTouched" behaviour of
 * the web clients). Save stays disabled until the form is valid and changed.
 */
@Composable
fun StateForm(
    onSubmit: (NewFederativeUnit) -> Unit,
    modifier: Modifier = Modifier,
    initialValues: NewFederativeUnit? = null,
    disabled: Boolean = false,
    existingAbbreviations: List<String> = emptyList(),
) {
    val initialAbbreviation = initialValues?.abbreviation.orEmpty()
    val initialName = initialValues?.name.orEmpty()
    var abbreviation by rememberSaveable { mutableStateOf(initialAbbreviation) }
    var name by rememberSaveable { mutableStateOf(initialName) }
    var abbreviationTouched by rememberSaveable { mutableStateOf(false) }
    var nameTouched by rememberSaveable { mutableStateOf(false) }

    val abbreviationError = validateAbbreviation(abbreviation, existingAbbreviations, initialValues?.abbreviation)
    val nameError = validateName(name)
    val dirty = abbreviation != initialAbbreviation || name != initialName
    val canSave = abbreviationError == null && nameError == null && dirty && !disabled

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TouchedTextField(
            value = abbreviation,
            onValueChange = { abbreviation = it.uppercase().take(ABBREVIATION_LENGTH) },
            label = "Abbreviation",
            error = abbreviationError.takeIf { abbreviationTouched },
            onTouched = { abbreviationTouched = true },
            tag = "abbreviation",
            capitalization = KeyboardCapitalization.Characters,
            imeAction = ImeAction.Next,
        )
        TouchedTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            error = nameError.takeIf { nameTouched },
            onTouched = { nameTouched = true },
            tag = "name",
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
            placeholder = "Enter the state name...",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = {
                    onSubmit(NewFederativeUnit(normalizeAbbreviation(abbreviation), name.trim()))
                },
                enabled = canSave,
                modifier = Modifier.testTag("save"),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Save", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** A text field that reports once it has been focused and then left, so an untouched empty field is not an error. */
@Composable
private fun TouchedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    onTouched: () -> Unit,
    tag: String,
    capitalization: KeyboardCapitalization,
    imeAction: ImeAction,
    placeholder: String? = null,
) {
    var hadFocus by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            autoCorrectEnabled = false,
            imeAction = imeAction,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .onFocusChanged {
                if (hadFocus && !it.isFocused) onTouched()
                hadFocus = it.isFocused
            },
    )
}
