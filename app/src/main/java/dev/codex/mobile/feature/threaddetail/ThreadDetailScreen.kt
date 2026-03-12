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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
import dev.codex.mobile.core.util.AppLog
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isWaitingOnApproval
import kotlinx.coroutines.flow.first

@Composable
fun ThreadDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ThreadDetailViewModel = viewModel(
        factory = ThreadDetailViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = uiState.detail
    val listState = remember(detail?.summary?.id) { LazyListState() }
    var hasInitialScroll by remember(detail?.summary?.id) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (detail == null) {
            EmptyThreadState(
                onNavigateBack = onNavigateBack,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val visibleItems = detail.items.filter(::shouldRenderTranscriptItem)
            val transcriptRows = buildTranscriptRows(
                items = visibleItems,
                approvals = uiState.approvals,
            )
            val latestTranscriptEntryId = transcriptRows.lastOrNull()?.id
            val activityRowCount = if (detail.activities.isEmpty()) 0 else 1
            val transcriptRowCount = if (transcriptRows.isEmpty()) 1 else transcriptRows.size
            val totalTranscriptRows = 1 + activityRowCount + transcriptRowCount

            LaunchedEffect(detail.summary.id, latestTranscriptEntryId, totalTranscriptRows) {
                if (latestTranscriptEntryId == null || totalTranscriptRows <= 0) return@LaunchedEffect
                val laidOutRowCount = snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .first { itemCount -> itemCount >= totalTranscriptRows }
                val targetIndex = totalTranscriptRows - 1
                AppLog.action(
                    name = "thread_autoscroll_prepare",
                    detail = "thread=${detail.summary.id} rows=$totalTranscriptRows laidOut=$laidOutRowCount target=$targetIndex initial=$hasInitialScroll",
                )
                if (!hasInitialScroll) {
                    listState.scrollToItem(targetIndex)
                    hasInitialScroll = true
                } else if (listState.isNearBottom(totalTranscriptRows)) {
                    listState.animateScrollToItem(targetIndex)
                }
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                AppLog.action(
                    name = "thread_autoscroll_result",
                    detail = "thread=${detail.summary.id} first=${listState.firstVisibleItemIndex} last=${visibleItems.lastOrNull()?.index ?: -1} target=$targetIndex total=${listState.layoutInfo.totalItemsCount}",
                )
            }

            LazyColumn(
                state = listState,
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
                if (detail.activities.isNotEmpty()) {
                    item {
                        ThreadActivityPanel(activities = detail.activities)
                    }
                }
                if (visibleItems.isEmpty()) {
                    item {
                        EmptyTranscriptState()
                    }
                } else {
                    items(
                        items = transcriptRows,
                        key = { row -> row.id },
                    ) { row ->
                        ThreadTranscriptRowView(
                            row = row,
                            activeItemIds = uiState.activeItemIds,
                            onDecision = viewModel::resolveApproval,
                        )
                    }
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
private fun EmptyTranscriptState() {
    CodexCard {
        Text(
            text = "This thread has no transcript items yet.",
            style = MaterialTheme.typography.titleMedium,
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "New user messages, Codex replies, tool calls, and approvals will appear here as the thread runs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun androidx.compose.foundation.lazy.LazyListState.isNearBottom(totalRows: Int): Boolean {
    if (totalRows <= 1) return true
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisibleIndex >= totalRows - 3
}

private fun shouldRenderTranscriptItem(item: ThreadItem): Boolean = when (item) {
    is ThreadItem.Reasoning -> item.summary.isNotBlank() || item.contentText.isNotBlank()
    else -> true
}
