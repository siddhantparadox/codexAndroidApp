package dev.codex.mobile.feature.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.R
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.SectionHeader
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.AccountStatus
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.displayMetaLabel
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.isWaitingOnUserInput
import dev.codex.mobile.core.model.runtimeSettingsLabel
import dev.codex.mobile.core.model.summary
import dev.codex.mobile.core.model.workspaceFolderName
import dev.codex.mobile.core.util.relativeTimeLabel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenThreads: () -> Unit,
    onOpenHostConnection: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenUsageWrapped: () -> Unit,
    viewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isUsageSheetVisible by rememberSaveable { mutableStateOf(false) }
    var isAccountSheetVisible by rememberSaveable { mutableStateOf(false) }
    DashboardContent(
        uiState = uiState,
        onOpenThreads = onOpenThreads,
        onOpenHostConnection = onOpenHostConnection,
        onOpenThread = onOpenThread,
        onOpenUsage = {
            isUsageSheetVisible = true
            viewModel.refreshUsageWrapped()
        },
        onOpenAccount = { isAccountSheetVisible = true },
        modifier = Modifier.fillMaxSize(),
    )
    if (isUsageSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isUsageSheetVisible = false },
        ) {
            DashboardUsageSheet(
                usageSheet = uiState.usageSheet,
                onOpenWrapped = {
                    isUsageSheetVisible = false
                    onOpenUsageWrapped()
                },
            )
        }
    }
    if (isAccountSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isAccountSheetVisible = false },
        ) {
            DashboardAccountSheet(
                account = uiState.account,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DashboardUiState,
    onOpenThreads: () -> Unit,
    onOpenHostConnection: () -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenUsage: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .padding(
                start = CodexSpacing.screenHorizontal,
                top = CodexSpacing.topLevelHeaderGap,
                end = CodexSpacing.screenHorizontal,
                bottom = CodexSpacing.screenBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
    ) {
        DashboardHeader(
            onOpenUsage = onOpenUsage,
            onOpenAccount = onOpenAccount,
        )
        ConnectionStrip(
            activeHost = uiState.activeHost,
            connection = uiState.connection,
            onClick = onOpenHostConnection,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            ActiveThreadPanel(
                thread = uiState.activeThread,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.42f),
                onClick = onOpenThread,
            )
            RecentThreadsPanel(
                threads = uiState.recentThreads,
                onOpenThread = onOpenThread,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.58f),
            )
        }
        DashboardFooter(
            syncedThreadCount = uiState.syncedThreadCount,
            attentionCount = uiState.attentionCount,
            onOpenThreads = onOpenThreads,
        )
    }
}

