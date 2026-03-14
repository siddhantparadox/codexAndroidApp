package dev.codex.mobile.core.data.usagewrapped

import dev.codex.mobile.core.model.UsageWrappedActivityDay
import dev.codex.mobile.core.model.UsageWrappedCostEstimate
import dev.codex.mobile.core.model.UsageWrappedDaySummary
import dev.codex.mobile.core.model.UsageWrappedHighlights
import dev.codex.mobile.core.model.UsageWrappedOverview
import dev.codex.mobile.core.model.UsageWrappedProjectSummary
import dev.codex.mobile.core.model.UsageWrappedRange
import dev.codex.mobile.core.model.UsageWrappedSourceSummary
import dev.codex.mobile.core.model.UsageWrappedSummary
import dev.codex.mobile.core.model.UsageWrappedTokenTotals

internal fun UsageWrappedSummaryDto.toModel(): UsageWrappedSummary = UsageWrappedSummary(
    generatedAt = generatedAt,
    range = UsageWrappedRange(
        start = range.start,
        end = range.end,
    ),
    overview = UsageWrappedOverview(
        startedAt = overview.startedAt,
        activeDays = overview.activeDays,
        sessionCount = overview.sessionCount,
        projectCount = overview.projectCount,
        currentStreakDays = overview.currentStreakDays,
        longestStreakDays = overview.longestStreakDays,
    ),
    tokenTotals = UsageWrappedTokenTotals(
        input = tokenTotals.input,
        cachedInput = tokenTotals.cachedInput,
        output = tokenTotals.output,
        reasoning = tokenTotals.reasoning,
        total = tokenTotals.total,
    ),
    costEstimate = costEstimate?.let { estimate ->
        UsageWrappedCostEstimate(
            approximateUsd = estimate.approximateUsd,
            currencyCode = estimate.currencyCode,
            basis = estimate.basis,
            coveragePercent = estimate.coveragePercent,
            note = estimate.note,
        )
    },
    highlights = UsageWrappedHighlights(
        mostActiveDay = highlights.mostActiveDay?.let { day ->
            UsageWrappedDaySummary(
                date = day.date,
                sessionCount = day.sessionCount,
                totalTokens = day.totalTokens,
            )
        },
        mostActiveProject = highlights.mostActiveProject?.let { project ->
            UsageWrappedProjectSummary(
                cwd = project.cwd,
                sessionCount = project.sessionCount,
                totalTokens = project.totalTokens,
            )
        },
        mostUsedSource = highlights.mostUsedSource?.let { source ->
            UsageWrappedSourceSummary(
                source = source.source,
                sessionCount = source.sessionCount,
            )
        },
    ),
    activity = activity.map { day ->
        UsageWrappedActivityDay(
            date = day.date,
            sessionCount = day.sessionCount,
            totalTokens = day.totalTokens,
        )
    },
)
