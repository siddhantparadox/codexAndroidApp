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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderOpen
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
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.util.relativeTimeLabel

@Composable
fun ThreadsScreen(
    onCreateThread: () -> Unit,
    onOpenThread: (String) -> Unit,
    viewModel: ThreadsViewModel = viewModel(
        factory = ThreadsViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        state = rememberLazyListState(),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                            )
                            .clickable(onClick = onCreateThread),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Create thread",
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                                .padding(horizontal = 16.dp, vertical = 10.dp),
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
            }
        }
        item {
            SectionHeader(
                title = "Recent Activity",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        items(
            items = uiState.threads,
            key = { thread -> thread.id },
        ) { thread ->
            ThreadCard(
                thread = thread,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                    imageVector = threadStatusIcon(thread.status),
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
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = threadTitle(thread),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = thread.preview,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (thread.ephemeral) "Ephemeral thread" else "Stored thread metadata",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusChip(
                label = threadStatusLabel(thread.status),
                color = threadStatusColor(thread.status),
                pulsingDot = thread.status.type == ThreadStatusType.Active && !thread.status.isWaitingOnApproval,
            )
        }
    }
}

private fun threadTitle(thread: ThreadSummary): String = thread.name?.takeIf { it.isNotBlank() } ?: "Untitled thread"

private fun threadMetaLabel(thread: ThreadSummary): String = buildList {
    add(thread.modelProvider.uppercase())
    if (thread.ephemeral) add("EPHEMERAL")
}.joinToString(" • ")

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
