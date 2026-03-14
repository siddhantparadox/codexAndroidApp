package dev.codex.mobile.feature.threaddetail

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType

internal data class ParsedUnifiedDiff(
    val oldFile: String?,
    val newFile: String?,
    val hunks: List<ParsedDiffHunk>,
    val addedLineCount: Int,
    val removedLineCount: Int,
    val contextLineCount: Int,
)

internal data class ParsedDiffHunk(
    val header: String,
    val rows: List<ParsedDiffRow>,
)

internal data class ParsedDiffRow(
    val type: ParsedDiffRowType,
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val text: String,
    val highlightRanges: List<IntRange> = emptyList(),
)

internal enum class ParsedDiffRowType {
    Added,
    Removed,
    Context,
}

internal object ThreadDiffParser {
    fun parse(diff: String): ParsedUnifiedDiff {
        if (diff.isBlank()) {
            return ParsedUnifiedDiff(
                oldFile = null,
                newFile = null,
                hunks = emptyList(),
                addedLineCount = 0,
                removedLineCount = 0,
                contextLineCount = 0,
            )
        }

        val normalizedLines: List<String> = diff
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')

        val hunks: MutableList<ParsedDiffHunk> = mutableListOf()
        var oldFile: String? = null
        var newFile: String? = null
        var currentHeader: String? = null
        var currentRows: MutableList<ParsedDiffRow> = mutableListOf()
        var oldLineNumber: Int = 0
        var newLineNumber: Int = 0

        fun flushCurrentHunk() {
            val header: String = currentHeader ?: return
            hunks += ParsedDiffHunk(
                header = header,
                rows = addInlineHighlights(currentRows),
            )
            currentHeader = null
            currentRows = mutableListOf()
        }

        normalizedLines.forEach { line ->
            when {
                line.startsWith("--- ") -> {
                    oldFile = line.removePrefix("--- ").trim().ifBlank { null }
                }

                line.startsWith("+++ ") -> {
                    newFile = line.removePrefix("+++ ").trim().ifBlank { null }
                }

                line.startsWith("@@") -> {
                    flushCurrentHunk()
                    currentHeader = line
                    val coordinates: MatchResult? = HUNK_HEADER_REGEX.find(line)
                    oldLineNumber = coordinates?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    newLineNumber = coordinates?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                }

                currentHeader != null -> when {
                    line.startsWith("+") && !line.startsWith("+++") -> {
                        currentRows += ParsedDiffRow(
                            type = ParsedDiffRowType.Added,
                            oldLineNumber = null,
                            newLineNumber = newLineNumber,
                            text = line.removePrefix("+"),
                        )
                        newLineNumber += 1
                    }

                    line.startsWith("-") && !line.startsWith("---") -> {
                        currentRows += ParsedDiffRow(
                            type = ParsedDiffRowType.Removed,
                            oldLineNumber = oldLineNumber,
                            newLineNumber = null,
                            text = line.removePrefix("-"),
                        )
                        oldLineNumber += 1
                    }

                    line.startsWith(" ") -> {
                        currentRows += ParsedDiffRow(
                            type = ParsedDiffRowType.Context,
                            oldLineNumber = oldLineNumber,
                            newLineNumber = newLineNumber,
                            text = line.removePrefix(" "),
                        )
                        oldLineNumber += 1
                        newLineNumber += 1
                    }

                    line == NO_NEWLINE_MARKER -> Unit

                    else -> {
                        currentRows += ParsedDiffRow(
                            type = ParsedDiffRowType.Context,
                            oldLineNumber = oldLineNumber,
                            newLineNumber = newLineNumber,
                            text = line,
                        )
                        oldLineNumber += 1
                        newLineNumber += 1
                    }
                }
            }
        }

        flushCurrentHunk()

        val flattenedRows: List<ParsedDiffRow> = hunks.flatMap(ParsedDiffHunk::rows)
        return ParsedUnifiedDiff(
            oldFile = oldFile,
            newFile = newFile,
            hunks = hunks,
            addedLineCount = flattenedRows.count { row -> row.type == ParsedDiffRowType.Added },
            removedLineCount = flattenedRows.count { row -> row.type == ParsedDiffRowType.Removed },
            contextLineCount = flattenedRows.count { row -> row.type == ParsedDiffRowType.Context },
        )
    }

