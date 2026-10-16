package com.ronybrand.estados.support

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** The smallest touch target Android's guidelines (and Flutter's `androidTapTargetGuideline`) accept, in dp. */
const val MIN_TARGET_DP = 48

/**
 * What a screen reader and a thumb need from every control on screen, checked on the merged semantics tree (the one
 * TalkBack walks):
 *
 * - a touch target of at least 48 x 48 dp: the area a finger can hit, which Compose widens to that size for small
 *   controls unless a screen opts out of it (as a `minimumInteractiveComponentSize` of zero would);
 * - a label: the text, the content description or, for a text field, the label it was given. A control a screen
 *   reader can only announce as "button" is a control nobody can use.
 *
 * This is the counterpart of the tap target and label guidelines the Flutter client runs on each of its screens. The
 * Accessibility Test Framework's own checks (`enableAccessibilityChecks`) were tried first and did not flag a 16 dp
 * unlabelled button under Robolectric, so they are not used: a checker that cannot fail proves nothing.
 *
 * Throws with every problem found on the screen, not just the first.
 */
fun ComposeContentTestRule.assertAccessible(screen: String) {
    val minimum = MIN_TARGET_DP * density.density
    val problems = onAllNodes(hasClickAction() or hasSetTextAction()).fetchSemanticsNodes().flatMap { node ->
        buildList {
            val width = node.touchBoundsInRoot.width
            val height = node.touchBoundsInRoot.height
            if (width < minimum || height < minimum) {
                add(
                    "${node.describe()} is ${width / density.density} x ${height / density.density} dp, under $MIN_TARGET_DP",
                )
            }
            if (node.label().isBlank()) add("${node.describe()} has no label")
        }
    }
    check(problems.isEmpty()) { "$screen is not accessible:\n  " + problems.joinToString("\n  ") }
}

private fun SemanticsNode.label(): String {
    val text = config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ")
    val description = config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString(" ")
    return "$text $description".trim()
}

private fun SemanticsNode.describe(): String =
    config.getOrNull(SemanticsProperties.TestTag)?.let { "the control \"$it\"" } ?: "a control (id $id)"
