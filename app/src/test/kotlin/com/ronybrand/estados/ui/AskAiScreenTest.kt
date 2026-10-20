package com.ronybrand.estados.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronybrand.estados.core.ApiException
import com.ronybrand.estados.support.AppUnderTest
import com.ronybrand.estados.support.FakeAskRepository
import com.ronybrand.estados.support.TestDeps
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AskAiScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `can open the assistant from the public state list`() {
        val app = AppUnderTest(rule)

        rule.onNodeWithTag("ask-ai").performClick()

        rule.onNodeWithText("Ask about the Brazilian states").assertIsDisplayed()
        assertEquals(Routes.ASK_AI, app.nav.currentDestination?.route)
    }

    @Test
    fun `submits a trimmed question in the current language and displays the answer`() {
        val repository = FakeAskRepository(answer = "# Answer\n\n**27 states.**")
        val app = AppUnderTest(rule, TestDeps(ask = repository))
        app.nav.navigate(Routes.ASK_AI)

        rule.onNodeWithTag("ask-ai-question").performTextInput("  How many states?  ")
        rule.onNodeWithTag("ask-ai-submit").assertIsEnabled().performClick()

        rule.onNodeWithText("27 states.").assertIsDisplayed()
        assertEquals(listOf("How many states?" to "en"), repository.questions)
    }

    @Test
    fun `disables the form while the AI response is pending`() {
        val pending = CompletableDeferred<String>()
        val repository = FakeAskRepository(pendingAnswer = pending)
        val app = AppUnderTest(rule, TestDeps(ask = repository))
        app.nav.navigate(Routes.ASK_AI)
        rule.onNodeWithTag("ask-ai-question").performTextInput("Question")
        rule.onNodeWithTag("ask-ai-submit").performClick()

        rule.onNodeWithTag("ask-ai-question").assertIsNotEnabled()
        rule.onNodeWithTag("ask-ai-submit").assertIsNotEnabled()

        pending.complete("Done")
        rule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun `shows the backend error and request id when a question fails`() {
        val repository = FakeAskRepository(
            failure = ApiException(400, "123e4567-e89b-42d3-a456-426614174000", "HTTP 400"),
        )
        val app = AppUnderTest(rule, TestDeps(ask = repository))
        app.nav.navigate(Routes.ASK_AI)
        rule.onNodeWithTag("ask-ai-question").performTextInput("Question")
        rule.onNodeWithTag("ask-ai-submit").performClick()

        rule.onNodeWithText("Failed to reach the AI assistant.").assertIsDisplayed()
        rule.onNodeWithText("Reference ID: 123e4567-e89b-42d3-a456-426614174000").assertIsDisplayed()
        rule.onNodeWithTag("ask-ai-answer").assertDoesNotExist()
    }

    @Test
    fun `reports the AI backend as unavailable when the API is offline`() {
        val repository = FakeAskRepository(failure = ApiException(null, null, "offline"))
        val app = AppUnderTest(rule, TestDeps(ask = repository))
        app.nav.navigate(Routes.ASK_AI)
        rule.onNodeWithTag("ask-ai-question").performTextInput("Question")
        rule.onNodeWithTag("ask-ai-submit").performClick()

        rule.onNodeWithText(
            "The API is offline (paused to save cost). Turn it on with the link below; it takes about 1 minute.",
        ).assertIsDisplayed()
    }
}
