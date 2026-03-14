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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.util.AppLog
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isWaitingOnApproval
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
    var hasInitialScroll by remember(detail?.summary?.id) { mutableStateOf(false) }
    var followMode by remember(detail?.summary?.id) { mutableStateOf(true) }
    var scrollToLatestRequestId by remember(detail?.summary?.id) { mutableStateOf(0) }
    var handledScrollToLatestRequestId by remember(detail?.summary?.id) { mutableStateOf(0) }
    var composerSheetContent by remember(detail?.summary?.id) {
        mutableStateOf<ThreadComposerSheetContent?>(null)
    }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { pickedUri ->
            viewModel.attachImage(pickedUri.toString())
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
            val visibleItems = detail.items.filter(::shouldRenderTranscriptItem)
            val transcriptRows = buildTranscriptRows(
                items = visibleItems,
                approvals = uiState.approvals,
            )
            val activityRowCount = if (detail.activities.isEmpty()) 0 else 1
            val transcriptRowCount = if (transcriptRows.isEmpty()) 1 else transcriptRows.size
            val totalTranscriptRows = 1 + activityRowCount + transcriptRowCount + 1
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
                uiState.activeItemIds,
                isImeVisible,
                followMode,
                scrollToLatestRequestId,
            ) {
                if (totalTranscriptRows <= 0) return@LaunchedEffect
                val laidOutRowCount = snapshotFlow { listState.layoutInfo.totalItemsCount }
                    .first { itemCount -> itemCount >= totalTranscriptRows }
                val targetIndex = totalTranscriptRows - 1
                AppLog.action(
                    name = "thread_autoscroll_prepare",
                    detail = "thread=${detail.summary.id} rows=$totalTranscriptRows laidOut=$laidOutRowCount target=$targetIndex initial=$hasInitialScroll follow=$followMode ime=$isImeVisible request=$scrollToLatestRequestId",
                )
                val hasExplicitScrollRequest: Boolean =
                    scrollToLatestRequestId != handledScrollToLatestRequestId
                if (!hasInitialScroll) {
                    listState.scrollToItem(targetIndex)
                    hasInitialScroll = true
                    followMode = true
                } else if (followMode) {
                    val lastVisibleIndex: Int = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    if (lastVisibleIndex < targetIndex || isImeVisible || hasExplicitScrollRequest) {
                        listState.animateScrollToItem(targetIndex)
                    }
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
                    start = CodexSpacing.screenHorizontal,
                    top = CodexSpacing.screenTop,
                    end = CodexSpacing.screenHorizontal,
                    bottom = 128.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
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
                            autoRevealExpandedContent = followMode,
                            onDecision = viewModel::resolveApproval,
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

private fun androidx.compose.foundation.lazy.LazyListState.isNearBottom(totalRows: Int): Boolean {
    if (totalRows <= 1) return true
    val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
    return lastVisibleIndex >= totalRows - 3
}

private fun shouldRenderTranscriptItem(item: ThreadItem): Boolean = when (item) {
    is ThreadItem.Reasoning -> item.summary.isNotBlank() || item.contentText.isNotBlank()
    else -> true
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

