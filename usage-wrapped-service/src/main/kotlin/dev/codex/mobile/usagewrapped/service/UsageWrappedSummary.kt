package dev.codex.mobile.usagewrapped.service

import kotlinx.serialization.Serializable

@Serializable
internal data class UsageWrappedSummary(
    val generatedAt: String,
    val range: UsageWrappedRange = UsageWrappedRange(),
    val overview: UsageWrappedOverview = UsageWrappedOverview(),
    val tokenTotals: UsageTokenTotals = UsageTokenTotals(),
    val costEstimate: UsageWrappedCostEstimate? = null,
    val highlights: UsageWrappedHighlights = UsageWrappedHighlights(),
    val activity: List<UsageWrappedActivityDay> = emptyList(),
)

@Serializable
internal data class UsageWrappedRange(
    val start: String? = null,
    val end: String? = null,
)

@Serializable
internal data class UsageWrappedOverview(
    val startedAt: String? = null,
    val activeDays: Int = 0,
    val sessionCount: Int = 0,
    val projectCount: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
)

@Serializable
internal data class UsageTokenTotals(
    val input: Long = 0L,
    val cachedInput: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val total: Long = 0L,
)

@Serializable
internal data class UsageWrappedCostEstimate(
    val approximateUsd: Double? = null,
    val currencyCode: String = "USD",
    val basis: String = "api_equivalent",
    val coveragePercent: Int = 0,
    val note: String? = null,
)

@Serializable
internal data class UsageWrappedHighlights(
    val mostActiveDay: UsageWrappedDaySummary? = null,
    val mostActiveProject: UsageWrappedProjectSummary? = null,
    val mostUsedSource: UsageWrappedSourceSummary? = null,
)

@Serializable
internal data class UsageWrappedDaySummary(
    val date: String,
    val sessionCount: Int,
    val totalTokens: Long,
)

@Serializable
internal data class UsageWrappedProjectSummary(
    val cwd: String,
    val sessionCount: Int,
    val totalTokens: Long,
)

@Serializable
internal data class UsageWrappedSourceSummary(
    val source: String,
    val sessionCount: Int,
)

@Serializable
internal data class UsageWrappedActivityDay(
    val date: String,
    val sessionCount: Int,
    val totalTokens: Long,
)
