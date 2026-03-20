package dev.codex.mobile.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.SectionHeader
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.util.relativeTimeLabel
import kotlin.math.round

private val TodaySparklineHeight = 26.dp
private val TodaySparklineMinimumBarHeight = 4.dp
private val TodaySparklineVariableBarHeight = 18.dp

@Composable
internal fun DashboardTodayCard(
    today: DashboardTodayUiModel,
    onClick: () -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
        ) {
            SectionHeader(
                title = "Today",
                trailing = {
                    Text(
                        text = today.dateLabel.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
            if (today.hasUsageHistory) {
                DashboardTodaySummaryRow(today = today)
            } else {
                DashboardTodayEmptySummary()
            }
            DashboardTodayFooter(
                today = today,
                onSyncNow = onSyncNow,
            )
        }
    }
}

@Composable
private fun DashboardTodaySummaryRow(
    today: DashboardTodayUiModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        ) {
            Text(
                text = today.primarySummaryLabel(),
                style = MaterialTheme.typography.titleMedium,
            )
            today.lastActiveAtEpochSeconds?.let { lastActive ->
                Text(
                    text = "Last active ${relativeTimeLabel(lastActive)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DashboardTodayMetricColumn(
            totalTokens = today.totalTokens,
            currentStreakDays = today.currentStreakDays,
        )
    }
}

@Composable
private fun DashboardTodayEmptySummary(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
    ) {
        Text(
            text = "Waiting for usage sync.",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "Connect your desktop to build today's summary.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardTodayMetricColumn(
    totalTokens: Long,
    currentStreakDays: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(112.dp),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        horizontalAlignment = Alignment.End,
    ) {
        DashboardTodayChip(
            label = "${formatCompactValue(totalTokens)} tokens",
            background = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (currentStreakDays > 0) {
            DashboardTodayChip(
                label = "$currentStreakDays-day streak",
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DashboardTodayFooter(
    today: DashboardTodayUiModel,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        verticalAlignment = Alignment.Bottom,
    ) {
        DashboardTodaySparkline(
            activity = today.activity,
            modifier = Modifier.weight(1f),
        )
        DashboardTodaySyncButton(
            canSync = today.canSync,
            isSyncing = today.isSyncing,
            onClick = onSyncNow,
        )
    }
}

@Composable
private fun DashboardTodaySparkline(
    activity: List<DashboardTodayActivityPoint>,
    modifier: Modifier = Modifier,
) {
    val maxSessions: Int = activity.maxOfOrNull(DashboardTodayActivityPoint::sessionCount) ?: 0
    Row(
        modifier = modifier.height(TodaySparklineHeight),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        activity.forEach { point ->
            val barHeightFraction: Float = if (maxSessions > 0) {
                point.sessionCount.toFloat() / maxSessions.toFloat()
            } else {
                0f
            }
            val barHeight = TodaySparklineMinimumBarHeight +
                (TodaySparklineVariableBarHeight * barHeightFraction)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(point.sparklineColor()),
                )
            }
        }
    }
}

@Composable
private fun DashboardTodaySyncButton(
    canSync: Boolean,
    isSyncing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background: Color = when {
        isSyncing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        canSync -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor: Color = when {
        isSyncing || canSync -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .clickable {
                if (canSync && !isSyncing) {
                    onClick()
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (isSyncing) "Syncing..." else "Sync now",
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun DashboardTodayChip(
    label: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardTodayActivityPoint.sparklineColor(): Color = when {
    isToday && sessionCount > 0 -> MaterialTheme.colorScheme.primary
    isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    sessionCount > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    else -> MaterialTheme.colorScheme.surfaceVariant
}

private fun DashboardTodayUiModel.primarySummaryLabel(): String = if (sessionCount <= 0) {
    "No sessions today yet"
} else {
    "$sessionCount ${sessionLabel(sessionCount)} today"
}

private fun sessionLabel(count: Int): String = if (count == 1) "session" else "sessions"

private fun formatCompactValue(value: Long): String {
    val absoluteValue: Long = kotlin.math.abs(value)
    return when {
        absoluteValue >= 1_000_000_000L -> compactWithSuffix(value, 1_000_000_000L, "B")
        absoluteValue >= 1_000_000L -> compactWithSuffix(value, 1_000_000L, "M")
        absoluteValue >= 1_000L -> compactWithSuffix(value, 1_000L, "K")
        else -> value.toString()
    }
}

private fun compactWithSuffix(
    value: Long,
    divisor: Long,
    suffix: String,
): String {
    val truncatedValue: Double = value.toDouble() / divisor.toDouble()
    val roundedValue: Double = round(truncatedValue * 10.0) / 10.0
    val formattedValue: String = if (roundedValue % 1.0 == 0.0) {
        roundedValue.toInt().toString()
    } else {
        roundedValue.toString()
    }
    return "$formattedValue$suffix"
}
