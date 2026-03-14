package dev.codex.mobile.core.data.usagewrapped

import kotlinx.serialization.Serializable

@Serializable
internal data class UsageWrappedSummaryDto(
    val generatedAt: String? = null,
    val range: UsageWrappedRangeDto = UsageWrappedRangeDto(),
    val overview: UsageWrappedOverviewDto = UsageWrappedOverviewDto(),
    val tokenTotals: UsageWrappedTokenTotalsDto = UsageWrappedTokenTotalsDto(),
    val costEstimate: UsageWrappedCostEstimateDto? = null,
    val highlights: UsageWrappedHighlightsDto = UsageWrappedHighlightsDto(),
    val activity: List<UsageWrappedActivityDayDto> = emptyList(),
)

@Serializable
internal data class UsageWrappedRangeDto(
    val start: String? = null,
    val end: String? = null,
)

@Serializable
internal data class UsageWrappedOverviewDto(
    val startedAt: String? = null,
    val activeDays: Int = 0,
    val sessionCount: Int = 0,
    val projectCount: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
)

@Serializable
internal data class UsageWrappedTokenTotalsDto(
    val input: Long = 0L,
    val cachedInput: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val total: Long = 0L,
)

@Serializable
internal data class UsageWrappedCostEstimateDto(
    val approximateUsd: Double? = null,
    val currencyCode: String = "USD",
    val basis: String = "api_equivalent",
    val coveragePercent: Int = 0,
    val note: String? = null,
)

@Serializable
internal data class UsageWrappedHighlightsDto(
    val mostActiveDay: UsageWrappedDaySummaryDto? = null,
    val mostActiveProject: UsageWrappedProjectSummaryDto? = null,
    val mostUsedSource: UsageWrappedSourceSummaryDto? = null,
)

@Serializable
internal data class UsageWrappedDaySummaryDto(
    val date: String,
    val sessionCount: Int,
    val totalTokens: Long,
)

@Serializable
internal data class UsageWrappedProjectSummaryDto(
    val cwd: String,
    val sessionCount: Int,
    val totalTokens: Long,
)

@Serializable
internal data class UsageWrappedSourceSummaryDto(
    val source: String,
    val sessionCount: Int,
)

@Serializable
internal data class UsageWrappedActivityDayDto(
    val date: String,
    val sessionCount: Int,
    val totalTokens: Long,
)
