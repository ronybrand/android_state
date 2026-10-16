package com.ronybrand.estados.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * WCAG 2.1 AA asks for a contrast ratio of 4.5 for normal text (the footer is 11sp, so nothing here counts as large
 * text). The Flutter client runs the framework's text contrast guideline on each screen; the Accessibility Test
 * Framework's own contrast check works on rendered pixels and sees little of a Robolectric screen, so the palette is
 * asserted directly: every pairing the screens draw text on.
 */
class ContrastTest {
    private fun ratio(foreground: Color, background: Color): Double {
        val a = foreground.luminance().toDouble()
        val b = background.luminance().toDouble()
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    private fun assertReadable(name: String, foreground: Color, background: Color) {
        val value = ratio(foreground, background)
        assertTrue(value >= 4.5, "$name has a contrast ratio of ${"%.2f".format(value)}, below 4.5")
    }

    @Test
    fun `body text and the muted text on the page and on cards`() {
        assertReadable("muted on the page", AppColors.Muted, AppColors.Background)
        assertReadable("muted on a card", AppColors.Muted, Color.White)
        assertReadable("black on a card", Color.Black, Color.White)
        assertReadable("the brand abbreviation on a card", AppColors.Brand, Color.White)
    }

    @Test
    fun `white text on the filled buttons and the app bar`() {
        assertReadable("app bar", Color.White, AppColors.Brand)
        assertReadable("New state", Color.White, AppColors.Success)
        assertReadable("Delete", Color.White, AppColors.Danger)
    }

    @Test
    fun `error text on the error banner and on a card`() {
        assertReadable("error on the banner", AppColors.Danger, AppColors.DangerSoft)
        assertReadable("the reference id on the banner", AppColors.Muted, AppColors.DangerSoft)
        assertReadable("field errors on a white field", AppColors.Danger, Color.White)
    }
}
