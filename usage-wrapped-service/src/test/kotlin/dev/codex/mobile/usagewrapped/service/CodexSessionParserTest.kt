package dev.codex.mobile.usagewrapped.service

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class CodexSessionParserTest {
    private val parser = CodexSessionParser()

    @Test
    fun parseUsesGpt54LongContextBucketWhenLastTurnInputExceedsThreshold() {
        val sessionFile: Path = writeSession(
            "long-context",
            listOf(
                """{"type":"session_meta","payload":{"timestamp":"2026-03-14T12:00:00Z","cwd":"D:/projects/codexAndroidApp","source":"vscode"}}""",
                """{"type":"turn_context","payload":{"model":"gpt-5.4"}}""",
                """{"type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":400000,"cached_input_tokens":0,"output_tokens":40000,"reasoning_output_tokens":10000,"total_tokens":440000},"last_token_usage":{"input_tokens":400000,"cached_input_tokens":0,"output_tokens":40000,"reasoning_output_tokens":10000,"total_tokens":440000}}}}""",
            ),
        )

        val snapshot: CodexSessionSnapshot = requireNotNull(parser.parse(sessionFile))

        assertEquals(440_000L, snapshot.tokenTotals.total)
        assertEquals(440_000L, snapshot.tokenTotalsByModel["gpt-5.4-long-context"]?.total)
    }

    @Test
    fun parseCorrectsModelTotalsWhenCumulativeCountersMoveBackward() {
        val sessionFile: Path = writeSession(
            "regression",
            listOf(
                """{"type":"session_meta","payload":{"timestamp":"2026-03-14T12:00:00Z","cwd":"D:/projects/codexAndroidApp","source":"vscode"}}""",
                """{"type":"turn_context","payload":{"model":"gpt-5.4"}}""",
                """{"type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":80000,"cached_input_tokens":0,"output_tokens":8000,"reasoning_output_tokens":2000,"total_tokens":88000},"last_token_usage":{"input_tokens":80000,"cached_input_tokens":0,"output_tokens":8000,"reasoning_output_tokens":2000,"total_tokens":88000}}}}""",
                """{"type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":60000,"cached_input_tokens":0,"output_tokens":6000,"reasoning_output_tokens":1500,"total_tokens":66000},"last_token_usage":{"input_tokens":60000,"cached_input_tokens":0,"output_tokens":6000,"reasoning_output_tokens":1500,"total_tokens":66000}}}}""",
                """{"type":"event_msg","payload":{"type":"token_count","info":{"total_token_usage":{"input_tokens":70000,"cached_input_tokens":0,"output_tokens":7000,"reasoning_output_tokens":1700,"total_tokens":77000},"last_token_usage":{"input_tokens":70000,"cached_input_tokens":0,"output_tokens":7000,"reasoning_output_tokens":1700,"total_tokens":77000}}}}""",
            ),
        )

        val snapshot: CodexSessionSnapshot = requireNotNull(parser.parse(sessionFile))

        assertEquals(77_000L, snapshot.tokenTotals.total)
        assertEquals(77_000L, snapshot.tokenTotalsByModel["gpt-5.4"]?.total)
    }
}

private fun writeSession(
    prefix: String,
    lines: List<String>,
): Path {
    val directory: Path = Files.createTempDirectory("usage-wrapped-parser-")
    val file: Path = directory.resolve("$prefix.jsonl")
    Files.writeString(file, lines.joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()))
    return file
}
