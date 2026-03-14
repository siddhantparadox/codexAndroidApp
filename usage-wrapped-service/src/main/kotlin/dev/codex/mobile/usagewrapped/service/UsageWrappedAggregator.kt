package dev.codex.mobile.usagewrapped.service

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal class UsageWrappedAggregator(
    private val parser: CodexSessionParser = CodexSessionParser(),
    private val costEstimator: UsageCostEstimator = UsageCostEstimator(),
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val clock: Clock = Clock.system(zoneId),
) {
    fun summarize(sessionRoot: Path): UsageWrappedSummary {
        val sessions: List<CodexSessionSnapshot> = loadSessions(sessionRoot = sessionRoot)
        return summarize(sessions = sessions)
    }

    internal fun summarize(sessions: List<CodexSessionSnapshot>): UsageWrappedSummary {
        val now: Instant = clock.instant()
        val sortedSessions: List<CodexSessionSnapshot> = sessions.sortedBy { it.startedAt }
        if (sortedSessions.isEmpty()) {
            return UsageWrappedSummary(generatedAt = now.toString())
        }

        val dates: List<LocalDate> = sortedSessions.map { session ->
            session.startedAt.atZone(zoneId).toLocalDate()
        }
        val uniqueDates: List<LocalDate> = dates.distinct().sorted()
        val activityByDate: Map<LocalDate, UsageWrappedActivityDay> = buildActivityByDate(
            sessions = sortedSessions,
            dates = dates,
        )
        val projectStats: Map<String, UsageWrappedProjectSummary> = buildProjectStats(sortedSessions)
        val sourceStats: Map<String, UsageWrappedSourceSummary> = buildSourceStats(sortedSessions)
        val tokenTotalsByModel: Map<String, UsageTokenTotals> = buildTokenTotalsByModel(sortedSessions)
        val tokenTotals: UsageTokenTotals = sortedSessions
            .map(CodexSessionSnapshot::tokenTotals)
            .fold(UsageTokenTotals()) { acc, totals ->
                acc.plus(totals)
            }

        return UsageWrappedSummary(
            generatedAt = now.toString(),
            range = UsageWrappedRange(
                start = uniqueDates.firstOrNull()?.toString(),
                end = uniqueDates.lastOrNull()?.toString(),
            ),
            overview = UsageWrappedOverview(
                startedAt = uniqueDates.firstOrNull()?.toString(),
                activeDays = uniqueDates.size,
                sessionCount = sortedSessions.size,
                projectCount = projectStats.size,
                currentStreakDays = currentStreakDays(
                    activeDates = uniqueDates,
                    today = LocalDate.now(clock),
                ),
                longestStreakDays = longestStreakDays(activeDates = uniqueDates),
            ),
            tokenTotals = tokenTotals,
            costEstimate = costEstimator.estimate(tokenTotalsByModel = tokenTotalsByModel),
            highlights = UsageWrappedHighlights(
                mostActiveDay = activityByDate.values.maxWithOrNull(
                    compareBy<UsageWrappedActivityDay> { it.totalTokens }
                        .thenBy { it.sessionCount }
                )?.let { day ->
                    UsageWrappedDaySummary(
                        date = day.date,
                        sessionCount = day.sessionCount,
                        totalTokens = day.totalTokens,
                    )
                },
                mostActiveProject = projectStats.values.maxWithOrNull(
                    compareBy<UsageWrappedProjectSummary> { it.totalTokens }
                        .thenBy { it.sessionCount }
                ),
                mostUsedSource = sourceStats.values.maxWithOrNull(
                    compareBy<UsageWrappedSourceSummary> { it.sessionCount }
                ),
            ),
            activity = activityByDate.values.sortedBy(UsageWrappedActivityDay::date),
        )
    }

    private fun loadSessions(sessionRoot: Path): List<CodexSessionSnapshot> {
        if (!Files.exists(sessionRoot)) return emptyList()

        val sessions: MutableList<CodexSessionSnapshot> = mutableListOf()
        Files.walk(sessionRoot).use { paths ->
            paths
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(".jsonl") }
                .forEach { path ->
                    parser.parse(path)?.let(sessions::add)
                }
        }
        return sessions
    }
}

