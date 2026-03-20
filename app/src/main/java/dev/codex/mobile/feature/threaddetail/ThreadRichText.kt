package dev.codex.mobile.feature.threaddetail

import android.content.ClipData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.codeBlock
import dev.codex.mobile.core.designsystem.theme.codeInline
import kotlinx.coroutines.launch

private val WindowsAbsolutePathRegex: Regex = Regex("^[A-Za-z]:[/\\\\].*")
private val MarkdownBlockSyntaxRegex: Regex = Regex(
    pattern = """(?m)^(#{1,6}\s|>\s|[-*+]\s|\d+\.\s|```|~~~|\|.*\|$)""",
)
private val MarkdownInlineSyntaxRegex: Regex = Regex(
    pattern = """(\[[^]]+]\([^)]+\)|`[^`\n]+`|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~)""",
)

@Composable
internal fun ThreadRichText(
    text: String,
    textColor: Color,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
    codeColor: Color = MaterialTheme.colorScheme.onSurface,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    if (text.isBlank()) return

    if (!shouldUseMarkdownRenderer(text)) {
        SelectionContainer {
            Text(
                text = text,
                style = textStyle,
                color = textColor,
                modifier = modifier.fillMaxWidth(),
            )
        }
        return
    }

    val copyTextToClipboard: (String) -> Unit = rememberThreadClipboardCopy()
    val platformUriHandler: UriHandler = LocalUriHandler.current
    val markdownState = rememberMarkdownState(
        content = text,
        retainState = true,
    )
    val transcriptUriHandler: UriHandler = remember(copyTextToClipboard, platformUriHandler) {
        TranscriptUriHandler(
            onCopyLocalLink = copyTextToClipboard,
            delegate = platformUriHandler,
        )
    }

    CompositionLocalProvider(LocalUriHandler provides transcriptUriHandler) {
        SelectionContainer {
            Markdown(
                markdownState = markdownState,
                modifier = modifier.fillMaxWidth(),
                colors = markdownColor(
                    text = textColor,
                    codeBackground = codeBackground,
                    inlineCodeBackground = textColor.copy(alpha = 0.14f),
                    dividerColor = textColor.copy(alpha = 0.18f),
                    tableBackground = codeBackground.copy(alpha = 0.64f),
                ),
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.headlineSmall.copy(color = textColor),
                    h2 = MaterialTheme.typography.titleLarge.copy(color = textColor),
                    h3 = MaterialTheme.typography.titleMedium.copy(color = textColor),
                    h4 = MaterialTheme.typography.titleSmall.copy(color = textColor),
                    h5 = textStyle.copy(color = textColor, fontWeight = FontWeight.SemiBold),
                    h6 = textStyle.copy(color = textColor, fontWeight = FontWeight.SemiBold),
                    text = textStyle.copy(color = textColor),
                    code = MaterialTheme.typography.codeBlock.copy(color = codeColor),
                    inlineCode = MaterialTheme.typography.codeInline.copy(color = textColor),
                    quote = textStyle.copy(
                        color = textColor.copy(alpha = 0.92f),
                        fontStyle = FontStyle.Italic,
                    ),
                    paragraph = textStyle.copy(color = textColor),
                    ordered = textStyle.copy(color = textColor),
                    bullet = textStyle.copy(color = textColor),
                    list = textStyle.copy(color = textColor),
                    textLink = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    table = textStyle.copy(color = textColor),
                ),
                padding = markdownPadding(
                    block = 3.dp,
                    list = 2.dp,
                    listItemTop = 2.dp,
                    listItemBottom = 2.dp,
                    listIndent = 8.dp,
                    codeBlock = PaddingValues(0.dp),
                    blockQuote = PaddingValues(start = 12.dp, top = 0.dp, end = 0.dp, bottom = 0.dp),
                    blockQuoteText = PaddingValues(vertical = 4.dp),
                    blockQuoteBar = PaddingValues.Absolute(left = 0.dp, top = 2.dp, right = 6.dp, bottom = 2.dp),
                ),
                components = markdownComponents(
                    codeFence = { component ->
                        ThreadMarkdownCodeFence(
                            component = component,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            chromeColor = textColor.copy(alpha = 0.84f),
                            onCopyCode = copyTextToClipboard,
                        )
                    },
                    codeBlock = { component ->
                        ThreadMarkdownCodeBlock(
                            component = component,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            chromeColor = textColor.copy(alpha = 0.84f),
                            onCopyCode = copyTextToClipboard,
                        )
                    },
                ),
                loading = { loadingModifier -> Spacer(modifier = loadingModifier.height(0.dp)) },
                error = { errorModifier ->
                    Text(
                        text = text,
                        style = textStyle,
                        color = textColor,
                        modifier = errorModifier.fillMaxWidth(),
                    )
                },
            )
        }
    }
}

