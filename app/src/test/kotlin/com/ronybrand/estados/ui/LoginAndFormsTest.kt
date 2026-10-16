package com.ronybrand.estados.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronybrand.estados.core.ApiException
import com.ronybrand.estados.support.AppUnderTest
import com.ronybrand.estados.support.TestDeps
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LoginAndFormsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun app(deps: TestDeps = TestDeps()) = AppUnderTest(rule, deps)

    private fun AppUnderTest.open(route: String) {
        rule.runOnUiThread { nav.navigate(route) }
        rule.waitForIdle()
    }

    private fun fillLogin(user: String = "admin", password: String = "secret") {
        rule.onNodeWithTag("username").performTextInput(user)
        rule.onNodeWithTag("password").performTextInput(password)
    }

    // --- login ---

    @Test
    fun `Log in stays disabled until both fields are filled`() {
        app().open(Routes.LOGIN)

        rule.onNodeWithTag("submit-login").assertIsNotEnabled()
        rule.onNodeWithTag("username").performTextInput("admin")
        rule.onNodeWithTag("submit-login").assertIsNotEnabled()
        rule.onNodeWithTag("password").performTextInput("secret")
        rule.onNodeWithTag("submit-login").assertIsEnabled()
    }

    @Test
    fun `a good login returns to the list, signed in`() {
        val deps = TestDeps()
        app(deps).open(Routes.LOGIN)
        fillLogin()

        rule.onNodeWithTag("submit-login").performClick()
        rule.waitForIdle()

        assertEquals(listOf("admin" to "secret"), deps.authRepository.logins)
        rule.onNodeWithText("Brazilian States").assertIsDisplayed()
        rule.onNodeWithTag("log-out").assertIsDisplayed()
    }

    @Test
    fun `a refused login says so, with the reference id, and lets the user try again`() {
        val deps = TestDeps()
        deps.authRepository.failure = ApiException(401, "123e4567-e89b-42d3-a456-426614174000", "HTTP 401")
        app(deps).open(Routes.LOGIN)
        fillLogin(password = "wrong")

        rule.onNodeWithTag("submit-login").performClick()

        rule.onNodeWithText("Invalid username or password.").assertIsDisplayed()
        rule.onNodeWithText("Reference ID: 123e4567-e89b-42d3-a456-426614174000").assertIsDisplayed()
        rule.onNodeWithTag("submit-login").assertIsEnabled()
    }

    @Test
    fun `a network failure at login is reported the same way, without a reference id`() {
        val deps = TestDeps()
        deps.authRepository.failure = IOException("offline")
        app(deps).open(Routes.LOGIN)
        fillLogin()

        rule.onNodeWithTag("submit-login").performClick()

        rule.onNodeWithText("Invalid username or password.").assertIsDisplayed()
        rule.onNodeWithText("Reference ID", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the back arrow leaves the login`() {
        app().open(Routes.LOGIN)

        rule.onNodeWithTag("back").performClick()

        rule.onNodeWithText("Brazilian States").assertIsDisplayed()
    }

    // --- the guard ---

    @Test
    fun `a protected screen asks a signed out user to log in`() {
        app().open(Routes.NEW)

        rule.onNodeWithText("Login").assertIsDisplayed()
    }

    @Test
    fun `a session that ends while on a protected screen sends the user to the login`() {
        val deps = TestDeps(signedIn = true)
        app(deps).open(Routes.NEW)
        rule.onNodeWithText("New state").assertIsDisplayed()

        deps.auth.signOut()
        rule.waitForIdle()

        rule.onNodeWithText("Login").assertIsDisplayed()
    }

    // --- create ---

    @Test
    fun `the create form shows a field's error only once the user has left it`() {
        app(TestDeps(signedIn = true)).open(Routes.NEW)

        rule.onNodeWithText("Enter the state abbreviation with 2 letters.").assertDoesNotExist()
        rule.onNodeWithTag("abbreviation").performClick()
        rule.onNodeWithTag("abbreviation").performTextInput("a")
        rule.onNodeWithText("Enter the state abbreviation with 2 letters.").assertDoesNotExist()
        rule.onNodeWithTag("name").performClick()

        rule.onNodeWithText("Enter the state abbreviation with 2 letters.").assertIsDisplayed()
    }

    @Test
    fun `the abbreviation is upper-cased and limited to two characters as typed`() {
        app(TestDeps(signedIn = true)).open(Routes.NEW)

        rule.onNodeWithTag("abbreviation").performTextInput("bahia")

        rule.onNodeWithTag("abbreviation").assertTextContains("BA")
    }

    @Test
    fun `Save needs a valid, changed form`() {
        app(TestDeps(signedIn = true)).open(Routes.NEW)

        rule.onNodeWithTag("save").assertIsNotEnabled()
        rule.onNodeWithTag("abbreviation").performTextInput("ba")
        rule.onNodeWithTag("save").assertIsNotEnabled()
        rule.onNodeWithTag("name").performTextInput("Ba")
        rule.onNodeWithTag("save").assertIsNotEnabled()
        rule.onNodeWithTag("name").performTextInput("hia")
        rule.onNodeWithTag("save").assertIsEnabled()
    }

    @Test
    fun `an abbreviation that already exists is refused`() {
        app(TestDeps(signedIn = true)).open(Routes.NEW)

        rule.onNodeWithTag("abbreviation").performTextInput("sp")
        rule.onNodeWithTag("name").performClick()
        rule.onNodeWithTag("name").performTextInput("Another")

        rule.onNodeWithText("A state with this abbreviation already exists.").assertIsDisplayed()
        rule.onNodeWithTag("save").assertIsNotEnabled()
    }

    @Test
    fun `saving creates the state and returns to the list that now shows it`() {
        val deps = TestDeps(signedIn = true)
        val app = app(deps)
        rule.onNodeWithTag("new-state").performClick()
        rule.onNodeWithTag("abbreviation").performTextInput("ba")
        rule.onNodeWithTag("name").performTextInput("  Bahia ")

        rule.onNodeWithTag("save").performClick()
        rule.waitForIdle()

        assertEquals(listOf("create BA"), deps.repository.calls)
        assertEquals("Bahia", deps.repository.units.last().name)
        rule.onNodeWithText("Brazilian States").assertIsDisplayed()
        rule.onNodeWithText("Bahia").assertIsDisplayed()
        assertEquals(Routes.LIST, app.nav.currentDestination?.route)
    }

    @Test
    fun `a failed create stays on the form with the error and the reference id`() {
        val deps = TestDeps(signedIn = true)
        app(deps).open(Routes.NEW)
        deps.repository.failWrite = ApiException(409, "123e4567-e89b-42d3-a456-426614174000", "HTTP 409")
        rule.onNodeWithTag("abbreviation").performTextInput("ba")
        rule.onNodeWithTag("name").performTextInput("Bahia")

        rule.onNodeWithTag("save").performClick()

        rule.onNodeWithText("Failed to create state.").assertIsDisplayed()
        rule.onNodeWithText("Reference ID: 123e4567-e89b-42d3-a456-426614174000").assertIsDisplayed()
        rule.onNodeWithTag("save").assertIsEnabled()
    }

    @Test
    fun `a form opened cold, with nothing to go back to, closes onto the list`() {
        app(TestDeps(signedIn = true)).open(Routes.NEW)
        rule.onNodeWithTag("back").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Brazilian States").assertIsDisplayed()
    }

    // --- edit ---

    @Test
    fun `the edit form loads the state, and Save waits for a change`() {
        app(TestDeps(signedIn = true)).open(Routes.edit(2))

        rule.onNodeWithTag("abbreviation").assertTextEquals("Abbreviation", "RJ")
        rule.onNodeWithTag("name").assertTextContains("Rio de Janeiro")
        rule.onNodeWithTag("save").assertIsNotEnabled()
    }

    @Test
    fun `an edited state may keep its own abbreviation`() {
        app(TestDeps(signedIn = true)).open(Routes.edit(2))

        rule.onNodeWithTag("name").performTextReplacement("Rio")

        rule.onNodeWithTag("save").assertIsEnabled()
    }

    @Test
    fun `saving an edit updates the state and returns to the list`() {
        val deps = TestDeps(signedIn = true)
        val app = app(deps)
        rule.onNodeWithTag("edit-2").performClick()
        rule.onNodeWithTag("name").performTextReplacement("Rio")

        rule.onNodeWithTag("save").performClick()
        rule.waitForIdle()

        assertEquals(listOf("get 2", "update 2"), deps.repository.calls)
        assertEquals("Rio", deps.repository.units.first { it.id == 2 }.name)
        assertEquals(Routes.LIST, app.nav.currentDestination?.route)
    }

    @Test
    fun `a failed update stays on the form with the error`() {
        val deps = TestDeps(signedIn = true)
        app(deps).open(Routes.edit(2))
        deps.repository.failWrite = IOException("down")
        rule.onNodeWithTag("name").performTextReplacement("Rio")

        rule.onNodeWithTag("save").performClick()

        rule.onNodeWithText("Failed to update state.").assertIsDisplayed()
    }

    @Test
    fun `a state that cannot be loaded says so and shows no form`() {
        val deps = TestDeps(signedIn = true)
        deps.repository.failGet = ApiException(404, "123e4567-e89b-42d3-a456-426614174000", "HTTP 404")
        app(deps).open(Routes.edit(99))

        rule.onNodeWithText("Failed to fetch state.").assertIsDisplayed()
        rule.onNodeWithText("Reference ID: 123e4567-e89b-42d3-a456-426614174000").assertIsDisplayed()
        rule.onNodeWithTag("save").assertDoesNotExist()
    }

    @Test
    fun `an id that is not a positive number never reaches the api`() {
        val deps = TestDeps(signedIn = true)
        app(deps).open("state/abc/edit")

        rule.onNodeWithText("Invalid state.").assertIsDisplayed()
        assertEquals(emptyList(), deps.repository.calls)
    }
}
