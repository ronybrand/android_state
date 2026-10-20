package com.ronybrand.estados.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private const val MARKDOWN_LINK_COLOR_ARGB = 0xFF155E63L

private enum class BlockType {
    Paragraph,
    Heading,
    ListItem,
    Code,
    Quote,
}

private data class MarkdownBlock(val type: BlockType, val text: String)

@Composable
fun MarkdownAnswer(markdown: String, modifier: Modifier = Modifier) {
    val blocks = parseBlocks(markdown)
    SelectionContainer {
        Column(
            modifier = modifier.fillMaxWidth().testTag("ask-ai-answer"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            blocks.forEachIndexed { index, block ->
                val style = when (block.type) {
                    BlockType.Heading -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    BlockType.Code -> MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
                    else -> MaterialTheme.typography.bodyMedium
                }
                val content = when (block.type) {
                    BlockType.ListItem -> "• ${block.text}"
                    BlockType.Quote -> "“${block.text}”"
                    else -> block.text
                }
                if (block.type == BlockType.Code) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            inlineMarkdown(content),
                            style = style,
                            modifier = Modifier.padding(12.dp).testTag("markdown-block-$index"),
                        )
                    }
                } else {
                    Text(
                        inlineMarkdown(content),
                        style = style,
                        color = if (block.type == BlockType.Quote) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.testTag("markdown-block-$index"),
                    )
                }
            }
        }
    }
}

private fun parseBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock(BlockType.Paragraph, paragraph.joinToString("\n"))
            paragraph.clear()
        }
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.isBlank() -> {
                flushParagraph()
                index++
            }

            line.trimStart().startsWith("```") -> {
                flushParagraph()
                index++
                val code = mutableListOf<String>()
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    code += lines[index]
                    index++
                }
                blocks += MarkdownBlock(BlockType.Code, code.joinToString("\n"))
                index++
            }

            else -> {
                val heading = Regex("^ {0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$").matchEntire(line)
                val listItem = Regex("^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+)(.+)$").matchEntire(line)
                val quote = Regex("^\\s*>\\s?(.*)$").matchEntire(line)
                when {
                    heading != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock(BlockType.Heading, heading.groupValues[2])
                    }

                    listItem != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock(BlockType.ListItem, listItem.groupValues[1])
                    }

                    quote != null -> {
                        flushParagraph()
                        blocks += MarkdownBlock(BlockType.Quote, quote.groupValues[1])
                    }

                    else -> paragraph += line
                }
                index++
            }
        }
    }
    flushParagraph()
    return blocks
}

private val inlinePattern =
    Regex(
        """(\*\*[^*]+\*\*|__[^_]+__|(?<!\*)\*[^*]+\*(?!\*)|(?<!_)_[^_]+_(?!_)|`[^`]+`|\[([^\]]+)]\(https?://[^)]+\))""",
    )

private fun inlineMarkdown(text: String) = buildAnnotatedString {
    var cursor = 0
    for (match in inlinePattern.findAll(text)) {
        append(text.substring(cursor, match.range.first))
        val token = match.value
        val linkLabel = match.groups[2]?.value
        val (content, style) = when {
            linkLabel != null ->
                linkLabel to
                    SpanStyle(
                        color = Color(MARKDOWN_LINK_COLOR_ARGB),
                        textDecoration = TextDecoration.Underline,
                    )

            token.startsWith("**") || token.startsWith("__") ->
                token.substring(2, token.length - 2) to SpanStyle(fontWeight = FontWeight.Bold)

            token.startsWith("`") ->
                token.substring(1, token.length - 1) to SpanStyle(fontFamily = FontFamily.Monospace)

            else -> token.substring(1, token.length - 1) to SpanStyle(fontStyle = FontStyle.Italic)
        }
        pushStyle(style)
        append(content)
        pop()
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
