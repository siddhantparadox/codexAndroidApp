package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.StopCircle
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
import dev.codex.mobile.core.model.ExecutionKind
import dev.codex.mobile.core.model.ExecutionLine
import dev.codex.mobile.core.model.ReasoningStep
import dev.codex.mobile.core.model.TimelineEntry

@Composable
fun ThreadDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThreadDetailViewModel = viewModel(
        factory = ThreadDetailViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = uiState.detail ?: return

    Box(modifier = Modifier.fillMaxSize()) {
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
                    title = detail.summary.title,
                    isActive = detail.summary.progressPercent != null,
                    onNavigateBack = onNavigateBack,
                )
            }
            items(
                items = detail.timeline,
                key = { item -> item.id },
            ) { entry ->
                TimelineEntryCard(entry = entry)
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
private fun ThreadDetailHeader(
    title: String,
    isActive: Boolean,
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
            Column {
                Text(
                    text = "Task #842".uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                label = if (isActive) "Active" else "Paused",
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                pulsingDot = isActive,
            )
            IconButton(onClick = {}) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More actions",
                )
            }
        }
    }
}

@Composable
private fun TimelineEntryCard(entry: TimelineEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = when (entry) {
                is TimelineEntry.UserIntent -> "User Intent"
                is TimelineEntry.Reasoning -> "Codex Reasoning"
                is TimelineEntry.ExecutionLog -> "Execution Log"
                is TimelineEntry.ProposedChange -> "Proposed Changes"
                is TimelineEntry.Message -> entry.author
            }.uppercase() + " — ${entry.timestampLabel}",
            style = MaterialTheme.typography.labelSmall,
            color = if (entry is TimelineEntry.ProposedChange) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        when (entry) {
            is TimelineEntry.UserIntent -> Text(
                text = entry.text,
                style = MaterialTheme.typography.titleLarge,
            )
            is TimelineEntry.Reasoning -> ReasoningCard(entry)
            is TimelineEntry.ExecutionLog -> ExecutionLogCard(entry.lines)
            is TimelineEntry.ProposedChange -> ProposalCard(entry)
            is TimelineEntry.Message -> MessageCard(entry)
        }
    }
}

@Composable
private fun ReasoningCard(entry: TimelineEntry.Reasoning) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = entry.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        entry.steps.forEach { step ->
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = if (step.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (step.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = step.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.completed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExecutionLogCard(lines: List<ExecutionLine>) {
    CodexCard(contentPadding = PaddingValues(14.dp)) {
        lines.forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = line.lineNumber.toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = line.kind.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = when (line.kind) {
                        ExecutionKind.Run, ExecutionKind.Patch, ExecutionKind.Write ->
                            MaterialTheme.colorScheme.primary
                        ExecutionKind.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                        ExecutionKind.Warn -> Color(0xFFD8A041)
                    },
                )
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ProposalCard(entry: TimelineEntry.ProposedChange) {
    CodexCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(18.dp),
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProposalMetric(title = "Created", value = "${entry.createdCount} files")
            ProposalMetric(title = "Modified", value = "${entry.modifiedCount} lines")
        }
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryActionButton(label = "Approve & Commit")
        Spacer(modifier = Modifier.height(10.dp))
        SecondaryActionButton(label = "Review Diff")
    }
}

@Composable
private fun RowScope.ProposalMetric(
    title: String,
    value: String,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun MessageCard(entry: TimelineEntry.Message) {
    CodexCard {
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
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
                Text("Reply or provide more context...")
            },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = null,
                )
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

@Composable
private fun PrimaryActionButton(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun SecondaryActionButton(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
