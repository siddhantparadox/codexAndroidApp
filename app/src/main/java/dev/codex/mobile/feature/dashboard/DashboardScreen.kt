package dev.codex.mobile.feature.dashboard

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.SectionHeader
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.displayMetaLabel
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.summary
import dev.codex.mobile.core.util.relativeTimeLabel

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
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = CodexSpacing.screenHorizontal,
            top = CodexSpacing.topLevelHeaderGap,
            end = CodexSpacing.screenHorizontal,
            bottom = CodexSpacing.screenBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
    ) {
        item { DashboardHeader() }
        item {
            uiState.activeHost?.let { host ->
                ConnectionCard(
                    hostName = host.name,
                    address = host.address,
                    port = host.port,
                    hostKind = host.kind,
                    connectionPhase = uiState.connection.phase,
                    connectionMessage = uiState.connection.message,
                    account = uiState.account,
                    onClick = onOpenHostConnection,
                )
            }
        }
        item {
            uiState.activeThread?.let { activeThread ->
                ActiveThreadCard(
                    thread = activeThread,
                    onClick = { onOpenThread(activeThread.id) },
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
    address: String,
    port: Int,
    hostKind: HostKind,
    connectionPhase: ConnectionPhase,
    connectionMessage: String?,
    account: AccountState,
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
                horizontalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
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
                Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap)) {
                    Text(
                        text = "Trusted LAN Endpoint".uppercase(),
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
                label = connectionLabel(connectionPhase),
                color = connectionColor(connectionPhase),
                pulsingDot = connectionPhase == ConnectionPhase.Connected,
            )
        }
        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
        Text(
            text = "$address:$port",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        Text(
            text = account.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        connectionMessage?.takeIf { it != "$address:$port" }?.let { message ->
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActiveThreadCard(
    thread: ThreadSummary,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap)) {
        SectionHeader(
            title = "Active Thread",
            trailing = {
                StatusChip(
                    label = threadStatusLabel(thread.status),
                    color = threadStatusColor(thread.status),
                    pulsingDot = !thread.status.isWaitingOnApproval,
                )
            },
        )
        CodexCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        ) {
            Text(
                text = threadMetaLabel(thread),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            Text(
                text = threadTitle(thread),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
            Text(
                text = thread.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
            Text(
                text = if (thread.status.isWaitingOnApproval) {
                    "Approval is blocking the current turn."
                } else {
                    "Open the live thread to steer or interrupt the turn."
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
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
            .height(64.dp),
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
                imageVector = threadStatusIcon(thread.status),
                contentDescription = null,
                tint = threadStatusColor(thread.status),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = threadTitle(thread),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = thread.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = relativeTimeLabel(thread.updatedAtEpochSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun threadTitle(thread: ThreadSummary): String = thread.name?.takeIf { it.isNotBlank() } ?: "Untitled thread"

private fun threadMetaLabel(thread: ThreadSummary): String = thread.displayMetaLabel()

private fun threadStatusLabel(status: ThreadStatus): String = when {
    status.isWaitingOnApproval -> "Needs Approval"
    status.type == ThreadStatusType.Active -> "Active"
    status.type == ThreadStatusType.SystemError -> "Error"
    status.type == ThreadStatusType.Idle -> "Idle"
    else -> "Stored"
}

@Composable
private fun threadStatusColor(status: ThreadStatus): Color = when {
    status.isWaitingOnApproval -> Color(0xFFD59734)
    status.type == ThreadStatusType.Active -> MaterialTheme.colorScheme.primary
    status.type == ThreadStatusType.SystemError -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun threadStatusIcon(status: ThreadStatus) = when {
    status.isWaitingOnApproval -> Icons.Rounded.FolderOpen
    status.type == ThreadStatusType.Active -> Icons.Rounded.Refresh
    status.type == ThreadStatusType.SystemError -> Icons.Rounded.Error
    else -> Icons.Rounded.ChatBubbleOutline
}

private fun connectionLabel(phase: ConnectionPhase): String = when (phase) {
    ConnectionPhase.Connected -> "Connected"
    ConnectionPhase.Connecting -> "Connecting"
    ConnectionPhase.Disconnected -> "Disconnected"
    ConnectionPhase.Error -> "Error"
    ConnectionPhase.Idle -> "Idle"
}

@Composable
private fun connectionColor(phase: ConnectionPhase): Color = when (phase) {
    ConnectionPhase.Connected -> MaterialTheme.colorScheme.primary
    ConnectionPhase.Connecting -> Color(0xFFD59734)
    ConnectionPhase.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
    ConnectionPhase.Error -> MaterialTheme.colorScheme.error
    ConnectionPhase.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