@Composable
private fun ThreadMarkdownCodeFence(
    component: MarkdownComponentModel,
    codeBackground: Color,
    codeColor: Color,
    chromeColor: Color,
    onCopyCode: (String) -> Unit,
) {
    MarkdownCodeFence(
        content = component.content,
        node = component.node,
        style = MaterialTheme.typography.codeBlock.copy(color = codeColor),
    ) { code, language, style ->
        ThreadMarkdownCodeContent(
            code = code,
            language = language,
            style = style.copy(color = codeColor),
            codeBackground = codeBackground,
            chromeColor = chromeColor,
            onCopyCode = onCopyCode,
        )
    }
}

@Composable
private fun ThreadMarkdownCodeBlock(
    component: MarkdownComponentModel,
    codeBackground: Color,
    codeColor: Color,
    chromeColor: Color,
    onCopyCode: (String) -> Unit,
) {
    MarkdownCodeBlock(
        content = component.content,
        node = component.node,
        style = MaterialTheme.typography.codeBlock.copy(color = codeColor),
    ) { code, language, style ->
        ThreadMarkdownCodeContent(
            code = code,
            language = language,
            style = style.copy(color = codeColor),
            codeBackground = codeBackground,
            chromeColor = chromeColor,
            onCopyCode = onCopyCode,
        )
    }
}

@Composable
private fun ThreadMarkdownCodeContent(
    code: String,
    language: String?,
    style: TextStyle,
    codeBackground: Color,
    chromeColor: Color,
    onCopyCode: (String) -> Unit,
) {
    Surface(
        color = codeBackground,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        ) {
            DisableSelection {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, top = 8.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = language?.takeIf { it.isNotBlank() }?.uppercase() ?: "CODE",
                        style = MaterialTheme.typography.codeInline,
                        color = chromeColor,
                    )
                    TranscriptCopyButton(
                        label = "Copy code",
                        tint = chromeColor,
                        onClick = {
                            onCopyCode(code)
                        },
                    )
                }
                HorizontalDivider(
                    color = chromeColor.copy(alpha = 0.18f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }

            Text(
                text = code,
                style = style,
                color = style.color,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 10.dp, top = 2.dp, end = 10.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
internal fun rememberThreadClipboardCopy(
    label: String = "Codex thread",
): (String) -> Unit {
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    return remember(clipboard, coroutineScope, label) {
        { copiedText ->
            coroutineScope.launch {
                clipboard.setClipEntry(
                    ClipEntry(
                        ClipData.newPlainText(label, copiedText),
                    ),
                )
            }
        }
    }
}

@Composable
internal fun TranscriptCopyButton(
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = tint.copy(alpha = 0.12f),
        contentColor = tint,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = null,
                modifier = Modifier.width(14.dp).height(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun TranscriptCopyIconButton(
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        color = tint.copy(alpha = 0.10f),
        contentColor = tint,
        shape = MaterialTheme.shapes.small,
    ) {
        Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Copy message",
            modifier = Modifier.padding(6.dp).width(14.dp).height(14.dp),
        )
    }
}

private class TranscriptUriHandler(
    private val onCopyLocalLink: (String) -> Unit,
    private val delegate: UriHandler,
) : UriHandler {
    override fun openUri(uri: String) {
        val normalizedUri: String = uri.trim()
        if (isTranscriptLocalLink(normalizedUri)) {
            onCopyLocalLink(normalizedUri)
            return
        }
        delegate.openUri(normalizedUri)
    }
}

internal fun isTranscriptLocalLink(uri: String): Boolean {
    if (uri.isBlank()) return false
    if (uri.startsWith("/")) return true
    if (uri.startsWith("./") || uri.startsWith("../")) return true
    return WindowsAbsolutePathRegex.matches(uri)
}

internal fun shouldUseMarkdownRenderer(text: String): Boolean {
    if (text.isBlank()) return false
    if ("```" in text || "~~~" in text) return true
    return MarkdownBlockSyntaxRegex.containsMatchIn(text) ||
        MarkdownInlineSyntaxRegex.containsMatchIn(text)
}
