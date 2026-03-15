package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.codeBlock
import dev.codex.mobile.core.designsystem.theme.codeInline

private sealed interface RichTextBlock {
    data class Paragraph(
        val text: String,
    ) : RichTextBlock

    data class CodeFence(
        val language: String?,
        val code: String,
    ) : RichTextBlock
}

@Composable
internal fun ThreadRichText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
    codeColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(text) { parseRichTextBlocks(text) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        blocks.forEach { block ->
            when (block) {
                is RichTextBlock.Paragraph -> {
                    Text(
                        text = buildInlineRichText(
                            text = block.text,
                            baseColor = textColor,
                            inlineCodeBackground = textColor.copy(alpha = 0.14f),
                            inlineCodeColor = textColor,
                        ),
                        style = textStyle,
                        color = textColor,
                    )
                }

                is RichTextBlock.CodeFence -> {
                    block.language?.takeIf { it.isNotBlank() }?.let { language ->
                        Text(
                            text = language.uppercase(),
                            style = MaterialTheme.typography.codeInline,
                            color = textColor.copy(alpha = 0.84f),
                        )
                        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
                    }
                    Surface(
                        color = codeBackground,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.codeBlock,
                            color = codeColor,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun parseRichTextBlocks(text: String): List<RichTextBlock> {
    if (text.isBlank()) return listOf(RichTextBlock.Paragraph(""))

    val blocks = mutableListOf<RichTextBlock>()
    val codeFenceRegex = Regex("(?s)```([A-Za-z0-9_+.-]*)\\n(.*?)\\n```")
    var lastIndex = 0

    codeFenceRegex.findAll(text).forEach { match ->
        val leadingText = text.substring(lastIndex, match.range.first)
        appendParagraphBlocks(blocks, leadingText)

        blocks += RichTextBlock.CodeFence(
            language = match.groupValues[1].ifBlank { null },
            code = match.groupValues[2],
        )
        lastIndex = match.range.last + 1
    }

    appendParagraphBlocks(blocks, text.substring(lastIndex))

    return blocks.ifEmpty { listOf(RichTextBlock.Paragraph(text)) }
}

private fun appendParagraphBlocks(
    destination: MutableList<RichTextBlock>,
    text: String,
) {
    text.trim('\n')
        .split(Regex("\\n\\s*\\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { paragraph ->
            destination += RichTextBlock.Paragraph(paragraph)
        }
}

private fun buildInlineRichText(
    text: String,
    baseColor: Color,
    inlineCodeBackground: Color,
    inlineCodeColor: Color,
) = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("`", index) -> {
                val closingIndex = text.indexOf('`', startIndex = index + 1)
                if (closingIndex > index + 1) {
                    withStyle(
                        SpanStyle(
                            color = inlineCodeColor,
                            background = inlineCodeBackground,
                            fontFamily = FontFamily.Monospace,
                        ),
                    ) {
                        append(text.substring(index + 1, closingIndex))
                    }
                    index = closingIndex + 1
                } else {
                    append(text[index])
                    index += 1
                }
            }

            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val delimiter = text.substring(index, index + 2)
                val closingIndex = text.indexOf(delimiter, startIndex = index + 2)
                if (closingIndex > index + 2) {
                    withStyle(
                        SpanStyle(
                            color = baseColor,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(text.substring(index + 2, closingIndex))
                    }
                    index = closingIndex + 2
                } else {
                    append(text[index])
                    index += 1
                }
            }

            else -> {
                append(text[index])
                index += 1
            }
        }
    }
}