@Composable
private fun DashboardHeader(
    onOpenUsage: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.icon),
                contentDescription = null,
                modifier = Modifier.size(42.dp),
            )
            Text(
                text = "Codex Mobile",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeaderActionChip(
                onClick = onOpenUsage,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueryStats,
                    contentDescription = "Usage",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HeaderActionChip(
                onClick = onOpenAccount,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
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
private fun HeaderActionChip(
    onClick: () -> Unit,
    containerColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ConnectionStrip(
    activeHost: dev.codex.mobile.core.model.HostProfile?,
    connection: dev.codex.mobile.core.model.ConnectionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (activeHost == null) {
            Text(
                text = "Connect your desktop",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.microGap))
            Text(
                text = "Scan the QR code from npx codexremote to start syncing threads and usage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            return@CodexCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (activeHost.kind == HostKind.Laptop) {
                        Icons.Rounded.LaptopMac
                    } else {
                        Icons.Rounded.Computer
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = activeHost.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildConnectionDetail(
                        address = activeHost.address,
                        port = activeHost.port,
                        connectionMessage = connection.message,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusChip(
                label = connectionLabel(connection.phase),
                color = connectionColor(connection.phase),
                pulsingDot = connection.phase == ConnectionPhase.Connected,
            )
        }
    }
}

@Composable
private fun ActiveThreadPanel(
    thread: ThreadSummary?,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (thread == null) {
        CodexCard(
            modifier = modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
            ) {
                Text(
                    text = "ACTIVE THREAD",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "No active turn",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Open Threads to resume a conversation or start a new one from the connected desktop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Usage stays in the header sheet while the dashboard focuses on active work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        return
    }

    CodexCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(thread.id) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
                ) {
                    Text(
                        text = "ACTIVE THREAD",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = threadTitle(thread),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                StatusChip(
                    label = threadStatusLabel(thread.status),
                    color = threadStatusColor(thread.status),
                    pulsingDot = !thread.status.isWaitingOnApproval && !thread.status.isWaitingOnUserInput,
                )
            }
            Text(
                text = buildThreadMetaLine(thread),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = thread.preview.ifBlank { "Open the thread to inspect the latest streamed output." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = when {
                    thread.status.isWaitingOnApproval -> "Approval is blocking the current turn."
                    thread.status.isWaitingOnUserInput -> "Codex is waiting for a short answer."
                    else -> "Open the thread to inspect diffs or interrupt it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentThreadsPanel(
    threads: List<ThreadSummary>,
    onOpenThread: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        SectionHeader(title = "Recent threads")
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        if (threads.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
            ) {
                Text(
                    text = "No other synced threads yet.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Recent work will appear here once the connected desktop syncs more threads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            return@CodexCard
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            threads.forEachIndexed { index, thread ->
                RecentThreadRow(
                    thread = thread,
                    onClick = { onOpenThread(thread.id) },
                )
                if (index != threads.lastIndex) {
                    Spacer(modifier = Modifier.height(CodexSpacing.microGap))
                }
            }
        }
    }
}

@Composable
private fun RecentThreadRow(
    thread: ThreadSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(threadStatusColor(thread.status)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
        ) {
            Text(
                text = threadTitle(thread),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildRecentThreadSubtitle(thread),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardUsageSheet(
    usageSheet: DashboardUsageSheetUiModel,
    onOpenWrapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CodexSpacing.screenHorizontal,
                end = CodexSpacing.screenHorizontal,
                bottom = CodexSpacing.screenBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Text(
            text = "Usage",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Live ChatGPT Codex quota plus wrapped local history from the connected desktop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WrappedUsageCallout(
            wrapped = usageSheet.wrapped,
            onClick = onOpenWrapped,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            UsageWindowCard(
                title = "5H",
                window = usageSheet.fiveHourWindow,
                modifier = Modifier.weight(1f),
            )
            UsageWindowCard(
                title = "7D",
                window = usageSheet.weeklyWindow,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WrappedUsageCallout(
    wrapped: DashboardWrappedPreviewUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.QueryStats,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "WRAPPED",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Open usage wrapped",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = buildWrappedCalloutSubtitle(wrapped),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DashboardAccountSheet(
    account: AccountState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CodexSpacing.screenHorizontal,
                end = CodexSpacing.screenHorizontal,
                bottom = CodexSpacing.screenBottom,
            ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
    ) {
        Text(
            text = "Account",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = account.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CodexCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            AccountField(
                label = "Status",
                value = accountStatusLabel(account),
            )
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            AccountField(
                label = "Plan",
                value = account.planType?.replaceFirstChar(Char::titlecase) ?: "Unavailable",
            )
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            AccountField(
                label = "Email",
                value = account.email ?: "Unavailable",
            )
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            AccountField(
                label = "OpenAI auth",
                value = if (account.requiresOpenaiAuth) "Required on desktop" else "Not required",
            )
        }
    }
}

@Composable
private fun AccountField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UsageWindowCard(
    title: String,
    window: DashboardUsageWindowUiModel,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        val progress: Float = ((window.usedPercent ?: 0).coerceIn(0, 100)) / 100f
        Text(
            text = "$title USAGE",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (window.windowDurationMins == null) {
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            Text(
                text = "Unavailable",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.microGap))
            Text(
                text = "Waiting for desktop quota sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            return@CodexCard
        }

        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
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
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        MetricLine(
            label = "Reset",
            value = resetWindowLabel(window.resetsAtEpochSeconds),
        )
    }
}

@Composable
private fun MetricLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DashboardFooter(
    syncedThreadCount: Int,
    attentionCount: Int,
    onOpenThreads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildFooterSummary(
                syncedThreadCount = syncedThreadCount,
                attentionCount = attentionCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "View all",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onOpenThreads),
        )
    }
}

private fun buildWrappedCalloutSubtitle(wrapped: DashboardWrappedPreviewUiModel): String = when {
    wrapped.approximateUsd != null && wrapped.sessionCount != null && wrapped.totalTokens != null -> {
        "${formatUsd(wrapped.approximateUsd)} approx. API cost  •  ${compactCount(wrapped.sessionCount.toLong())} sessions  •  ${compactCount(wrapped.totalTokens)} tokens"
    }

    wrapped.approximateUsd != null -> "${formatUsd(wrapped.approximateUsd)} approx. API-equivalent cost from local session history."
    wrapped.sessionCount != null && wrapped.totalTokens != null -> {
        "${compactCount(wrapped.sessionCount.toLong())} sessions  •  ${compactCount(wrapped.totalTokens)} tokens  •  Activity, streaks, and projects."
    }

    else -> "Open local history, streaks, projects, and the API-equivalent cost estimate."
}

private fun buildRecentThreadSubtitle(thread: ThreadSummary): String = listOfNotNull(
    threadStatusLabel(thread.status),
    thread.workspaceFolderName()?.takeIf { it.isNotBlank() },
    relativeTimeLabel(thread.updatedAtEpochSeconds),
).joinToString(separator = "  •  ")

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

private fun formatUsd(value: Double?): String {
    if (value == null) return "Unavailable"
    return NumberFormat.getCurrencyInstance(Locale.US).format(value)
}

private fun buildConnectionDetail(
    address: String,
    port: Int,
    connectionMessage: String?,
): String = listOfNotNull(
    "$address:$port",
    connectionMessage?.takeIf { it.isNotBlank() && it != "$address:$port" },
).joinToString(separator = "  •  ")

private fun buildThreadMetaLine(thread: ThreadSummary): String = listOfNotNull(
    thread.runtimeSettingsLabel(),
    thread.workspaceFolderName(),
    thread.gitBranch?.takeIf { it.isNotBlank() },
    thread.displayMetaLabel().takeIf { value ->
        value.isNotBlank() && value != thread.workspaceFolderName()
    },
).distinct().joinToString(separator = "  •  ")

private fun buildFooterSummary(
    syncedThreadCount: Int,
    attentionCount: Int,
): String = "$syncedThreadCount threads synced  •  $attentionCount need attention"

private fun resetWindowLabel(resetsAtEpochSeconds: Long?): String {
    if (resetsAtEpochSeconds == null) return "Unavailable"
    val remainingSeconds: Long = (resetsAtEpochSeconds - (System.currentTimeMillis() / 1_000)).coerceAtLeast(0)
    val remainingMinutes: Long = remainingSeconds / 60
    return when {
        remainingMinutes < 1L -> "Under 1 min"
        remainingMinutes < 60L -> "In ${remainingMinutes}m"
        remainingMinutes < 24L * 60L -> {
            val hours = remainingMinutes / 60L
            "In ${hours}h"
        }

        else -> {
            val days = remainingMinutes / (24L * 60L)
            "In ${days}d"
        }
    }
}

private fun threadTitle(thread: ThreadSummary): String = thread.name?.takeIf { it.isNotBlank() } ?: "Untitled thread"

private fun threadStatusLabel(status: ThreadStatus): String = when {
    status.isWaitingOnApproval -> "Needs Approval"
    status.isWaitingOnUserInput -> "Needs Input"
    status.type == ThreadStatusType.Active -> "Active"
    status.type == ThreadStatusType.SystemError -> "Error"
    status.type == ThreadStatusType.Idle -> "Idle"
    else -> "Stored"
}

@Composable
private fun threadStatusColor(status: ThreadStatus): Color = when {
    status.isWaitingOnApproval -> Color(0xFFD59734)
    status.isWaitingOnUserInput -> Color(0xFF3A7BD5)
    status.type == ThreadStatusType.Active -> MaterialTheme.colorScheme.primary
    status.type == ThreadStatusType.SystemError -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun connectionLabel(phase: ConnectionPhase): String = when (phase) {
    ConnectionPhase.Connected -> "Connected"
    ConnectionPhase.Connecting -> "Connecting"
    ConnectionPhase.Disconnected -> "Offline"
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

private fun accountStatusLabel(account: AccountState): String = when (account.status) {
    AccountStatus.Unknown -> "Unavailable"
    AccountStatus.RequiresLogin -> "Needs login"
    AccountStatus.ApiKey -> "API key"
    AccountStatus.ChatGpt -> "ChatGPT"
}





