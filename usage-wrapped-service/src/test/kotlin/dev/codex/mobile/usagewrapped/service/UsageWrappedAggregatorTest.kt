package dev.codex.mobile.usagewrapped.service

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageWrappedAggregatorTest {
    private val zoneId: ZoneId = ZoneId.of("America/New_York")
    private val clock: Clock = Clock.fixed(
        Instant.parse("2026-03-14T16:00:00Z"),
        zoneId,
    )

    @Test
    fun `summarize aggregates streaks tokens and activity`() {
        val aggregator = UsageWrappedAggregator(
            zoneId = zoneId,
            clock = clock,
        )

        val summary = aggregator.summarize(
            sessions = listOf(
                session(
                    startedAt = "2026-03-12T12:00:00Z",
                    cwd = "D:/projects/codexAndroidApp",
                    source = "vscode",
                    totalTokens = 120,
                ),
                session(
                    startedAt = "2026-03-13T12:00:00Z",
                    cwd = "D:/projects/codexAndroidApp",
                    source = "vscode",
                    totalTokens = 220,
                ),
                session(
                    startedAt = "2026-03-14T14:00:00Z",
                    cwd = "D:/projects/sidecar",
                    source = "cli",
                    totalTokens = 300,
                ),
            ),
        )

        assertEquals("2026-03-12", summary.overview.startedAt)
        assertEquals(3, summary.overview.activeDays)
        assertEquals(3, summary.overview.sessionCount)
        assertEquals(2, summary.overview.projectCount)
        assertEquals(3, summary.overview.currentStreakDays)
        assertEquals(3, summary.overview.longestStreakDays)
        assertEquals(640L, summary.tokenTotals.total)
        assertEquals("2026-03-14", summary.highlights.mostActiveDay?.date)
        assertEquals("D:/projects/codexAndroidApp", summary.highlights.mostActiveProject?.cwd)
        assertEquals("vscode", summary.highlights.mostUsedSource?.source)
        assertEquals(3, summary.activity.size)
    }

    @Test
    fun `current streak resets when latest activity is older than yesterday`() {
        val aggregator = UsageWrappedAggregator(
            zoneId = zoneId,
            clock = clock,
        )

        val summary = aggregator.summarize(
            sessions = listOf(
                session(
                    startedAt = "2026-03-10T12:00:00Z",
                    totalTokens = 120,
                ),
                session(
                    startedAt = "2026-03-11T12:00:00Z",
                    totalTokens = 120,
                ),
            ),
        )

        assertEquals(0, summary.overview.currentStreakDays)
        assertEquals(2, summary.overview.longestStreakDays)
    }

    @Test
    fun `summarize estimates API equivalent cost from model totals`() {
        val aggregator = UsageWrappedAggregator(
            zoneId = zoneId,
            clock = clock,
        )

        val summary = aggregator.summarize(
            sessions = listOf(
                session(
                    startedAt = "2026-03-14T14:00:00Z",
                    modelTokenTotals = mapOf(
                        "gpt-5.4" to UsageTokenTotals(
                            input = 1_000_000L,
                            cachedInput = 1_000_000L,
                            output = 1_000_000L,
                            reasoning = 1_000_000L,
                            total = 4_000_000L,
                        ),
                        "gpt-5.3-codex-spark" to UsageTokenTotals(
                            input = 1_000_000L,
                            cachedInput = 1_000_000L,
                            output = 1_000_000L,
                            reasoning = 1_000_000L,
                            total = 4_000_000L,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(62.68, summary.costEstimate?.approximateUsd)
        assertEquals(100, summary.costEstimate?.coveragePercent)
        assertTrue(
            summary.costEstimate?.note?.contains(
                "gpt-5.3-codex-spark mapped to gpt-5.3-codex public API pricing.",
            ) == true,
        )
    }
}

private fun session(
    startedAt: String,
    cwd: String = "D:/projects/codexAndroidApp",
    source: String = "vscode",
    totalTokens: Long = 0L,
    modelTokenTotals: Map<String, UsageTokenTotals> = emptyMap(),
): CodexSessionSnapshot {
    val tokenTotals: UsageTokenTotals = if (modelTokenTotals.isEmpty()) {
        UsageTokenTotals(total = totalTokens)
    } else {
        modelTokenTotals.values.fold(UsageTokenTotals()) { acc, totals ->
            UsageTokenTotals(
                input = acc.input + totals.input,
                cachedInput = acc.cachedInput + totals.cachedInput,
                output = acc.output + totals.output,
                reasoning = acc.reasoning + totals.reasoning,
                total = acc.total + totals.total,
            )
        }
    }
    return CodexSessionSnapshot(
        startedAt = Instant.parse(startedAt),
        cwd = cwd,
        source = source,
        tokenTotals = tokenTotals,
        tokenTotalsByModel = modelTokenTotals,
    )
}
