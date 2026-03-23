package dev.codex.mobile.feature.threaddetail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.util.AppLog
import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.ThreadDynamicToolRequest
import dev.codex.mobile.core.model.ThreadDynamicToolResponse
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.displayTitle
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.isWaitingOnUserInput
import dev.codex.mobile.core.model.displayMetaLabel
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val isUserDragging: Boolean by listState.interactionSource.collectIsDraggedAsState()
    val isImeVisible: Boolean = WindowInsets.isImeVisible
    val density = LocalDensity.current
    val navigationBarBottomPadding = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    var hasInitialScroll by remember(detail?.summary?.id) { mutableStateOf(false) }
    var followMode by remember(detail?.summary?.id) { mutableStateOf(true) }
    var scrollToLatestRequestId by remember(detail?.summary?.id) { mutableStateOf(0) }
    var handledScrollToLatestRequestId by remember(detail?.summary?.id) { mutableStateOf(0) }
    var composerBarContentHeightPx by remember(detail?.summary?.id) { mutableStateOf(0) }
    var composerSheetContent by remember(detail?.summary?.id) {
        mutableStateOf<ThreadComposerSheetContent?>(null)
    }
    var selectedTechnicalItem by remember(detail?.summary?.id) {
        mutableStateOf<ThreadItem?>(null)
    }
    var reviewedDiff by remember(detail?.summary?.id) {
        mutableStateOf<FileChangeEntry?>(null)
    }
    var pendingPhotoPickerRequestId by remember(detail?.summary?.id) {
        mutableStateOf<String?>(null)
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val pendingRequestId = pendingPhotoPickerRequestId
        pendingPhotoPickerRequestId = null
        if (pendingRequestId != null) {
            if (uri != null) {
                viewModel.respondToDynamicTool(
                    requestId = pendingRequestId,
                    response = ThreadDynamicToolResponse.PickPhotoSelected(uri.toString()),
                )
            } else {
                viewModel.respondToDynamicTool(
                    requestId = pendingRequestId,
                    response = ThreadDynamicToolResponse.Cancel,
                )
            }
        } else {
            uri?.let { pickedUri ->
                viewModel.attachImage(pickedUri.toString())
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (detail == null) {
            if (uiState.isInitialLoadInFlight) {
                LoadingThreadState(
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EmptyThreadState(
                    onNavigateBack = onNavigateBack,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            val visibleItems = remember(detail.items) {
                detail.items.filter(::shouldRenderTranscriptItem)
            }
            val showPendingAgentPlaceholder = remember(
                detail.summary.status,
                visibleItems,
                uiState.activeItemIds,
                uiState.approvals,
                uiState.userInputRequests,
                uiState.dynamicToolRequests,
            ) {
                shouldShowPendingAgentPlaceholder(
                    status = detail.summary.status,
                    items = visibleItems,
                    activeItemIds = uiState.activeItemIds,
                    approvals = uiState.approvals,
                    userInputRequests = uiState.userInputRequests,
                    dynamicToolRequests = uiState.dynamicToolRequests,
                )
            }
            val transcriptRows = remember(
                visibleItems,
                uiState.approvals,
                uiState.userInputRequests,
                uiState.dynamicToolRequests,
                showPendingAgentPlaceholder,
            ) {
                buildTranscriptRows(
                    items = visibleItems,
                    approvals = uiState.approvals,
                    userInputRequests = uiState.userInputRequests,
                    dynamicToolRequests = uiState.dynamicToolRequests,
                    showPendingAgentPlaceholder = showPendingAgentPlaceholder,
                )
            }
            val waitingOnUnavailableApproval = remember(detail.summary.status, uiState.approvals) {
                detail.summary.status.isWaitingOnApproval && uiState.approvals.isEmpty()
            }
            val waitingOnUnavailableUserInput = remember(detail.summary.status, uiState.userInputRequests) {
                detail.summary.status.isWaitingOnUserInput && uiState.userInputRequests.isEmpty()
            }
            val activityRowCount = if (detail.activities.isEmpty()) 0 else 1
            val transcriptRowCount = if (transcriptRows.isEmpty()) 1 else transcriptRows.size
            val pendingRequestUnavailableRowCount =
                if (waitingOnUnavailableApproval || waitingOnUnavailableUserInput) 1 else 0
            val totalTranscriptRows =
                1 + activityRowCount + transcriptRowCount + pendingRequestUnavailableRowCount + 1
            val transcriptBottomPadding = if (composerBarContentHeightPx > 0) {
                with(density) { composerBarContentHeightPx.toDp() } +
                    navigationBarBottomPadding +
                    CodexSpacing.sectionGap
            } else {
                128.dp
            }
            val transcriptEffectsEnabled: Boolean = !listState.isScrollInProgress
            val isNearBottom: Boolean by remember(listState, totalTranscriptRows) {
                derivedStateOf { listState.isNearBottom(totalTranscriptRows) }
            }

            LaunchedEffect(detail.summary.id, isUserDragging, isNearBottom, hasInitialScroll) {
                if (!hasInitialScroll) return@LaunchedEffect
                when {
                    isUserDragging && followMode != isNearBottom -> {
                        followMode = isNearBottom
                        AppLog.action(
                            name = "thread_follow_mode",
                            detail = "thread=${detail.summary.id} enabled=$followMode source=drag",
                        )
                    }

                    !followMode && isNearBottom -> {
                        followMode = true
                        AppLog.action(
                            name = "thread_follow_mode",
                            detail = "thread=${detail.summary.id} enabled=true source=returned_to_bottom",
                        )
                    }
                }
            }

            LaunchedEffect(
                detail.summary.id,
                transcriptRows,
                isImeVisible,
                followMode,
                scrollToLatestRequestId,
            ) {
                if (totalTranscriptRows <= 0) return@LaunchedEffect
                val targetIndex = totalTranscriptRows - 1
                val hasExplicitScrollRequest: Boolean =
                    scrollToLatestRequestId != handledScrollToLatestRequestId
                val lastVisibleIndex: Int = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val bottomAnchorVisible: Boolean = listState.isItemFullyVisible(targetIndex)
                val shouldAutoScroll: Boolean = !hasInitialScroll || (
                    followMode && (
                        hasExplicitScrollRequest ||
                            lastVisibleIndex < targetIndex ||
                            !bottomAnchorVisible
                    )
                )

                if (!shouldAutoScroll) {
                    handledScrollToLatestRequestId = scrollToLatestRequestId
                    return@LaunchedEffect
                }

                val laidOutRowCount = if (listState.layoutInfo.totalItemsCount >= totalTranscriptRows) {
                    listState.layoutInfo.totalItemsCount
                } else {
                    snapshotFlow { listState.layoutInfo.totalItemsCount }
                        .first { itemCount -> itemCount >= totalTranscriptRows }
                }

                AppLog.action(
                    name = "thread_autoscroll_prepare",
                    detail = "thread=${detail.summary.id} rows=$totalTranscriptRows laidOut=$laidOutRowCount target=$targetIndex initial=$hasInitialScroll follow=$followMode ime=$isImeVisible request=$scrollToLatestRequestId bottomVisible=$bottomAnchorVisible",
                )
                if (!hasInitialScroll) {
                    listState.scrollToItem(targetIndex)
                    hasInitialScroll = true
                    followMode = true
                } else if (followMode) {
                    listState.animateScrollToItem(targetIndex)
                }
                handledScrollToLatestRequestId = scrollToLatestRequestId
                val visibleItems = listState.layoutInfo.visibleItemsInfo
                AppLog.action(
                    name = "thread_autoscroll_result",
                    detail = "thread=${detail.summary.id} first=${listState.firstVisibleItemIndex} last=${visibleItems.lastOrNull()?.index ?: -1} target=$targetIndex total=${listState.layoutInfo.totalItemsCount} follow=$followMode",
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = ThreadTranscriptHorizontalPadding,
                    top = CodexSpacing.screenTop,
                    end = ThreadTranscriptHorizontalPadding,
                    bottom = transcriptBottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
            ) {
                item(
                    key = "thread-header",
                    contentType = "thread-header",
                ) {
                    ThreadDetailHeader(
                        summary = detail.summary,
                        onNavigateBack = onNavigateBack,
                    )
                }
                if (detail.activities.isNotEmpty()) {
                    item(
                        key = "thread-activity",
                        contentType = "thread-activity",
                    ) {
                        ThreadActivityPanel(activities = detail.activities)
                    }
                }
                if (transcriptRows.isEmpty()) {
                    item(
                        key = "empty-transcript",
                        contentType = "empty-transcript",
                    ) {
                        EmptyTranscriptState()
                    }
                } else {
                    items(
                        items = transcriptRows,
                        key = { row -> row.id },
                        contentType = { row -> transcriptRowContentType(row) },
                    ) { row ->
                        ThreadTranscriptRowView(
                            row = row,
                            activeItemIds = uiState.activeItemIds,
                            selectionEnabled = transcriptEffectsEnabled,
                            animationsEnabled = transcriptEffectsEnabled,
                            autoRevealExpandedContent = followMode,
                            onDecision = viewModel::resolveApproval,
                            onSubmitUserInput = viewModel::respondToUserInput,
                            onChooseDynamicTool = { request ->
                                pendingPhotoPickerRequestId = request.requestId
                                photoPicker.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                            onCancelDynamicTool = { requestId ->
                                viewModel.respondToDynamicTool(
                                    requestId = requestId,
                                    response = ThreadDynamicToolResponse.Cancel,
                                )
                            },
                            onOpenTechnicalItemDetail = { item ->
                                selectedTechnicalItem = item
                            },
                        )
                    }
                }
                if (waitingOnUnavailableApproval || waitingOnUnavailableUserInput) {
                    item(
                        key = "pending-request-unavailable",
                        contentType = "pending-request-unavailable",
                    ) {
                        PendingRequestUnavailableCard(
                            waitingOnApproval = waitingOnUnavailableApproval,
                            waitingOnUserInput = waitingOnUnavailableUserInput,
                        )
                    }
                }
                item(key = "thread-bottom-anchor") {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(1.dp))
                }
            }
        }

        ThreadComposerBar(
            value = uiState.draft,
            onValueChange = { nextValue ->
                val opensSkillPicker = nextValue.endsWith("$") && !uiState.draft.endsWith("$")
                viewModel.onDraftChanged(nextValue)
                if (opensSkillPicker) {
                    composerSheetContent = ThreadComposerSheetContent.Skill
                }
            },
            onSend = {
                followMode = true
                scrollToLatestRequestId += 1
                viewModel.sendReply()
            },
            onInterrupt = viewModel::interruptThread,
            onModelClick = {
                composerSheetContent = ThreadComposerSheetContent.Model
            },
            onEffortClick = {
                composerSheetContent = ThreadComposerSheetContent.Effort
            },
            onMoreClick = {
                composerSheetContent = ThreadComposerSheetContent.QuickActions
            },
            onClearSkill = viewModel::clearSkill,
            onClearImage = viewModel::clearImage,
            onClearPermission = viewModel::clearSandboxMode,
            modelLabel = uiState.selectedModel?.displayName ?: "Model",
            effortLabel = uiState.selectedEffort.displayLabel(),
            selectedSkillLabel = uiState.selectedSkill?.let { "$${it.name}" },
            imageLabel = uiState.selectedImage?.label,
            permissionLabel = uiState.selectedSandboxMode.tokenLabel(),
            canChangeTurnSettings = !uiState.canInterrupt,
            canInterrupt = uiState.canInterrupt,
            isInterrupting = uiState.isInterrupting,
            sendEnabled = uiState.sendEnabled,
            onContentHeightChanged = { height -> composerBarContentHeightPx = height },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    val visibleSheetContent = composerSheetContent
    if (visibleSheetContent != null) {
        ModalBottomSheet(
            onDismissRequest = { composerSheetContent = null },
        ) {
            ThreadComposerSheetContentView(
                content = visibleSheetContent,
                uiState = uiState,
                onShowModels = { composerSheetContent = ThreadComposerSheetContent.Model },
                onShowEfforts = { composerSheetContent = ThreadComposerSheetContent.Effort },
                onShowPersonality = { composerSheetContent = ThreadComposerSheetContent.Personality },
                onShowPermissions = { composerSheetContent = ThreadComposerSheetContent.Permissions },
                onShowSkills = { composerSheetContent = ThreadComposerSheetContent.Skill },
                onSelectModel = { modelId ->
                    viewModel.selectModel(modelId)
                    composerSheetContent = null
                },
                onSelectEffort = { effort ->
                    viewModel.selectEffort(effort)
                    composerSheetContent = null
                },
                onSelectPersonality = { personality ->
                    viewModel.selectPersonality(personality)
                    composerSheetContent = null
                },
                onSelectSandboxMode = { sandboxMode ->
                    viewModel.selectSandboxMode(sandboxMode)
                    composerSheetContent = null
                },
                onSelectSkill = { skill ->
                    viewModel.selectSkill(skill)
                    composerSheetContent = null
                },
                onPickPhoto = {
                    composerSheetContent = null
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )
        }
    }

    val visibleTechnicalItem: ThreadItem? = selectedTechnicalItem
    if (visibleTechnicalItem != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedTechnicalItem = null },
        ) {
            ThreadTechnicalItemDetailSheetContent(
                item = visibleTechnicalItem,
                activeItemIds = uiState.activeItemIds,
                onReviewDiff = { change ->
                    selectedTechnicalItem = null
                    reviewedDiff = change
                },
            )
        }
    }

    val visibleReviewedDiff: FileChangeEntry? = reviewedDiff
    if (visibleReviewedDiff != null) {
        ModalBottomSheet(
            onDismissRequest = { reviewedDiff = null },
        ) {
            ThreadDiffViewerContent(change = visibleReviewedDiff)
        }
    }
}

@Composable
private fun LoadingThreadState(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(
            horizontal = CodexSpacing.screenHorizontal,
            vertical = CodexSpacing.screenTop,
        ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.width(18.dp).height(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = "Loading thread…",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            Text(
                text = "Fetching the latest transcript, tool calls, and approvals from the host.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyThreadState(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(
            horizontal = CodexSpacing.screenHorizontal,
            vertical = CodexSpacing.screenTop,
        ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
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
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
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
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
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
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(CodexSpacing.sectionGap))
        StatusChip(
            label = threadStatusLabel(summary.status),
            color = threadStatusColor(summary.status),
            pulsingDot = summary.status.isActive &&
                !summary.status.isWaitingOnApproval &&
                !summary.status.isWaitingOnUserInput,
        )
    }
}

private fun threadTitle(summary: ThreadSummary): String = summary.displayTitle()

private fun threadMetaLabel(summary: ThreadSummary): String = summary.displayMetaLabel()

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

private fun androidx.compose.foundation.lazy.LazyListState.isNearBottom(totalRows: Int): Boolean {
    if (totalRows <= 1) return true
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisibleIndex >= totalRows - 3
}

private fun androidx.compose.foundation.lazy.LazyListState.isItemFullyVisible(index: Int): Boolean {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { visibleItem -> visibleItem.index == index } ?: return false
    return item.offset >= layoutInfo.viewportStartOffset &&
        item.offset + item.size <= layoutInfo.viewportEndOffset
}

private fun shouldRenderTranscriptItem(item: ThreadItem): Boolean = when (item) {
    is ThreadItem.Reasoning -> item.summary.isNotBlank() || item.contentText.isNotBlank()
    else -> true
}

private fun transcriptRowContentType(row: TranscriptRow): String = when (row) {
    is TranscriptRow.UserMessage -> "user-message"
    is TranscriptRow.AgentMessage -> "agent-message"
    is TranscriptRow.TechnicalStrip -> "technical-strip"
    is TranscriptRow.ApprovalCard -> "approval-card"
    is TranscriptRow.UserInputRequestCard -> "user-input-request-card"
    is TranscriptRow.DynamicToolRequestCard -> "dynamic-tool-request-card"
    TranscriptRow.PendingAgentPlaceholder -> "pending-agent-placeholder"
}

private fun dev.codex.mobile.core.model.ComposerReasoningEffort.displayLabel(): String = when (this) {
    dev.codex.mobile.core.model.ComposerReasoningEffort.None -> "None"
    dev.codex.mobile.core.model.ComposerReasoningEffort.Minimal -> "Minimal"
    dev.codex.mobile.core.model.ComposerReasoningEffort.Low -> "Low"
    dev.codex.mobile.core.model.ComposerReasoningEffort.Medium -> "Medium"
    dev.codex.mobile.core.model.ComposerReasoningEffort.High -> "High"
    dev.codex.mobile.core.model.ComposerReasoningEffort.XHigh -> "XHigh"
}

private fun dev.codex.mobile.core.model.ComposerSandboxMode.tokenLabel(): String? = when (this) {
    dev.codex.mobile.core.model.ComposerSandboxMode.Default -> null
    dev.codex.mobile.core.model.ComposerSandboxMode.ReadOnly -> "Read Only"
    dev.codex.mobile.core.model.ComposerSandboxMode.WorkspaceWrite -> "Workspace Write"
    dev.codex.mobile.core.model.ComposerSandboxMode.FullAccess -> "Full Access"
}

