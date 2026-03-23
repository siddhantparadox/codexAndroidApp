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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.metaText
import dev.codex.mobile.core.designsystem.theme.screenTitle
import dev.codex.mobile.core.designsystem.theme.sectionLabel
import dev.codex.mobile.core.designsystem.theme.supportingText
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.util.relativeTimeLabel
import kotlinx.coroutines.delay

@Composable
fun ThreadsScreen(
    onOpenThread: (String) -> Unit,
    viewModel: ThreadsViewModel = viewModel(
        factory = ThreadsViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    ThreadsScreenContent(
        uiState = uiState,
        onRefresh = viewModel::refreshThreads,
        onQueryChanged = viewModel::onQueryChanged,
        onFilterSelected = viewModel::onFilterSelected,
        onOpenCreateThreadPicker = {
            threadCwdQuery = ""
            createThreadErrorMessage = null
            isCreatingThread = false
            isThreadCwdPickerVisible = true
        },
        onOpenThread = onOpenThread,
    )

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadsScreenContent(
    uiState: ThreadsUiState,
    onRefresh: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onFilterSelected: (ThreadFilter) -> Unit,
    onOpenCreateThreadPicker: () -> Unit,
    onOpenThread: (String) -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val listState = rememberLazyListState()
    var collapsedSectionKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var expandedSectionKeys by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val availableSectionKeys: Set<String> = remember(uiState.folderSections) {
        uiState.folderSections.map(ThreadFolderSection::key).toSet()
    }
    val collapsedSectionKeySet: Set<String> = remember(collapsedSectionKeys) {
        collapsedSectionKeys.toSet()
    }
    val expandedSectionKeySet: Set<String> = remember(expandedSectionKeys) {
        expandedSectionKeys.toSet()
    }
    val listItems: List<ThreadsListItem> = remember(
        uiState.folderSections,
        uiState.unreadResultDigests,
        collapsedSectionKeySet,
        expandedSectionKeySet,
    ) {
        buildThreadFolderListItems(
            sections = uiState.folderSections,
            unreadResultDigests = uiState.unreadResultDigests,
            collapsedSectionKeys = collapsedSectionKeySet,
            expandedSectionKeys = expandedSectionKeySet,
        )
    }

    LaunchedEffect(availableSectionKeys) {
        collapsedSectionKeys = collapsedSectionKeys.filter { key -> key in availableSectionKeys }
        expandedSectionKeys = expandedSectionKeys.filter { key -> key in availableSectionKeys }
    }

    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        state = pullToRefreshState,
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = CodexSpacing.screenBottom),
            state = listState,
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
                                onClick = onRefresh,
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
                                .clickable(
                                    enabled = uiState.canCreateThread,
                                    onClick = onOpenCreateThreadPicker,
                                ),
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
                        onValueChange = onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                            )
                        },
                        placeholder = {
                            Text("Search thread, preview, or directory")
                        },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.listGap),
                    ) {
                        ThreadFilter.entries.forEach { filter ->
                            val selected: Boolean = filter == uiState.selectedFilter
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = CircleShape,
                                    )
                                    .clickable { onFilterSelected(filter) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = filter.label,
                                    style = MaterialTheme.typography.sectionLabel,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (!uiState.canCreateThread) {
                        Text(
                            text = uiState.createThreadUnavailableMessage,
                            style = MaterialTheme.typography.supportingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (uiState.folderSections.isEmpty()) {
                item {
                    ThreadsEmptyState(message = threadsEmptyMessage(uiState))
                }
            } else {
                items(
                    items = listItems,
                    key = { item -> item.key },
                    contentType = { item -> item.contentType },
                ) { item ->
                    when (item) {
                        is ThreadFolderHeaderItem -> {
                            ThreadFolderHeaderRow(
                                section = item.section,
                                expanded = item.isExpanded,
                                onClick = {
                                    collapsedSectionKeys = toggleKeyMembership(
                                        values = collapsedSectionKeys,
                                        key = item.section.key,
                                    )
                                },
                            )
                        }

                        is ThreadFolderThreadItem -> {
                            ThreadFolderThreadRow(
                                thread = item.thread,
                                resultDigest = item.resultDigest,
                                modifier = Modifier.padding(
                                    start = CodexSpacing.screenHorizontal + 30.dp,
                                    top = 1.dp,
                                    end = CodexSpacing.screenHorizontal,
                                    bottom = 1.dp,
                                ),
                                onClick = { onOpenThread(item.thread.id) },
                            )
                        }

                        is ThreadFolderShowMoreItem -> {
                            ThreadFolderShowMoreRow(
                                hiddenThreadCount = item.hiddenThreadCount,
                                sectionKey = item.sectionKey,
                                modifier = Modifier.padding(
                                    start = CodexSpacing.screenHorizontal + 40.dp,
                                    end = CodexSpacing.screenHorizontal,
                                    bottom = 4.dp,
                                ),
                                onClick = {
                                    expandedSectionKeys = addUniqueKey(
                                        values = expandedSectionKeys,
                                        key = item.sectionKey,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
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

private fun threadsSyncStatus(uiState: ThreadsUiState): String = when {
    uiState.isRefreshing -> "Syncing threads…"
    uiState.refreshErrorMessage != null -> uiState.refreshErrorMessage
    uiState.connectionPhase == ConnectionPhase.Reconnecting -> "Reconnecting to desktop…"
    uiState.lastRefreshAtEpochSeconds != null -> "Updated ${relativeTimeLabel(uiState.lastRefreshAtEpochSeconds)}"
    uiState.canRefresh -> "Live sync on"
    else -> "Connect to refresh threads"
}

@Composable
private fun threadsSyncStatusColor(uiState: ThreadsUiState): Color = when {
    uiState.isRefreshing -> MaterialTheme.colorScheme.primary
    uiState.refreshErrorMessage != null -> MaterialTheme.colorScheme.error
    uiState.connectionPhase == ConnectionPhase.Reconnecting -> Color(0xFFD59734)
    uiState.canRefresh -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun threadsEmptyMessage(uiState: ThreadsUiState): String = when {
    uiState.query.isNotBlank() -> "Try a different search query."
    uiState.selectedFilter != ThreadFilter.All -> "Try a different filter."
    uiState.canRefresh -> "Pull to refresh or start a new thread."
    else -> "Connect to your desktop to load threads."
}

private fun toggleKeyMembership(
    values: List<String>,
    key: String,
): List<String> = if (key in values) {
    values - key
} else {
    values + key
}

private fun addUniqueKey(
    values: List<String>,
    key: String,
): List<String> = if (key in values) {
    values
} else {
    values + key
}

private val ThreadFilter.label: String
    get() = when (this) {
        ThreadFilter.All -> "All Threads"
        ThreadFilter.Active -> "Active"
        ThreadFilter.WaitingOnApproval -> "Needs Approval"
        ThreadFilter.SystemError -> "System Error"
    }
