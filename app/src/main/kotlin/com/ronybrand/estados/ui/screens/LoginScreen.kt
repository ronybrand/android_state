package com.ronybrand.estados.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ronybrand.estados.auth.AuthController
import com.ronybrand.estados.core.attempt
import com.ronybrand.estados.core.correlationId
import com.ronybrand.estados.core.userMessage
import com.ronybrand.estados.ui.AppColors
import com.ronybrand.estados.ui.components.FormPage
import com.ronybrand.estados.ui.components.rememberErrorMessage
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(auth: AuthController, onBack: () -> Unit, onSignedIn: () -> Unit) {
    val errors = rememberErrorMessage()
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    val canSubmit = username.isNotEmpty() && password.isNotEmpty() && !submitting

    fun submit() {
        if (!canSubmit) return
        submitting = true
        scope.launch {
            attempt { auth.signIn(username, password) }
                .onSuccess { onSignedIn() }
                .onFailure {
                    submitting = false
                    errors.show(it.userMessage("Invalid username or password."), it.correlationId)
                }
        }
    }

    FormPage(title = "Login", error = errors.error, requestId = errors.requestId, onBack = onBack) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().testTag("username"),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().testTag("password"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = ::submit, enabled = canSubmit, modifier = Modifier.testTag("submit-login")) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = AppColors.Muted)
                    } else {
                        Text("Log in")
                    }
                }
            }
            Text(
                "Demo credentials (intentionally public): admin / Estado-Demo-2026",
                fontSize = 13.sp,
                color = AppColors.Muted,
                modifier = Modifier.testTag("demo-credentials"),
            )
        }
    }
}