private fun buildActivityByDate(
    sessions: List<CodexSessionSnapshot>,
    dates: List<LocalDate>,
): Map<LocalDate, UsageWrappedActivityDay> {
    val dayStats: MutableMap<LocalDate, UsageWrappedActivityDay> = linkedMapOf()
    sessions.forEachIndexed { index, session ->
        val date: LocalDate = dates[index]
        val existing: UsageWrappedActivityDay? = dayStats[date]
        dayStats[date] = UsageWrappedActivityDay(
            date = date.toString(),
            sessionCount = (existing?.sessionCount ?: 0) + 1,
            totalTokens = (existing?.totalTokens ?: 0L) + session.tokenTotals.total,
        )
    }
    return dayStats
}

private fun buildProjectStats(
    sessions: List<CodexSessionSnapshot>,
): Map<String, UsageWrappedProjectSummary> {
    val projectStats: MutableMap<String, UsageWrappedProjectSummary> = linkedMapOf()
    sessions.forEach { session ->
        val cwd: String = session.cwd?.takeIf(String::isNotBlank) ?: return@forEach
        val existing: UsageWrappedProjectSummary? = projectStats[cwd]
        projectStats[cwd] = UsageWrappedProjectSummary(
            cwd = cwd,
            sessionCount = (existing?.sessionCount ?: 0) + 1,
            totalTokens = (existing?.totalTokens ?: 0L) + session.tokenTotals.total,
        )
    }
    return projectStats
}

private fun buildSourceStats(
    sessions: List<CodexSessionSnapshot>,
): Map<String, UsageWrappedSourceSummary> {
    val sourceStats: MutableMap<String, UsageWrappedSourceSummary> = linkedMapOf()
    sessions.forEach { session ->
        val source: String = session.source?.takeIf(String::isNotBlank) ?: return@forEach
        val existing: UsageWrappedSourceSummary? = sourceStats[source]
        sourceStats[source] = UsageWrappedSourceSummary(
            source = source,
            sessionCount = (existing?.sessionCount ?: 0) + 1,
        )
    }
    return sourceStats
}

private fun buildTokenTotalsByModel(
    sessions: List<CodexSessionSnapshot>,
): Map<String, UsageTokenTotals> {
    val totalsByModel: MutableMap<String, UsageTokenTotals> = linkedMapOf()
    sessions.forEach { session ->
        session.tokenTotalsByModel.forEach { (modelId, totals) ->
            totalsByModel[modelId] = (totalsByModel[modelId] ?: UsageTokenTotals()).plus(totals)
        }
    }
    return totalsByModel
}

private fun currentStreakDays(
    activeDates: List<LocalDate>,
    today: LocalDate,
): Int {
    if (activeDates.isEmpty()) return 0

    val latestActiveDate: LocalDate = activeDates.last()
    val streakAnchor: LocalDate = when {
        latestActiveDate == today -> today
        latestActiveDate == today.minusDays(1) -> latestActiveDate
        else -> return 0
    }

    val activeSet: Set<LocalDate> = activeDates.toSet()
    var cursor: LocalDate = streakAnchor
    var streak = 0
    while (cursor in activeSet) {
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}

private fun longestStreakDays(activeDates: List<LocalDate>): Int {
    if (activeDates.isEmpty()) return 0

    var longest = 1
    var current = 1
    activeDates.zipWithNext().forEach { (previous, next) ->
        if (previous.plusDays(1) == next) {
            current += 1
            longest = maxOf(longest, current)
        } else {
            current = 1
        }
    }
    return longest
}

private fun UsageTokenTotals.plus(other: UsageTokenTotals): UsageTokenTotals = UsageTokenTotals(
    input = input + other.input,
    cachedInput = cachedInput + other.cachedInput,
    output = output + other.output,
    reasoning = reasoning + other.reasoning,
    total = total + other.total,
)
