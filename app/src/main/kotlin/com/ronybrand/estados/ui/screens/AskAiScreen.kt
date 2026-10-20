package com.ronybrand.estados.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ronybrand.estados.R
import com.ronybrand.estados.core.attempt
import com.ronybrand.estados.core.correlationId
import com.ronybrand.estados.core.isApiUnavailable
import com.ronybrand.estados.core.userMessage
import com.ronybrand.estados.data.AskRepository
import com.ronybrand.estados.ui.components.ErrorMessageState
import com.ronybrand.estados.ui.components.FormPage
import com.ronybrand.estados.ui.components.MarkdownAnswer
import com.ronybrand.estados.ui.components.rememberErrorMessage
import kotlinx.coroutines.launch

private const val MAX_QUESTION_LENGTH = 1000
private const val AI_AGENT_REPOSITORY_URL = "https://github.com/ronybrand/estado-ai-agent"

@Composable
fun AskAiScreen(repository: AskRepository, onBack: () -> Unit) {
    val errors = rememberErrorMessage()
    FormPage(
        title = stringResource(R.string.ask_ai_title),
        error = errors.error,
        requestId = errors.requestId,
        apiUnavailable = errors.apiUnavailable,
        onBack = onBack,
    ) {
        AskAiContent(repository, errors)
    }
}

@Composable
private fun AskAiContent(repository: AskRepository, errors: ErrorMessageState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.ask_ai_how_it_works))
        Text(stringResource(R.string.ask_ai_language_limit_note))
        AskQuestionForm(repository, errors)
        AiAgentSourceLink(errors)
    }
}

@Composable
private fun AskQuestionForm(repository: AskRepository, errors: ErrorMessageState) {
    val scope = rememberCoroutineScope()
    val language = stringResource(R.string.date_locale_tag).substringBefore('-').lowercase()
    val askError = stringResource(R.string.ask_ai_error)
    val apiUnavailableMessage = stringResource(R.string.api_unavailable_message)
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }

    fun submit() {
        val trimmedQuestion = question.trim()
        if (trimmedQuestion.isEmpty() || submitting) return
        errors.clear()
        answer = null
        submitting = true
        scope.launch {
            attempt { repository.ask(trimmedQuestion, language) }
                .onSuccess { answer = it.answer }
                .onFailure {
                    errors.show(it.userMessage(askError, apiUnavailableMessage), it.correlationId, it.isApiUnavailable)
                }
            submitting = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = question,
            onValueChange = { if (it.length <= MAX_QUESTION_LENGTH) question = it },
            label = { Text(stringResource(R.string.ask_ai_question_label)) },
            placeholder = { Text(stringResource(R.string.ask_ai_placeholder)) },
            minLines = 3,
            maxLines = 5,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth().testTag("ask-ai-question"),
        )
        Button(
            onClick = ::submit,
            enabled = !submitting && question.isNotBlank(),
            modifier = Modifier.testTag("ask-ai-submit"),
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag("ask-ai-loading"),
                    strokeWidth = 2.dp,
                )
                Text(stringResource(R.string.ask_ai_pending_button))
            } else {
                Text(stringResource(R.string.ask_ai_button))
            }
        }
        answer?.let { MarkdownAnswer(it) }
    }
}

@Composable
private fun AiAgentSourceLink(errors: ErrorMessageState) {
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val sourceError = stringResource(R.string.ask_ai_source_error)
    TextButton(
        onClick = {
            scope.launch {
                attempt { uriHandler.openUri(AI_AGENT_REPOSITORY_URL) }
                    .onFailure { errors.show(sourceError) }
            }
        },
        modifier = Modifier.testTag("ask-ai-source"),
    ) {
        Text(stringResource(R.string.ask_ai_source_link))
    }
}
