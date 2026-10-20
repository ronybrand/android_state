package com.ronybrand.estados.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronybrand.estados.support.AppUnderTest
import com.ronybrand.estados.support.FakeStateRepository
import com.ronybrand.estados.support.TestDeps
import com.ronybrand.estados.support.assertAccessible
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Every screen, and every state of it worth looking at, against the touch target and label checks of [assertAccessible].
 * It is the counterpart of the guidelines the Flutter client runs on each of its screens, and it needs no device: the
 * checks read the semantics tree. Colour contrast is asserted on the palette itself, in ContrastTest.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val name = TestName()

    private fun app(deps: TestDeps = TestDeps(signedIn = true)) = AppUnderTest(rule, deps)

    private fun AppUnderTest.open(route: String) {
        rule.runOnUiThread { nav.navigate(route) }
        rule.waitForIdle()
    }

    private fun check(screen: String = name.methodName) = rule.assertAccessible(screen)

    @Test
    fun `the list, signed in`() {
        app()
        check()
    }

    @Test
    fun `the list, signed out`() {
        app(TestDeps(signedIn = false))
        check()
    }

    @Test
    fun `the list, empty`() {
        app(TestDeps(repository = FakeStateRepository(initial = emptyList()), signedIn = true))
        check()
    }

    @Test
    fun `the list, after a failed first load`() {
        app(TestDeps(repository = FakeStateRepository().apply { failList = IOException("down") }))
        check()
    }

    @Test
    fun `the list with a sort applied and an error banner`() {
        val deps = TestDeps(signedIn = true)
        app(deps)
        deps.repository.failWrite = IOException("down")
        rule.onNodeWithTag("sort-nome").performClick()
        rule.onNodeWithTag("delete-1").performClick()
        rule.onNodeWithTag("confirm-delete").performClick()
        check()
    }

    @Test
    fun `the delete confirmation`() {
        app()
        rule.onNodeWithTag("delete-1").performClick()
        check()
    }

    @Test
    fun `the login`() {
        app(TestDeps()).open(Routes.LOGIN)
        check()
    }

    @Test
    fun `the login with a filled form and a refused password`() {
        val deps = TestDeps()
        deps.authRepository.failure = IOException("401")
        app(deps).open(Routes.LOGIN)
        rule.onNodeWithTag("username").performTextInput("admin")
        rule.onNodeWithTag("password").performTextInput("wrong")
        rule.onNodeWithTag("submit-login").performClick()
        check()
    }

    @Test
    fun `the create form, empty and with field errors`() {
        app().open(Routes.NEW)
        check()
        rule.onNodeWithTag("abbreviation").performClick()
        rule.onNodeWithTag("abbreviation").performTextInput("a")
        rule.onNodeWithTag("name").performClick()
        rule.onNodeWithTag("name").performTextInput("b")
        rule.onNodeWithTag("abbreviation").performClick()
        check()
    }

    @Test
    fun `the edit form`() {
        app().open(Routes.edit(2))
        check()
    }

    @Test
    fun `the edit form that failed to load`() {
        val deps = TestDeps(signedIn = true)
        deps.repository.failGet = IOException("404")
        app(deps).open(Routes.edit(99))
        check()
    }

    @Test
    fun `the edit form with an invalid id`() {
        app().open("state/abc/edit")
        check()
    }

    @Test
    fun `the AI assistant form`() {
        app().open(Routes.ASK_AI)
        check()
    }

    // Negative controls: without them, a checker that quietly checks nothing would look the same as a clean app.
    // Compose widens the touch area of every clickable to 48 dp on its own, so the control has to switch that off, as
    // a screen that opted out of it would.
    @Test
    fun `the check flags a tiny unlabelled button that opted out of the minimum touch target`() {
        rule.setContent {
            val configuration = LocalViewConfiguration.current
            val noMinimum = object : ViewConfiguration by configuration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
            CompositionLocalProvider(LocalViewConfiguration provides noMinimum) {
                Box(Modifier.size(16.dp).clickable { })
            }
        }

        val error = assertFailsWith<IllegalStateException> { check("a bad screen") }

        assertTrue("under 48" in error.message.orEmpty(), error.message)
        assertTrue("has no label" in error.message.orEmpty(), error.message)
    }

    @Test
    fun `the check accepts a labelled button of the minimum size`() {
        rule.setContent { Box(Modifier.size(48.dp).clickable { }.semantics { contentDescription = "Go" }) }

        check("a good screen")
    }
}
