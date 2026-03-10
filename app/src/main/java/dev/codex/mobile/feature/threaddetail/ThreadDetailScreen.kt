package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isWaitingOnApproval

@Composable
fun ThreadDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThreadDetailViewModel = viewModel(
        factory = ThreadDetailViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = uiState.detail

    Box(modifier = Modifier.fillMaxSize()) {
        if (detail == null) {
            EmptyThreadState(
                onNavigateBack = onNavigateBack,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    top = 18.dp,
                    end = 20.dp,
                    bottom = 140.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    ThreadDetailHeader(
                        summary = detail.summary,
                        onNavigateBack = onNavigateBack,
                    )
                }
                items(
                    items = detail.items,
                    key = { item -> item.id },
                ) { entry ->
                    ThreadItemCard(entry = entry)
                }
            }
        }

        ComposerBar(
            value = uiState.draft,
            onValueChange = viewModel::onDraftChanged,
            onSend = viewModel::sendReply,
            onInterrupt = viewModel::interruptThread,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun EmptyThreadState(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Thread Detail",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        CodexCard {
            Text(
                text = "Thread data is unavailable.",
                style = MaterialTheme.typography.titleMedium,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Reconnect to the host or reopen the thread after the next sync.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThreadDetailHeader(
    summary: ThreadSummary,
    onNavigateBack: () -> Unit,
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
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = threadMetaLabel(summary),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = threadTitle(summary),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
        StatusChip(
            label = threadStatusLabel(summary.status),
            color = threadStatusColor(summary.status),
            pulsingDot = summary.status.isActive && !summary.status.isWaitingOnApproval,
        )
    }
}

@Composable
private fun ThreadItemCard(entry: ThreadItem) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = itemLabel(entry).uppercase() + " • ${entry.timestampLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (entry) {
            is ThreadItem.UserMessage -> TextCard(text = entry.text)
            is ThreadItem.AgentMessage -> TextCard(text = entry.text)
            is ThreadItem.Plan -> TextCard(text = entry.text)
            is ThreadItem.Reasoning -> ReasoningCard(entry)
            is ThreadItem.CommandExecution -> CommandExecutionCard(entry)
            is ThreadItem.FileChange -> FileChangeCard(entry.changes, entry.status)
        }
    }
}

private fun itemLabel(entry: ThreadItem): String = when (entry) {
    is ThreadItem.UserMessage -> "User Message"
    is ThreadItem.AgentMessage -> "Agent Message"
    is ThreadItem.Plan -> "Plan"
    is ThreadItem.Reasoning -> "Reasoning"
    is ThreadItem.CommandExecution -> "Command Execution"
    is ThreadItem.FileChange -> "File Change"
}

@Composable
private fun TextCard(text: String) {
    CodexCard {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ReasoningCard(entry: ThreadItem.Reasoning) {
    CodexCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = entry.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommandExecutionCard(entry: ThreadItem.CommandExecution) {
    CodexCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = entry.command,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            StatusChip(
                label = threadItemStatusLabel(entry.status),
                color = threadItemStatusColor(entry.status),
            )
        }
        entry.cwd?.let { cwd ->
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "CWD: $cwd",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.aggregatedOutput?.let { output ->
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = output,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
        entry.exitCode?.let { exitCode ->
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Exit code: $exitCode",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileChangeCard(
    changes: List<FileChangeEntry>,
    status: ThreadItemStatus,
) {
    CodexCard(contentPadding = PaddingValues(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${changes.size} proposed change(s)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            StatusChip(
                label = threadItemStatusLabel(status),
                color = threadItemStatusColor(status),
            )
        }
        changes.forEach { change ->
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = change.path,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${change.kind} • ${change.diff}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun threadTitle(summary: ThreadSummary): String = summary.name?.takeIf { it.isNotBlank() } ?: "Untitled thread"

private fun threadMetaLabel(summary: ThreadSummary): String = buildList {
    add(summary.modelProvider.uppercase())
    if (summary.ephemeral) add("EPHEMERAL")
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

private fun threadItemStatusLabel(status: ThreadItemStatus): String = when (status) {
    ThreadItemStatus.InProgress -> "In Progress"
    ThreadItemStatus.Completed -> "Completed"
    ThreadItemStatus.Failed -> "Failed"
    ThreadItemStatus.Declined -> "Declined"
}

@Composable
private fun threadItemStatusColor(status: ThreadItemStatus): Color = when (status) {
    ThreadItemStatus.InProgress -> MaterialTheme.colorScheme.primary
    ThreadItemStatus.Completed -> Color(0xFF2F9A58)
    ThreadItemStatus.Failed -> MaterialTheme.colorScheme.error
    ThreadItemStatus.Declined -> Color(0xFFD59734)
}

@Composable
private fun ComposerBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onInterrupt) {
            Icon(
                imageVector = Icons.Rounded.StopCircle,
                contentDescription = "Interrupt thread",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp),
            placeholder = {
                Text("Reply or steer the active turn...")
            },
            shape = RoundedCornerShape(28.dp),
            maxLines = 3,
        )
        IconButton(
            onClick = onSend,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "Send",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