    private fun addInlineHighlights(rows: List<ParsedDiffRow>): List<ParsedDiffRow> {
        if (rows.isEmpty()) return rows

        val highlightedRows: MutableList<ParsedDiffRow> = rows.toMutableList()
        var index: Int = 0

        while (index < rows.size) {
            if (rows[index].type == ParsedDiffRowType.Context) {
                index += 1
                continue
            }

            val removedIndexes: MutableList<Int> = mutableListOf()
            val addedIndexes: MutableList<Int> = mutableListOf()
            while (index < rows.size && rows[index].type != ParsedDiffRowType.Context) {
                when (rows[index].type) {
                    ParsedDiffRowType.Removed -> removedIndexes += index
                    ParsedDiffRowType.Added -> addedIndexes += index
                    ParsedDiffRowType.Context -> Unit
                }
                index += 1
            }

            val pairedLineCount: Int = minOf(removedIndexes.size, addedIndexes.size)
            repeat(pairedLineCount) { pairIndex ->
                val removedRowIndex: Int = removedIndexes[pairIndex]
                val addedRowIndex: Int = addedIndexes[pairIndex]
                val removedRow: ParsedDiffRow = highlightedRows[removedRowIndex]
                val addedRow: ParsedDiffRow = highlightedRows[addedRowIndex]
                val highlightPair: HighlightPair = computeHighlightPair(
                    leftText = removedRow.text,
                    rightText = addedRow.text,
                )
                highlightedRows[removedRowIndex] = removedRow.copy(highlightRanges = highlightPair.left)
                highlightedRows[addedRowIndex] = addedRow.copy(highlightRanges = highlightPair.right)
            }
        }

        return highlightedRows
    }

    private fun computeHighlightPair(
        leftText: String,
        rightText: String,
    ): HighlightPair {
        if (leftText.isBlank() || rightText.isBlank()) {
            return HighlightPair(emptyList(), emptyList())
        }

        val leftTokens: List<DiffToken> = tokenize(leftText)
        val rightTokens: List<DiffToken> = tokenize(rightText)
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return HighlightPair(emptyList(), emptyList())
        }

        return try {
            val patch = DiffUtils.diff(
                leftTokens.map(DiffToken::value),
                rightTokens.map(DiffToken::value),
            )
            val leftFlags = BooleanArray(leftTokens.size)
            val rightFlags = BooleanArray(rightTokens.size)
            patch.deltas.forEach { delta ->
                when (delta.type) {
                    DeltaType.CHANGE -> {
                        markChangedTokens(
                            flags = leftFlags,
                            start = delta.source.position,
                            size = delta.source.lines.size,
                        )
                        markChangedTokens(
                            flags = rightFlags,
                            start = delta.target.position,
                            size = delta.target.lines.size,
                        )
                    }

                    DeltaType.DELETE -> {
                        markChangedTokens(
                            flags = leftFlags,
                            start = delta.source.position,
                            size = delta.source.lines.size,
                        )
                    }

                    DeltaType.INSERT -> {
                        markChangedTokens(
                            flags = rightFlags,
                            start = delta.target.position,
                            size = delta.target.lines.size,
                        )
                    }

                    DeltaType.EQUAL -> Unit
                }
            }
            HighlightPair(
                left = compactRanges(leftTokens, leftFlags),
                right = compactRanges(rightTokens, rightFlags),
            )
        } catch (_: Exception) {
            HighlightPair(emptyList(), emptyList())
        }
    }
}

private data class HighlightPair(
    val left: List<IntRange>,
    val right: List<IntRange>,
)

private data class DiffToken(
    val value: String,
    val start: Int,
    val endExclusive: Int,
)

private fun tokenize(text: String): List<DiffToken> = TOKEN_REGEX.findAll(text)
    .map { match ->
        DiffToken(
            value = match.value,
            start = match.range.first,
            endExclusive = match.range.last + 1,
        )
    }
    .toList()

private fun markChangedTokens(
    flags: BooleanArray,
    start: Int,
    size: Int,
) {
    val endExclusive: Int = minOf(flags.size, start + size)
    for (index in start until endExclusive) {
        flags[index] = true
    }
}

private fun compactRanges(
    tokens: List<DiffToken>,
    flags: BooleanArray,
): List<IntRange> {
    if (tokens.isEmpty() || flags.none { it }) return emptyList()

    val ranges: MutableList<IntRange> = mutableListOf()
    var activeStart: Int? = null
    var activeEndExclusive: Int = 0

    tokens.forEachIndexed { index, token ->
        if (!flags[index]) {
            if (activeStart != null) {
                ranges += activeStart until activeEndExclusive
                activeStart = null
                activeEndExclusive = 0
            }
            return@forEachIndexed
        }

        if (activeStart == null) {
            activeStart = token.start
            activeEndExclusive = token.endExclusive
        } else {
            activeEndExclusive = token.endExclusive
        }
    }

    if (activeStart != null) {
        ranges += activeStart until activeEndExclusive
    }

    return ranges
}

private const val NO_NEWLINE_MARKER: String = "\\ No newline at end of file"
private val HUNK_HEADER_REGEX: Regex = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@.*""")
private val TOKEN_REGEX: Regex = Regex("""\s+|\w+|[^\w\s]""")
