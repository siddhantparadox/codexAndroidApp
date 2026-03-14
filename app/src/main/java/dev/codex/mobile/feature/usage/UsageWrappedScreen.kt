package dev.codex.mobile.feature.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.SectionHeader
import dev.codex.mobile.core.designsystem.theme.CodexMobileTheme
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.UsageWrappedActivityDay
import dev.codex.mobile.core.model.UsageWrappedCostEstimate
import dev.codex.mobile.core.model.UsageWrappedDaySummary
import dev.codex.mobile.core.model.UsageWrappedHighlights
import dev.codex.mobile.core.model.UsageWrappedOverview
import dev.codex.mobile.core.model.UsageWrappedProjectSummary
import dev.codex.mobile.core.model.UsageWrappedRange
import dev.codex.mobile.core.model.UsageWrappedSourceSummary
import dev.codex.mobile.core.model.UsageWrappedState
import dev.codex.mobile.core.model.UsageWrappedSummary
import dev.codex.mobile.core.model.UsageWrappedTokenTotals
import dev.codex.mobile.core.model.usageWrappedPort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.text.NumberFormat
import java.util.Locale
@Composable
internal fun UsageWrappedScreen(
    onNavigateBack: () -> Unit,
    viewModel: UsageWrappedViewModel = viewModel(
        factory = UsageWrappedViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UsageWrappedContent(
        uiState = uiState,
        onNavigateBack = onNavigateBack,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun UsageWrappedContent(
    uiState: UsageWrappedUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary: UsageWrappedSummary? = uiState.wrapped.summary
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = CodexSpacing.screenHorizontal,
            top = CodexSpacing.topLevelHeaderGap,
            end = CodexSpacing.screenHorizontal,
            bottom = CodexSpacing.screenBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
    ) {
        item {
            UsageWrappedHeader(
                isRefreshing = uiState.wrapped.isLoading,
                onNavigateBack = onNavigateBack,
                onRefresh = onRefresh,
            )
        }
        item {
            UsageWrappedOverviewCard(
                host = uiState.activeHost,
                overview = summary?.overview,
                range = summary?.range,
                costEstimate = summary?.costEstimate,
            )
        }
        item {
            LiveQuotaRow(quota = uiState.quota)
        }
        uiState.wrapped.errorMessage?.let { errorMessage ->
            item {
                InfoCard(
                    title = "Connection note",
                    message = errorMessage,
                )
            }
        }
        when {
            summary == null && uiState.wrapped.isLoading -> {
                item {
                    EmptyStateCard(
                        title = "Loading history",
                        message = "Reading local Codex session logs from the desktop sidecar.",
                    )
                }
            }

            summary == null -> {
                item {
                    EmptyStateCard(
                        title = "Usage wrapped unavailable",
                        message = buildUnavailableMessage(uiState.activeHost),
                    )
                }
            }

            else -> {
                item {
                    ActivityHeatmapCard(
                        activity = summary.activity,
                    )
                }
                item {
                    UsageHighlightsCard(
                        highlights = summary.highlights,
                    )
                }
                item {
                    TokenBreakdownCard(
                        totals = summary.tokenTotals,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageWrappedHeader(
    isRefreshing: Boolean,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Column {
                Text(
                    text = "Usage Wrapped",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = if (isRefreshing) "Refreshing local history" else "Desktop session analytics",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh usage history",
            )
        }
    }
}

@Composable
private fun UsageWrappedOverviewCard(
    host: HostProfile?,
    overview: UsageWrappedOverview?,
    range: UsageWrappedRange?,
    costEstimate: UsageWrappedCostEstimate?,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionHeader(title = "Overview")
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        Text(
            text = host?.name ?: "No active desktop",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = listOfNotNull(
                range?.start?.let(::prettyDateLabel),
                host?.let(::hostSummary),
            ).joinToString(separator = "  •  ").ifBlank { "Connect to a desktop to view wrapped analytics." },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
        OverviewMetricsGrid(
            overview = overview,
            costEstimate = costEstimate,
        )
        costEstimate?.note?.let { note ->
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OverviewMetricsGrid(
    overview: UsageWrappedOverview?,
    costEstimate: UsageWrappedCostEstimate?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            OverviewMetricTile(
                label = "Started",
                value = overview?.startedAt?.let(::prettyDateLabel) ?: "Unavailable",
                modifier = Modifier.weight(1f),
            )
            OverviewMetricTile(
                label = "Active days",
                value = overview?.activeDays?.toString() ?: "0",
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            OverviewMetricTile(
                label = "Streak",
                value = "${overview?.currentStreakDays ?: 0}d",
                modifier = Modifier.weight(1f),
            )
            OverviewMetricTile(
                label = "Sessions",
                value = compactCount(overview?.sessionCount?.toLong()),
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            OverviewMetricTile(
                label = "Projects",
                value = overview?.projectCount?.toString() ?: "0",
                modifier = Modifier.weight(1f),
            )
            OverviewMetricTile(
                label = "Approx. cost",
                value = formatUsd(costEstimate?.approximateUsd),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OverviewMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveQuotaRow(
    quota: UsageWrappedQuotaUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        CompactQuotaCard(
            title = "5H",
            window = quota.fiveHourWindow,
            modifier = Modifier.weight(1f),
        )
        CompactQuotaCard(
            title = "7D",
            window = quota.weeklyWindow,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CompactQuotaCard(
    title: String,
    window: UsageWrappedQuotaWindowUiModel,
    modifier: Modifier = Modifier,
) {
    val progress: Float = ((window.usedPercent ?: 0).coerceIn(0, 100)) / 100f
    CodexCard(
        modifier = modifier,
        contentPadding = PaddingValues(14.dp),
    ) {
        Text(
            text = "$title USAGE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = window.usedPercent?.let { "$it%" } ?: "—",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Reset ${resetWindowLabel(window.resetsAtEpochSeconds)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActivityHeatmapCard(
    activity: List<UsageWrappedActivityDay>,
    modifier: Modifier = Modifier,
) {
    val activityByDate: Map<String, UsageWrappedActivityDay> = remember(activity) {
        activity.associateBy(UsageWrappedActivityDay::date)
    }
    val endDate: LocalDate = remember(activity) {
        activity.lastOrNull()?.date?.let(LocalDate::parse) ?: LocalDate.now()
    }
    val weeks: List<List<HeatmapCell>> = remember(activityByDate, endDate) {
        buildHeatmapWeeks(
            activityByDate = activityByDate,
            endDate = endDate,
        )
    }
    val thresholds: List<Long> = remember(activity) {
        buildHeatThresholds(activity)
    }
    val scrollState = rememberScrollState()
    var selectedDate by rememberSaveable(activity) {
        mutableStateOf(activity.lastOrNull()?.date)
    }
    val selectedDay: UsageWrappedActivityDay? = selectedDate?.let(activityByDate::get)

    LaunchedEffect(weeks.size, scrollState.maxValue) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    CodexCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionHeader(title = "Activity")
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        Text(
            text = "Last 52 weeks",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            weeks.forEach { week ->
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    week.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    color = heatColor(
                                        level = heatLevel(
                                            totalTokens = cell.totalTokens,
                                            thresholds = thresholds,
                                        ),
                                    ),
                                )
                                .clickable { selectedDate = cell.date },
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        Text(
            text = selectedDay?.let { day ->
                "${prettyDateLabel(day.date)}  •  ${day.sessionCount} sessions  •  ${compactCount(day.totalTokens)} tokens"
            } ?: "Tap a day to inspect activity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UsageHighlightsCard(
    highlights: UsageWrappedHighlights,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionHeader(title = "Highlights")
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        HighlightRow(
            label = "Most active day",
            title = highlights.mostActiveDay?.date?.let(::prettyDateLabel) ?: "Unavailable",
            detail = highlights.mostActiveDay?.let { day ->
                "${day.sessionCount} sessions  •  ${compactCount(day.totalTokens)} tokens"
            } ?: "No activity recorded yet.",
        )
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        HighlightRow(
            label = "Top project",
            title = highlights.mostActiveProject?.cwd?.let(::projectLabel) ?: "Unavailable",
            detail = highlights.mostActiveProject?.let { project ->
                "${project.sessionCount} sessions  •  ${compactCount(project.totalTokens)} tokens"
            } ?: "No project usage recorded yet.",
        )
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        HighlightRow(
            label = "Most used source",
            title = highlights.mostUsedSource?.source?.replaceFirstChar(Char::titlecase) ?: "Unavailable",
            detail = highlights.mostUsedSource?.let { source ->
                "${source.sessionCount} sessions"
            } ?: "Source data is not available yet.",
        )
    }
}

@Composable
private fun HighlightRow(
    label: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TokenBreakdownCard(
    totals: UsageWrappedTokenTotals,
    modifier: Modifier = Modifier,
) {
    val maxValue: Long = listOf(
        totals.input,
        totals.cachedInput,
        totals.output,
        totals.reasoning,
        totals.total,
    ).maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val rows: List<Pair<String, Long>> = listOf(
        "Input" to totals.input,
        "Cached input" to totals.cachedInput,
        "Output" to totals.output,
        "Reasoning" to totals.reasoning,
        "Total" to totals.total,
    )
    CodexCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionHeader(title = "Token breakdown")
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        rows.forEachIndexed { index, (label, value) ->
            TokenBreakdownRow(
                label = label,
                value = value,
                progress = value.toFloat() / maxValue.toFloat(),
            )
            if (index != rows.lastIndex) {
                Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            }
        }
    }
}

@Composable
private fun TokenBreakdownRow(
    label: String,
    value: Long,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = compactCount(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = Icons.Rounded.QueryStats,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private data class HeatmapCell(
    val date: String,
    val totalTokens: Long,
)

private fun buildHeatmapWeeks(
    activityByDate: Map<String, UsageWrappedActivityDay>,
    endDate: LocalDate,
): List<List<HeatmapCell>> {
    val rangeEnd: LocalDate = endDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val rangeStart: LocalDate = rangeEnd
        .minusWeeks(51)
        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0 until 52).map { weekIndex ->
        (0 until 7).map { dayIndex ->
            val date: LocalDate = rangeStart
                .plusWeeks(weekIndex.toLong())
                .plusDays(dayIndex.toLong())
            val key: String = date.toString()
            HeatmapCell(
                date = key,
                totalTokens = activityByDate[key]?.totalTokens ?: 0L,
            )
        }
    }
}

private fun buildHeatThresholds(activity: List<UsageWrappedActivityDay>): List<Long> {
    val totals: List<Long> = activity.map(UsageWrappedActivityDay::totalTokens)
        .filter { total -> total > 0L }
        .sorted()
    if (totals.isEmpty()) return listOf(0L, 0L, 0L)
    return listOf(
        totals[((totals.size - 1) * 0.25f).toInt()],
        totals[((totals.size - 1) * 0.50f).toInt()],
        totals[((totals.size - 1) * 0.75f).toInt()],
    )
}

private fun heatLevel(
    totalTokens: Long,
    thresholds: List<Long>,
): Int = when {
    totalTokens <= 0L -> 0
    totalTokens <= thresholds.getOrElse(0) { 0L } -> 1
    totalTokens <= thresholds.getOrElse(1) { 0L } -> 2
    totalTokens <= thresholds.getOrElse(2) { 0L } -> 3
    else -> 4
}

@Composable
private fun heatColor(level: Int): Color = when (level) {
    0 -> MaterialTheme.colorScheme.surfaceVariant
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.36f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.60f)
    else -> MaterialTheme.colorScheme.primary
}

private fun buildUnavailableMessage(host: HostProfile?): String = if (host == null) {
    "Connect to a desktop first. The wrapped view reads local Codex history through a sidecar desktop service."
} else {
    "Start the desktop usage history service on ${host.address}:${host.usageWrappedPort()} and refresh."
}

private fun hostSummary(host: HostProfile): String = "${host.address}:${host.port}  •  ${host.kind.name.lowercase()}"

private fun projectLabel(cwd: String): String = cwd.substringAfterLast('\\').substringAfterLast('/')

private fun prettyDateLabel(date: String): String = runCatching {
    LocalDate.parse(date).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM),
    )
}.getOrElse { date }

private fun formatUsd(value: Double?): String {
    if (value == null) return "Unavailable"
    return NumberFormat.getCurrencyInstance(Locale.US).format(value)
}

private fun compactCount(value: Long?): String {
    if (value == null) return "0"
    val absoluteValue: Long = kotlin.math.abs(value)
    return when {
        absoluteValue >= 1_000_000_000L -> compactWithSuffix(value, 1_000_000_000L, "B")
        absoluteValue >= 1_000_000L -> compactWithSuffix(value, 1_000_000L, "M")
        absoluteValue >= 1_000L -> compactWithSuffix(value, 1_000L, "K")
        else -> value.toString()
    }
}

private fun resetWindowLabel(resetsAtEpochSeconds: Long?): String {
    if (resetsAtEpochSeconds == null) return "Unavailable"
    val remainingSeconds: Long = (resetsAtEpochSeconds - (System.currentTimeMillis() / 1_000)).coerceAtLeast(0L)
    val remainingMinutes: Long = remainingSeconds / 60L
    return when {
        remainingMinutes < 1L -> "under 1 min"
        remainingMinutes < 60L -> "in ${remainingMinutes}m"
        remainingMinutes < 24L * 60L -> "in ${remainingMinutes / 60L}h"
        else -> "in ${remainingMinutes / (24L * 60L)}d"
    }
}

private fun compactWithSuffix(
    value: Long,
    divisor: Long,
    suffix: String,
): String {
    val truncatedValue: Double = value.toDouble() / divisor.toDouble()
    val roundedValue: Double = kotlin.math.round(truncatedValue * 10.0) / 10.0
    val formattedValue: String = if (roundedValue % 1.0 == 0.0) {
        roundedValue.toInt().toString()
    } else {
        roundedValue.toString()
    }
    return "$formattedValue$suffix"
}

@Preview(showBackground = true)
@Composable
private fun UsageWrappedContentPreview() {
    CodexMobileTheme(useDarkTheme = false) {
        UsageWrappedContent(
            uiState = UsageWrappedUiState(
                activeHost = HostProfile(
                    id = "preview",
                    name = "Studio Desktop",
                    address = "10.0.0.94",
                    port = 4500,
                    kind = HostKind.Desktop,
                    isActive = true,
                ),
                quota = UsageWrappedQuotaUiModel(
                    fiveHourWindow = UsageWrappedQuotaWindowUiModel(
                        usedPercent = 14,
                        resetsAtEpochSeconds = (System.currentTimeMillis() / 1_000) + 14_400L,
                        windowDurationMins = 300,
                    ),
                    weeklyWindow = UsageWrappedQuotaWindowUiModel(
                        usedPercent = 33,
                        resetsAtEpochSeconds = (System.currentTimeMillis() / 1_000) + 302_400L,
                        windowDurationMins = 10_080,
                    ),
                ),
                wrapped = UsageWrappedState(
                    hostId = "preview",
                    summary = previewSummary(),
                ),
            ),
            onNavigateBack = {},
            onRefresh = {},
        )
    }
}

private fun previewSummary(): UsageWrappedSummary {
    val today: LocalDate = LocalDate.now(ZoneId.systemDefault())
    val activity: List<UsageWrappedActivityDay> = (0 until 90).mapNotNull { index ->
        val date: LocalDate = today.minusDays((89 - index).toLong())
        val sessions: Int = when {
            date.dayOfWeek.value >= 6 && index % 2 == 0 -> 0
            index % 10 == 0 -> 3
            index % 4 == 0 -> 2
            else -> 1
        }
        if (sessions == 0) {
            null
        } else {
            UsageWrappedActivityDay(
                date = date.toString(),
                sessionCount = sessions,
                totalTokens = sessions * (14_000L + (index % 6) * 5_500L),
            )
        }
    }
    val total: Long = activity.sumOf(UsageWrappedActivityDay::totalTokens)
    return UsageWrappedSummary(
        generatedAt = "${today}T12:00:00Z",
        range = UsageWrappedRange(
            start = activity.firstOrNull()?.date,
            end = activity.lastOrNull()?.date,
        ),
        overview = UsageWrappedOverview(
            startedAt = "2025-10-02",
            activeDays = activity.size,
            sessionCount = activity.sumOf(UsageWrappedActivityDay::sessionCount),
            projectCount = 12,
            currentStreakDays = 7,
            longestStreakDays = 18,
        ),
        tokenTotals = UsageWrappedTokenTotals(
            input = total * 61 / 100,
            cachedInput = total * 29 / 100,
            output = total * 6 / 100,
            reasoning = total * 4 / 100,
            total = total,
        ),
        costEstimate = UsageWrappedCostEstimate(
            approximateUsd = 1842.73,
            coveragePercent = 100,
            note = "Estimated using public GPT-5 and Codex API pricing. Reasoning tokens are treated at output-token rates.",
        ),
        highlights = UsageWrappedHighlights(
            mostActiveDay = activity.maxByOrNull(UsageWrappedActivityDay::totalTokens)?.let { day ->
                UsageWrappedDaySummary(
                    date = day.date,
                    sessionCount = day.sessionCount,
                    totalTokens = day.totalTokens,
                )
            },
            mostActiveProject = UsageWrappedProjectSummary(
                cwd = "D:/projects/codexAndroidApp",
                sessionCount = 41,
                totalTokens = total / 3,
            ),
            mostUsedSource = UsageWrappedSourceSummary(
                source = "vscode",
                sessionCount = activity.sumOf(UsageWrappedActivityDay::sessionCount) - 11,
            ),
        ),
        activity = activity,
    )
}



