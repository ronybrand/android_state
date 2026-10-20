package com.ronybrand.estados.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronybrand.estados.core.API_UNAVAILABLE_MESSAGE
import com.ronybrand.estados.core.ApiException
import com.ronybrand.estados.support.AppUnderTest
import com.ronybrand.estados.support.FakeInfoRepository
import com.ronybrand.estados.support.FakeStateRepository
import com.ronybrand.estados.support.TestDeps
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ListScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun app(deps: TestDeps = TestDeps()) = AppUnderTest(rule, deps)

    @Test
    fun `shows the states with their dates and the footer`() {
        app()

        rule.onNodeWithText("Brazilian States").assertIsDisplayed()
        rule.onNodeWithText("São Paulo").assertIsDisplayed()
        rule.onNodeWithText("Rio de Janeiro").assertIsDisplayed()
        rule.onNodeWithText("Minas Gerais").assertIsDisplayed()
        rule.onAllNodesWithText("Created: Jan 10, 2026, 9:00:00\u202fAM").assertCountEquals(3)
        rule.onAllNodesWithText("Updated: -").assertCountEquals(3)
        rule.onNodeWithText("App 1.2.3").assertExists()
        rule.onNodeWithText("API abc1234 · Oct 1, 2026, 12:00:00\u202fPM").assertExists()
    }

    @Test
    fun `the footer hides the backend build when it cannot be read`() {
        app(TestDeps(infoRepository = FakeInfoRepository(info = null)))

        rule.onNodeWithText("App 1.2.3").assertExists()
        rule.onNodeWithText("|").assertDoesNotExist()
    }

    @Test
    fun `searching waits for a pause in typing and then narrows the list`() {
        val deps = TestDeps()
        app(deps)
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag("search").performTextInput("minas")
        rule.mainClock.advanceTimeBy(200)
        assertEquals(1, deps.repository.listCalls.size)
        rule.mainClock.advanceTimeBy(150)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()

        assertEquals("minas", deps.repository.listCalls.last().first)
        rule.onNodeWithText("Minas Gerais").assertIsDisplayed()
        rule.onNodeWithText("São Paulo").assertDoesNotExist()
    }

    @Test
    fun `sorting toggles the direction and the chips say which one is active`() {
        val deps = TestDeps()
        app(deps)

        rule.onNodeWithContentDescription("Name").performClick()
        rule.onNodeWithContentDescription("Name, ascending").assertIsSelected()
        rule.onNodeWithContentDescription("Abbreviation").assertExists()
        assertEquals("nome,asc", deps.repository.listCalls.last().second)

        rule.onNodeWithContentDescription("Name, ascending").performClick()
        rule.onNodeWithContentDescription("Name, descending").assertIsSelected()
        assertEquals("nome,desc", deps.repository.listCalls.last().second)
    }

    @Test
    fun `a signed out user sees Log in, and the New state button leads to the login`() {
        app()

        rule.onNodeWithTag("log-in").assertIsDisplayed()
        rule.onNodeWithTag("log-out").assertDoesNotExist()
        rule.onNodeWithTag("new-state").performClick()

        rule.onNodeWithText("Login").assertIsDisplayed()
        rule.onNodeWithTag("demo-credentials").assertTextContains("admin / Estado-Demo-2026", substring = true)
    }

    @Test
    fun `a signed in user can log out`() {
        val deps = TestDeps(signedIn = true)
        app(deps)

        rule.onNodeWithTag("log-in").assertDoesNotExist()
        rule.onNodeWithTag("log-out").performClick()

        rule.onNodeWithTag("log-in").assertIsDisplayed()
        assertEquals(1, deps.authRepository.logouts)
    }

    @Test
    fun `row actions are named after the state they act on`() {
        app()

        rule.onNodeWithContentDescription("Edit SP").assertIsDisplayed()
        rule.onNodeWithContentDescription("Delete RJ").assertIsDisplayed()
    }

    @Test
    fun `deleting asks first, and Cancel keeps the state`() {
        val deps = TestDeps()
        app(deps)

        rule.onNodeWithTag("delete-1").performClick()
        rule.onNodeWithText("Delete state").assertIsDisplayed()
        rule.onNodeWithText("Are you sure you want to delete SP?").assertIsDisplayed()
        rule.onNodeWithText("Cancel").performClick()

        rule.onNodeWithText("São Paulo").assertIsDisplayed()
        assertEquals(emptyList(), deps.repository.calls)
    }

    @Test
    fun `confirming deletes the state and the list reloads without it`() {
        val deps = TestDeps()
        app(deps)

        rule.onNodeWithTag("delete-1").performClick()
        rule.onNodeWithTag("confirm-delete").performClick()

        rule.waitForIdle()
        assertEquals(listOf("delete 1"), deps.repository.calls)
        rule.onNodeWithText("São Paulo").assertDoesNotExist()
        rule.onNodeWithText("Rio de Janeiro").assertIsDisplayed()
    }

    @Test
    fun `a failed delete shows the error with its reference id and keeps the row`() {
        val deps = TestDeps()
        app(deps)
        deps.repository.failWrite = ApiException(500, "123e4567-e89b-42d3-a456-426614174000", "HTTP 500")

        rule.onNodeWithTag("delete-1").performClick()
        rule.onNodeWithTag("confirm-delete").performClick()

        rule.onNodeWithText("Failed to delete state.").assertIsDisplayed()
        rule.onNodeWithText("Reference ID: 123e4567-e89b-42d3-a456-426614174000").assertIsDisplayed()
        rule.onNodeWithText("São Paulo").assertIsDisplayed()
        rule.onNodeWithTag("delete-1").assertIsEnabled()
    }

    @Test
    fun `the error banner clears itself after a few seconds`() {
        val deps = TestDeps()
        app(deps)
        deps.repository.failWrite = IOException("down")
        rule.onNodeWithTag("delete-1").performClick()
        rule.onNodeWithTag("confirm-delete").performClick()
        rule.onNodeWithText("Failed to delete state.").assertIsDisplayed()

        rule.mainClock.advanceTimeBy(5_100)

        rule.onNodeWithText("Failed to delete state.").assertDoesNotExist()
    }

    @Test
    fun `an unreachable api says so and offers to turn it on`() {
        val unreachable = ApiException(null, null, "Network error")
        app(TestDeps(repository = FakeStateRepository().apply { failList = unreachable }))

        rule.onNodeWithText(API_UNAVAILABLE_MESSAGE).assertIsDisplayed()
        rule.onNodeWithTag("turn-on-api").assertIsDisplayed()
    }

    @Test
    fun `a failed first load shows the error and a way to try again, never an empty list`() {
        val deps = TestDeps(repository = FakeStateRepository().apply { failList = IOException("down") })
        app(deps)

        rule.onNodeWithText("Failed to fetch states.").assertIsDisplayed()
        rule.onNodeWithText("No states registered.").assertDoesNotExist()
        deps.repository.failList = null
        rule.onNodeWithTag("retry").performClick()

        rule.waitForIdle()
        rule.onNodeWithText("São Paulo").assertIsDisplayed()
        rule.onNodeWithTag("retry").assertDoesNotExist()
    }

    @Test
    fun `an empty list offers to create the first state`() {
        app(TestDeps(repository = FakeStateRepository(initial = emptyList()), signedIn = true))

        rule.onNodeWithText("No states registered.").assertIsDisplayed()
        rule.onNodeWithText("Create the first state").performClick()

        rule.onNodeWithText("New state").assertExists()
    }

    @Test
    fun `the error banner is a live region, so a screen reader announces it`() {
        val deps = TestDeps()
        app(deps)
        deps.repository.failWrite = IOException("down")
        rule.onNodeWithTag("delete-1").performClick()
        rule.onNodeWithTag("confirm-delete").performClick()

        val config = rule.onNodeWithTag("error-banner").fetchSemanticsNode().config

        assertTrue(SemanticsProperties.LiveRegion in config)
    }
}
