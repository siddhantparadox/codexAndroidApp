package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.codeBlock
import dev.codex.mobile.core.designsystem.theme.codeInline
import dev.codex.mobile.core.designsystem.theme.denseSupportingText
import dev.codex.mobile.core.designsystem.theme.panelHeadline
import dev.codex.mobile.core.model.FileChangeEntry

@Composable
internal fun PatchFileSummaryCard(
    change: FileChangeEntry,
    onReviewDiff: (FileChangeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val parsedDiff: ParsedUnifiedDiff = remember(change.diff) { ThreadDiffParser.parse(change.diff) }
    val fileName: String = change.path.fileName()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (fileName != change.path) {
                Text(
                    text = change.path,
                    style = MaterialTheme.typography.codeBlock,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(
                    label = change.kind,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (parsedDiff.addedLineCount > 0) {
                    StatusChip(
                        label = "+${parsedDiff.addedLineCount}",
                        color = AddedDiffAccent,
                    )
                }
                if (parsedDiff.removedLineCount > 0) {
                    StatusChip(
                        label = "-${parsedDiff.removedLineCount}",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Text(
                text = diffOverviewText(parsedDiff),
                style = MaterialTheme.typography.denseSupportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (change.diff.isNotBlank()) {
                ReviewDiffButton(
                    onClick = { onReviewDiff(change) },
                )
            }
        }
    }
}

@Composable
internal fun ThreadDiffViewerContent(
    change: FileChangeEntry,
    modifier: Modifier = Modifier,
) {
    val parsedDiff: ParsedUnifiedDiff = remember(change.diff) { ThreadDiffParser.parse(change.diff) }
    val fileName: String = change.path.fileName()
    var hideContext: Boolean by remember(change.diff) { mutableStateOf(parsedDiff.contextLineCount > 8) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = CodexSpacing.screenHorizontal,
            top = CodexSpacing.compactGap,
            end = CodexSpacing.screenHorizontal,
            bottom = CodexSpacing.screenBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
    ) {
        item(key = "diff-header") {
            Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap)) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.panelHeadline,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (fileName != change.path) {
                    Text(
                        text = change.path,
                        style = MaterialTheme.typography.codeBlock,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(
                        label = change.kind,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (parsedDiff.addedLineCount > 0) {
                        StatusChip(
                            label = "+${parsedDiff.addedLineCount}",
                            color = AddedDiffAccent,
                        )
                    }
                    if (parsedDiff.removedLineCount > 0) {
                        StatusChip(
                            label = "-${parsedDiff.removedLineCount}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (parsedDiff.contextLineCount > 0) {
                        ToggleChip(
                            label = if (hideContext) "Show Context" else "Hide Context",
                            onClick = { hideContext = !hideContext },
                        )
                    }
                }
            }
        }

        if (parsedDiff.hunks.isEmpty()) {
            item(key = "raw-diff") {
                RawDiffFallback(change.diff)
            }
        } else {
            parsedDiff.hunks.forEachIndexed { hunkIndex, hunk ->
                item(key = "hunk-$hunkIndex-header") {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.66f),
                        shape = CircleShape,
                    ) {
                        Text(
                            text = hunk.header,
                            style = MaterialTheme.typography.codeInline,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }

                val visibleRows: List<ParsedDiffRow> = if (hideContext) {
                    hunk.rows.filter { row -> row.type != ParsedDiffRowType.Context }
                } else {
                    hunk.rows
                }
                itemsIndexed(
                    items = visibleRows,
                    key = { rowIndex, row -> "hunk-$hunkIndex-row-$rowIndex-${row.oldLineNumber}-${row.newLineNumber}" },
                ) { _, row ->
                    DiffRowView(row = row)
                }
            }
        }
    }
}

@Composable
private fun ReviewDiffButton(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Review Diff",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ToggleChip(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DiffRowView(
    row: ParsedDiffRow,
) {
    val palette: DiffRowPalette = diffRowPalette(row.type)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = palette.container,
                shape = MaterialTheme.shapes.extraSmall,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DiffLineNumber(
            value = row.oldLineNumber?.toString(),
            color = palette.gutter,
        )
        DiffLineNumber(
            value = row.newLineNumber?.toString(),
            color = palette.gutter,
        )
        Text(
            text = row.type.prefix,
            style = MaterialTheme.typography.codeBlock,
            color = palette.accent,
            modifier = Modifier.width(12.dp),
        )
        Spacer(modifier = Modifier.width(CodexSpacing.microGap))
        Text(
            text = buildHighlightedLine(row, palette.highlight),
            style = MaterialTheme.typography.codeBlock,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiffLineNumber(
    value: String?,
    color: Color,
) {
    Text(
        text = value.orEmpty(),
        style = MaterialTheme.typography.codeInline,
        color = color,
        textAlign = TextAlign.End,
        modifier = Modifier.width(34.dp),
    )
}

@Composable
private fun RawDiffFallback(
    diff: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = diff,
            style = MaterialTheme.typography.codeBlock,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private data class DiffRowPalette(
    val accent: Color,
    val container: Color,
    val gutter: Color,
    val highlight: Color,
)

@Composable
private fun diffRowPalette(type: ParsedDiffRowType): DiffRowPalette = when (type) {
    ParsedDiffRowType.Added -> DiffRowPalette(
        accent = AddedDiffAccent,
        container = AddedDiffAccent.copy(alpha = 0.1f),
        gutter = AddedDiffAccent.copy(alpha = 0.9f),
        highlight = AddedDiffAccent.copy(alpha = 0.22f),
    )

    ParsedDiffRowType.Removed -> DiffRowPalette(
        accent = MaterialTheme.colorScheme.error,
        container = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        gutter = MaterialTheme.colorScheme.error.copy(alpha = 0.92f),
        highlight = MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
    )

    ParsedDiffRowType.Context -> DiffRowPalette(
        accent = MaterialTheme.colorScheme.onSurfaceVariant,
        container = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        gutter = MaterialTheme.colorScheme.onSurfaceVariant,
        highlight = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
    )
}

private fun buildHighlightedLine(
    row: ParsedDiffRow,
    highlightColor: Color,
): AnnotatedString = buildAnnotatedString {
    val text: String = row.text.ifEmpty { " " }
    append(text)
    row.highlightRanges.forEach { range ->
        val start: Int = range.first.coerceIn(0, text.length)
        val endExclusive: Int = (range.last + 1).coerceIn(start, text.length)
        if (start < endExclusive) {
            addStyle(
                style = SpanStyle(background = highlightColor),
                start = start,
                end = endExclusive,
            )
        }
    }
}

private fun diffOverviewText(parsedDiff: ParsedUnifiedDiff): String = when {
    parsedDiff.hunks.isEmpty() -> "Raw diff available"
    parsedDiff.hunks.size == 1 -> "1 hunk ready for inline review"
    else -> "${parsedDiff.hunks.size} hunks ready for inline review"
}

private val ParsedDiffRowType.prefix: String
    get() = when (this) {
        ParsedDiffRowType.Added -> "+"
        ParsedDiffRowType.Removed -> "-"
        ParsedDiffRowType.Context -> " "
    }

private fun String.fileName(): String = substringAfterLast('/').substringAfterLast('\\')

private val AddedDiffAccent: Color = Color(0xFF2F9A58)
