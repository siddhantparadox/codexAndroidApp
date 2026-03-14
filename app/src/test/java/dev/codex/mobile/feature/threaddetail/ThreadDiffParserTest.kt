package dev.codex.mobile.feature.threaddetail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadDiffParserTest {

    @Test
    fun parse_extracts_hunks_counts_and_line_numbers() {
        val parsed: ParsedUnifiedDiff = ThreadDiffParser.parse(
            """
            --- a/sample.txt
            +++ b/sample.txt
            @@ -1,3 +1,4 @@
             line one
            -old two
            +new two
             line three
            +line four
            """.trimIndent(),
        )

        assertEquals("a/sample.txt", parsed.oldFile)
        assertEquals("b/sample.txt", parsed.newFile)
        assertEquals(1, parsed.hunks.size)
        assertEquals(2, parsed.addedLineCount)
        assertEquals(1, parsed.removedLineCount)
        assertEquals(2, parsed.contextLineCount)

        val rows: List<ParsedDiffRow> = parsed.hunks.single().rows
        assertEquals(ParsedDiffRowType.Context, rows[0].type)
        assertEquals(1, rows[0].oldLineNumber)
        assertEquals(1, rows[0].newLineNumber)
        assertEquals(ParsedDiffRowType.Removed, rows[1].type)
        assertEquals(2, rows[1].oldLineNumber)
        assertEquals(null, rows[1].newLineNumber)
        assertEquals(ParsedDiffRowType.Added, rows[2].type)
        assertEquals(null, rows[2].oldLineNumber)
        assertEquals(2, rows[2].newLineNumber)
    }

    @Test
    fun parse_adds_word_level_highlights_for_changed_lines() {
        val parsed: ParsedUnifiedDiff = ThreadDiffParser.parse(
            """
            @@ -1,2 +1,2 @@
            -status: draft
            -alpha
            +status: updated
            +alpha revised
            """.trimIndent(),
        )

        val removedRows: List<ParsedDiffRow> = parsed.hunks.single().rows.filter { row ->
            row.type == ParsedDiffRowType.Removed
        }
        val addedRows: List<ParsedDiffRow> = parsed.hunks.single().rows.filter { row ->
            row.type == ParsedDiffRowType.Added
        }

        assertTrue(removedRows.isNotEmpty())
        assertTrue(addedRows.isNotEmpty())
        assertTrue(removedRows.any { row -> row.highlightRanges.isNotEmpty() })
        assertTrue(addedRows.any { row -> row.highlightRanges.isNotEmpty() })
        assertFalse(addedRows.any { row -> row.text.isBlank() })
    }
}
