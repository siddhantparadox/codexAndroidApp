package dev.codex.mobile.feature.threads

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LaptopMac
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import dev.codex.mobile.core.model.ThreadSourceKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.displayMetaLabel
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.runtimeSettingsLabel
import dev.codex.mobile.core.util.relativeTimeLabel

@Composable
fun ThreadsScreen(
    onOpenThread: (String) -> Unit,
    viewModel: ThreadsViewModel = viewModel(
        factory = ThreadsViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = CodexSpacing.screenBottom),
        state = rememberLazyListState(),
    ) {
        item {
            Column(
                modifier = Modifier.padding(
                    start = CodexSpacing.screenHorizontal,
                    top = CodexSpacing.topLevelHeaderGap,
                    end = CodexSpacing.screenHorizontal,
                    bottom = CodexSpacing.screenTop,
                ),
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Threads",
                            style = MaterialTheme.typography.headlineMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (uiState.canCreateThread) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                                shape = CircleShape,
                            )
                            .clickable(enabled = uiState.canCreateThread) {
                                viewModel.createThread(onOpenThread)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Create thread",
                            tint = if (uiState.canCreateThread) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                        )
                    },
                    placeholder = {
                        Text("Search thread name or preview")
                    },
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(CodexSpacing.listGap),
                ) {
                    ThreadFilter.entries.forEach { filter ->
                        val selected = filter == uiState.selectedFilter
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.onFilterSelected(filter) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = when (filter) {
                                    ThreadFilter.All -> "All Threads"
                                    ThreadFilter.Active -> "Active"
                                    ThreadFilter.WaitingOnApproval -> "Needs Approval"
                                    ThreadFilter.SystemError -> "System Error"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (!uiState.canCreateThread) {
                    Text(
                        text = "Connect to a desktop app-server before creating a new thread.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            SectionHeader(
                title = "Recent Activity",
                modifier = Modifier.padding(horizontal = CodexSpacing.screenHorizontal),
            )
        }
        items(
            items = uiState.threads,
            key = { thread -> thread.id },
        ) { thread ->
            ThreadCard(
                thread = thread,
                modifier = Modifier.padding(
                    horizontal = CodexSpacing.screenHorizontal,
                    vertical = CodexSpacing.microGap,
                ),
                onClick = { onOpenThread(thread.id) },
            )
        }
    }
}

@Composable
private fun ThreadCard(
    thread: ThreadSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    CodexCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = threadCardIcon(thread),
                    contentDescription = null,
                    tint = threadStatusColor(thread.status),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = threadMetaLabel(thread),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
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
        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        Text(
            text = threadTitle(thread),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = thread.preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val runtimeLabel = threadRuntimeLabel(thread)
            if (runtimeLabel != null) {
                Text(
                    text = runtimeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            StatusChip(
                label = threadStatusLabel(thread.status),
                color = threadStatusColor(thread.status),
                pulsingDot = thread.status.type == ThreadStatusType.Active && !thread.status.isWaitingOnApproval,
            )
        }
    }
}

private fun threadTitle(thread: ThreadSummary): String = thread.name?.takeIf { it.isNotBlank() } ?: "Untitled thread"

private fun threadMetaLabel(thread: ThreadSummary): String = thread.displayMetaLabel()

private fun threadRuntimeLabel(thread: ThreadSummary): String? = thread.runtimeSettingsLabel()

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

private fun threadCardIcon(thread: ThreadSummary) = when {
    thread.status.isWaitingOnApproval -> Icons.Rounded.FolderOpen
    thread.status.type == ThreadStatusType.Active -> Icons.Rounded.Refresh
    thread.status.type == ThreadStatusType.SystemError -> Icons.Rounded.Error
    else -> threadSourceIcon(thread.source)
}

private fun threadSourceIcon(source: ThreadSourceKind) = when (source) {
    ThreadSourceKind.Cli -> Icons.Rounded.Code
    ThreadSourceKind.VsCode -> Icons.Rounded.Code
    ThreadSourceKind.Exec -> Icons.Rounded.PlayArrow
    ThreadSourceKind.AppServer -> Icons.Rounded.LaptopMac
    ThreadSourceKind.SubAgent -> Icons.Rounded.AccountTree
    ThreadSourceKind.Unknown -> Icons.Rounded.ChatBubbleOutline
}

