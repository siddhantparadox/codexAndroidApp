package dev.codex.mobile.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.SectionHeader
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadSummary

@Composable
fun DashboardScreen(
    onOpenThreads: () -> Unit,
    onOpenHostConnection: () -> Unit,
    onOpenThread: (String) -> Unit,
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            top = 18.dp,
            end = 20.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item { DashboardHeader() }
        item {
            uiState.activeHost?.let { host ->
                ConnectionCard(
                    hostName = host.name,
                    qualityLabel = host.qualityLabel,
                    hostKind = host.kind,
                    onClick = onOpenHostConnection,
                )
            }
        }
        item {
            uiState.runningThread?.let { runningThread ->
                RunningThreadCard(
                    thread = runningThread,
                    onClick = { onOpenThread(runningThread.id) },
                )
            }
        }
        item {
            SectionHeader(
                title = "Recent Threads",
                trailing = {
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onOpenThreads),
                    )
                },
            )
        }
        items(uiState.recentThreads) { thread ->
            RecentThreadRow(
                thread = thread,
                onClick = { onOpenThread(thread.id) },
            )
        }
    }
}

@Composable
private fun DashboardHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LaptopMac,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Codex Mobile",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "SG",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    hostName: String,
    qualityLabel: String,
    hostKind: HostKind,
    onClick: () -> Unit,
) {
    CodexCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (hostKind == HostKind.Laptop) Icons.Rounded.LaptopMac else Icons.Rounded.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Laptop Connection".uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = hostName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            StatusChip(
                label = "Active",
                color = MaterialTheme.colorScheme.primary,
                pulsingDot = true,
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "LAN Connection: $qualityLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RunningThreadCard(
    thread: ThreadSummary,
    onClick: () -> Unit,
) {
    val progress by animateFloatAsState(
        targetValue = (thread.progressPercent ?: 0) / 100f,
        label = "runningProgress",
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(
            title = "Now Running",
            trailing = {
                Text(
                    text = "Auto-syncing...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        CodexCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = thread.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = thread.snippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${thread.progressPercent ?: 0}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(min = 56.dp),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.End,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = thread.remainingLabel.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.StopCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Pause",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentThreadRow(
    thread: ThreadSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.ChatBubbleOutline,
                contentDescription = null,
                tint = when (thread.status) {
                    ThreadStatus.Running -> MaterialTheme.colorScheme.primary
                    ThreadStatus.NeedsReview -> Color(0xFFCD9128)
                    ThreadStatus.Failed -> MaterialTheme.colorScheme.error
                    ThreadStatus.Completed -> Color(0xFF2B9957)
                    ThreadStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thread.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = thread.snippet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = thread.timeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
