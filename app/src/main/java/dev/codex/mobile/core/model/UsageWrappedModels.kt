package dev.codex.mobile.core.model

data class UsageWrappedState(
    val hostId: String? = null,
    val summary: UsageWrappedSummary? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class UsageWrappedSummary(
    val generatedAt: String? = null,
    val range: UsageWrappedRange = UsageWrappedRange(),
    val overview: UsageWrappedOverview = UsageWrappedOverview(),
    val tokenTotals: UsageWrappedTokenTotals = UsageWrappedTokenTotals(),
    val costEstimate: UsageWrappedCostEstimate? = null,
    val highlights: UsageWrappedHighlights = UsageWrappedHighlights(),
    val activity: List<UsageWrappedActivityDay> = emptyList(),
)

data class UsageWrappedRange(
    val start: String? = null,
    val end: String? = null,
)

data class UsageWrappedOverview(
    val startedAt: String? = null,
    val activeDays: Int = 0,
    val sessionCount: Int = 0,
    val projectCount: Int = 0,
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
)

data class UsageWrappedTokenTotals(
    val input: Long = 0L,
    val cachedInput: Long = 0L,
    val output: Long = 0L,
    val reasoning: Long = 0L,
    val total: Long = 0L,
)

data class UsageWrappedCostEstimate(
    val approximateUsd: Double? = null,
    val currencyCode: String = "USD",
    val basis: String = "api_equivalent",
    val coveragePercent: Int = 0,
    val note: String? = null,
)

data class UsageWrappedHighlights(
    val mostActiveDay: UsageWrappedDaySummary? = null,
    val mostActiveProject: UsageWrappedProjectSummary? = null,
    val mostUsedSource: UsageWrappedSourceSummary? = null,
)

data class UsageWrappedDaySummary(
    val date: String,
    val sessionCount: Int,
    val totalTokens: Long,
)

data class UsageWrappedProjectSummary(
    val cwd: String,
    val sessionCount: Int,
    val totalTokens: Long,
)

data class UsageWrappedSourceSummary(
    val source: String,
    val sessionCount: Int,
)

data class UsageWrappedActivityDay(
    val date: String,
    val sessionCount: Int,
    val totalTokens: Long,
)

fun HostProfile.usageWrappedPort(): Int = if (port >= 65_535) {
    65_535
} else {
    port + 1
}
