package dev.codex.mobile.feature.threads

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.SectionHeader
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.denseSupportingText
import dev.codex.mobile.core.designsystem.theme.listItemTitle
import dev.codex.mobile.core.designsystem.theme.metaText
import dev.codex.mobile.core.designsystem.theme.screenTitle
import dev.codex.mobile.core.designsystem.theme.sectionLabel
import dev.codex.mobile.core.designsystem.theme.supportingText
import dev.codex.mobile.core.model.ThreadSourceKind
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadResultDigestKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.displayText
import dev.codex.mobile.core.model.displayMetaLabel
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.isWaitingOnUserInput
import dev.codex.mobile.core.model.runtimeSettingsLabel
import dev.codex.mobile.core.util.relativeTimeLabel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadsScreen(
    onOpenThread: (String) -> Unit,
    viewModel: ThreadsViewModel = viewModel(
        factory = ThreadsViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    var isThreadCwdPickerVisible by rememberSaveable { mutableStateOf(false) }
    var threadCwdQuery by rememberSaveable { mutableStateOf("") }
    var createThreadErrorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isCreatingThread by rememberSaveable { mutableStateOf(false) }
    val filteredCwdOptions = filterThreadCwdOptions(
        options = uiState.existingCwdOptions,
        query = threadCwdQuery,
    )

    LaunchedEffect(uiState.canRefresh) {
        if (!uiState.canRefresh) return@LaunchedEffect
        viewModel.refreshThreadsInBackground()
        while (true) {
            delay(5_000)
            viewModel.refreshThreadsInBackground()
        }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = viewModel::refreshThreads,
        state = pullToRefreshState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                            RefreshThreadsButton(
                                enabled = uiState.canRefresh,
                                isRefreshing = uiState.isRefreshing,
                                onClick = viewModel::refreshThreads,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
                            ) {
                                Text(
                                    text = "Threads",
                                    style = MaterialTheme.typography.screenTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = threadsSyncStatus(uiState),
                                    style = MaterialTheme.typography.metaText,
                                    color = threadsSyncStatusColor(uiState),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
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
                                    threadCwdQuery = ""
                                    createThreadErrorMessage = null
                                    isCreatingThread = false
                                    isThreadCwdPickerVisible = true
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
                                    style = MaterialTheme.typography.sectionLabel,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (!uiState.canCreateThread) {
                        Text(
                            text = "Connect to your desktop before creating a new thread.",
                            style = MaterialTheme.typography.supportingText,
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
                    resultDigest = uiState.unreadResultDigests[thread.id],
                    modifier = Modifier.padding(
                        horizontal = CodexSpacing.screenHorizontal,
                        vertical = CodexSpacing.microGap,
                    ),
                    onClick = { onOpenThread(thread.id) },
                )
            }
        }
    }

    if (isThreadCwdPickerVisible) {
        ThreadCwdPickerSheet(
            query = threadCwdQuery,
            options = filteredCwdOptions,
            hasAnyOptions = uiState.existingCwdOptions.isNotEmpty(),
            isCreatingThread = isCreatingThread,
            errorMessage = createThreadErrorMessage,
            onDismissRequest = {
                if (isCreatingThread) return@ThreadCwdPickerSheet
                isThreadCwdPickerVisible = false
                threadCwdQuery = ""
                createThreadErrorMessage = null
            },
            onQueryChanged = { value ->
                threadCwdQuery = value
                createThreadErrorMessage = null
            },
            onSelectCwd = { cwd ->
                if (isCreatingThread) return@ThreadCwdPickerSheet
                isCreatingThread = true
                createThreadErrorMessage = null
                viewModel.createThread(
                    cwd = cwd,
                    onThreadCreated = { threadId ->
                        isCreatingThread = false
                        isThreadCwdPickerVisible = false
                        threadCwdQuery = ""
                        createThreadErrorMessage = null
                        onOpenThread(threadId)
                    },
                    onThreadCreationFailed = { message ->
                        isCreatingThread = false
                        createThreadErrorMessage = message
                    },
                )
            },
        )
    }
}

@Composable
private fun RefreshThreadsButton(
    enabled: Boolean,
    isRefreshing: Boolean,
    onClick: () -> Unit,
) {
    val rotation: Float = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "threadRefresh")
        val animatedRotation by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
            ),
            label = "threadRefreshRotation",
        )
        animatedRotation
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
            )
            .clickable(enabled = enabled && !isRefreshing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = "Refresh threads",
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun ThreadCard(
    thread: ThreadSummary,
    resultDigest: ThreadResultDigest?,
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
                    style = MaterialTheme.typography.metaText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = relativeTimeLabel(thread.updatedAtEpochSeconds),
                style = MaterialTheme.typography.metaText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        Text(
            text = threadTitle(thread),
            style = MaterialTheme.typography.listItemTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.microGap))
        Text(
            text = thread.preview,
            style = MaterialTheme.typography.denseSupportingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (
            resultDigest != null &&
            thread.status.type != ThreadStatusType.Active &&
            !thread.status.isWaitingOnApproval &&
            !thread.status.isWaitingOnUserInput
        ) {
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            Text(
                text = resultDigest.displayText,
                style = MaterialTheme.typography.sectionLabel,
                color = threadResultDigestColor(resultDigest),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                    style = MaterialTheme.typography.metaText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }
            StatusChip(
                label = threadStatusLabel(thread.status),
                color = threadStatusColor(thread.status),
                pulsingDot = thread.status.type == ThreadStatusType.Active &&
                    !thread.status.isWaitingOnApproval &&
                    !thread.status.isWaitingOnUserInput,
            )
        }
    }
}

private fun threadTitle(thread: ThreadSummary): String = thread.name?.takeIf { it.isNotBlank() } ?: "Untitled thread"

private fun threadMetaLabel(thread: ThreadSummary): String = thread.displayMetaLabel()

private fun threadRuntimeLabel(thread: ThreadSummary): String? = thread.runtimeSettingsLabel()

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

@Composable
private fun threadResultDigestColor(resultDigest: ThreadResultDigest): Color = when (resultDigest.kind) {
    ThreadResultDigestKind.PatchReady -> MaterialTheme.colorScheme.primary
    ThreadResultDigestKind.ReplyReady -> MaterialTheme.colorScheme.primary
    ThreadResultDigestKind.Failed -> MaterialTheme.colorScheme.error
    ThreadResultDigestKind.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun threadCardIcon(thread: ThreadSummary) = when {
    thread.status.isWaitingOnApproval -> Icons.Rounded.FolderOpen
    thread.status.isWaitingOnUserInput -> Icons.Rounded.ChatBubbleOutline
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

private fun threadsSyncStatus(uiState: ThreadsUiState): String = when {
    uiState.isRefreshing -> "Syncing threads…"
    uiState.refreshErrorMessage != null -> uiState.refreshErrorMessage
    uiState.lastRefreshAtEpochSeconds != null -> "Updated ${relativeTimeLabel(uiState.lastRefreshAtEpochSeconds)}"
    uiState.canRefresh -> "Live sync on"
    else -> "Connect to refresh threads"
}

@Composable
private fun threadsSyncStatusColor(uiState: ThreadsUiState): Color = when {
    uiState.isRefreshing -> MaterialTheme.colorScheme.primary
    uiState.refreshErrorMessage != null -> MaterialTheme.colorScheme.error
    uiState.canRefresh -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}


