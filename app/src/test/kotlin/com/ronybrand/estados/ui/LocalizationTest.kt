package com.ronybrand.estados.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ronybrand.estados.core.ApiException
import com.ronybrand.estados.support.AppUnderTest
import com.ronybrand.estados.support.FakeStateRepository
import com.ronybrand.estados.support.TestDeps
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "pt-rBR")
class LocalizationTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `the list and its accessibility labels follow the Brazilian Portuguese device locale`() {
        AppUnderTest(rule, TestDeps(signedIn = true))

        rule.onNodeWithText("Estados brasileiros").assertIsDisplayed()
        rule.onNodeWithText("Novo estado").assertIsDisplayed()
        rule.onNodeWithText("Ordenar por").assertIsDisplayed()
        rule.onNodeWithContentDescription("Editar SP").assertIsDisplayed()
        rule.onNodeWithContentDescription("Excluir SP").assertIsDisplayed()
        rule.onNodeWithText("Criado: 10 de jan. de 2026 09:00:00").assertIsDisplayed()
    }

    @Test
    fun `the login and state form follow the Brazilian Portuguese device locale`() {
        AppUnderTest(rule, TestDeps(signedIn = true))
        rule.onNodeWithTag("new-state").performClick()

        rule.onNodeWithText("Novo estado").assertIsDisplayed()
        rule.onNodeWithText("Sigla").assertIsDisplayed()
        rule.onNodeWithText("Nome").assertIsDisplayed()
        rule.onNodeWithText("Salvar").assertIsDisplayed()
        rule.onNodeWithTag("abbreviation").performClick()
        rule.onNodeWithTag("abbreviation").performTextInput("a")
        rule.onNodeWithTag("name").performClick()
        rule.onNodeWithText("Digite a sigla do estado com 2 letras.").assertIsDisplayed()

        rule.onNodeWithTag("back").performClick()
        rule.onNodeWithTag("log-out").performClick()
        rule.onNodeWithTag("log-in").performClick()

        rule.onAllNodesWithText("Entrar").assertCountEquals(2)
        rule.onNodeWithText("Usuário").assertIsDisplayed()
        rule.onNodeWithText("Senha").assertIsDisplayed()
    }

    @Test
    fun `API availability errors and their action are localized`() {
        val repository = FakeStateRepository().apply { failList = ApiException(503, null, "HTTP 503") }
        AppUnderTest(rule, TestDeps(repository = repository))

        rule.onNodeWithText(
            "A API está offline (pausada para reduzir custos). Ative-a pelo link abaixo; isso leva cerca de 1 minuto.",
        ).assertIsDisplayed()
        rule.onNodeWithText("Ativar a API").assertIsDisplayed()
    }
}
