package dev.codex.mobile.feature.threaddetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BuildCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.codeBlock
import dev.codex.mobile.core.designsystem.theme.codeInline
import dev.codex.mobile.core.designsystem.theme.denseSupportingText
import dev.codex.mobile.core.designsystem.theme.sectionLabel
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.CollabAgentState
import dev.codex.mobile.core.model.CommandActionHint
import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.ToolContentItem
import dev.codex.mobile.core.model.UserInputContent
import dev.codex.mobile.core.model.displayLabel

private enum class TechnicalPillFamily {
    Plan,
    Reasoning,
    Command,
    Patch,
    Mcp,
    Tool,
    Web,
    Collab,
    Review,
    Image,
    System,
}

private data class TechnicalPillPalette(
    val accent: Color,
    val border: Color,
    val container: Color,
)

private data class TechnicalPillPresentation(
    val badge: String,
    val title: String,
    val preview: String,
    val icon: ImageVector,
    val family: TechnicalPillFamily,
    val status: ThreadItemStatus? = null,
    val statusLabel: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TechnicalPillStrip(
    items: List<ThreadItem>,
    approvals: List<ApprovalItem>,
    userInputRequests: List<ThreadUserInputRequest>,
    activeItemIds: Set<String>,
    autoRevealExpandedContent: Boolean,
    onDecision: (String, ApprovalDecision) -> Unit,
    onSubmitUserInput: (String, ThreadUserInputResponse) -> Unit,
    onReviewDiff: (FileChangeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val stripKey: String = remember(items) { items.joinToString(separator = "|") { item -> item.id } }
    var expandedItemId: String? by remember(stripKey) { mutableStateOf(null) }
    val selectedItem: ThreadItem? = items.firstOrNull { item -> item.id == expandedItemId }
    val selectedPresentation: TechnicalPillPresentation? = selectedItem?.let(::technicalPresentation)
    val selectedItemIsLive: Boolean = selectedItem?.isLive(activeItemIds) == true
    val detailBringIntoViewRequester: BringIntoViewRequester = remember(stripKey) { BringIntoViewRequester() }

    LaunchedEffect(selectedItem?.id, autoRevealExpandedContent) {
        if (selectedItem != null && autoRevealExpandedContent) {
            detailBringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
                    verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
                ) {
                    items.forEach { item ->
                        val presentation: TechnicalPillPresentation = technicalPresentation(item)
                        TechnicalPill(
                            presentation = presentation,
                            isLive = item.isLive(activeItemIds),
                            selected = item.id == expandedItemId,
                            onClick = {
                                expandedItemId = if (expandedItemId == item.id) null else item.id
                            },
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selectedItem != null && selectedPresentation != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    if (selectedItem != null && selectedPresentation != null) {
                        TechnicalPillDetailPanel(
                            item = selectedItem,
                            presentation = selectedPresentation,
                            isLive = selectedItemIsLive,
                            onReviewDiff = onReviewDiff,
                            modifier = Modifier.bringIntoViewRequester(detailBringIntoViewRequester),
                        )
                    }
                }
            }
        }

        userInputRequests.forEach { request ->
            InlineUserInputRequestCard(
                request = request,
                onSubmit = onSubmitUserInput,
            )
        }

        approvals.forEach { approval ->
            InlineApprovalCard(
                approval = approval,
                onDecision = onDecision,
            )
        }
    }
}

@Composable
private fun TechnicalPill(
    presentation: TechnicalPillPresentation,
    isLive: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette: TechnicalPillPalette = technicalPalette(presentation.family)
    val backgroundColor: Color = when {
        isLive -> liveContainerColor(
            accent = palette.accent,
            selected = selected,
        )
        selected -> palette.accent.copy(alpha = 0.18f)
        else -> palette.container
    }
    val dotColor: Color = if (presentation.status != null) {
        technicalStatusColor(presentation.status)
    } else {
        palette.accent
    }

    Row(
        modifier = Modifier
            .background(
                color = backgroundColor,
                shape = CircleShape,
            )
            .border(
                width = if (isLive || selected) 1.2.dp else 1.dp,
                color = when {
                    isLive -> palette.accent.copy(alpha = 0.55f)
                    selected -> palette.accent.copy(alpha = 0.55f)
                    else -> palette.border
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLive) {
            LivePulseDot(color = dotColor)
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = dotColor,
                        shape = CircleShape,
                    ),
            )
        }
        Text(
            text = presentation.badge,
            style = MaterialTheme.typography.codeInline,
            color = if (isLive || selected) palette.accent else palette.accent.copy(alpha = 0.92f),
            maxLines = 1,
        )
    }
}

@Composable
private fun TechnicalPillDetailPanel(
    item: ThreadItem,
    presentation: TechnicalPillPresentation,
    isLive: Boolean,
    onReviewDiff: (FileChangeEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette: TechnicalPillPalette = technicalPalette(presentation.family)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(width = 1.dp, color = palette.border),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            if (isLive) {
                LiveAccentLine(color = palette.accent)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(CodexSpacing.listGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = palette.accent.copy(alpha = 0.14f),
                                shape = CircleShape,
                            )
                            .padding(6.dp),
                    ) {
                        Icon(
                            imageVector = presentation.icon,
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = palette.accent.copy(alpha = 0.14f),
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = presentation.badge,
                                    style = MaterialTheme.typography.codeInline,
                                    color = palette.accent,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                )
                            }
                            when {
                                isLive -> LiveStatusBadge(
                                    label = loadingLabelForItem(item),
                                    color = palette.accent,
                                )

                                presentation.status != null -> StatusChip(
                                    label = technicalStatusLabel(presentation.status),
                                    color = technicalStatusColor(presentation.status),
                                )

                                presentation.statusLabel != null -> StatusChip(
                                    label = presentation.statusLabel,
                                    color = palette.accent,
                                )
                            }
                        }
                        Text(
                            text = presentation.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (presentation.preview.isNotBlank() && presentation.preview != presentation.title) {
                            Text(
                                text = presentation.preview,
                                style = MaterialTheme.typography.denseSupportingText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = palette.border)
            TechnicalItemDetail(
                item = item,
                isLive = isLive,
                onReviewDiff = onReviewDiff,
            )
        }
    }
}

@Composable
private fun TechnicalItemDetail(
    item: ThreadItem,
    isLive: Boolean,
    onReviewDiff: (FileChangeEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap)) {
        when (item) {
            is ThreadItem.Plan -> {
                ThreadRichText(
                    text = item.text,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            is ThreadItem.Reasoning -> {
                item.summarySections.ifEmpty { listOf(item.summary) }
                    .filter { section -> section.isNotBlank() }
                    .forEach { section ->
                        ThreadRichText(
                            text = section,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                if (item.contentText.isNotBlank()) {
                    TechnicalSectionTitle("Raw reasoning")
                    CodeBlock(
                        text = item.contentText,
                        maxLines = 14,
                        showLiveCaret = isLive,
                    )
                }
            }

            is ThreadItem.CommandExecution -> {
                item.cwd?.let { cwd -> MetadataLine("CWD", cwd) }
                if (item.commandActions.isNotEmpty()) {
                    WrapPills(item.commandActions.map(::commandActionLabel))
                }
                item.durationMs?.let { durationMs -> MetadataLine("Duration", "${durationMs} ms") }
                item.processId?.let { processId -> MetadataLine("Process", processId) }
                item.exitCode?.let { exitCode -> MetadataLine("Exit code", exitCode.toString()) }
                item.aggregatedOutput?.takeIf { output -> output.isNotBlank() }?.let { output ->
                    TechnicalSectionTitle("Output")
                    CodeBlock(
                        text = output,
                        showLiveCaret = isLive,
                    )
                }
                if (item.interactions.isNotEmpty()) {
                    TechnicalSectionTitle("Terminal input")
                    item.interactions.forEach { interaction ->
                        CodeBlock(
                            text = interaction,
                            maxLines = 8,
                            showLiveCaret = isLive,
                        )
                    }
                }
            }

            is ThreadItem.FileChange -> {
                item.changes.forEach { change ->
                    PatchFileSummaryCard(
                        change = change,
                        onReviewDiff = onReviewDiff,
                    )
                }
                item.toolOutput?.takeIf { output -> output.isNotBlank() }?.let { output ->
                    TechnicalSectionTitle("apply_patch output")
                    CodeBlock(
                        text = output,
                        showLiveCaret = isLive,
                    )
                }
            }

            is ThreadItem.McpToolCall -> {
                JsonSection(label = "Arguments", value = item.arguments)
                item.result?.let { result -> JsonSection(label = "Result", value = result) }
                item.errorMessage?.let { error -> MetadataLine("Error", error) }
                item.durationMs?.let { durationMs -> MetadataLine("Duration", "${durationMs} ms") }
                if (item.progressMessages.isNotEmpty()) {
                    TechnicalSectionTitle("Progress")
                    item.progressMessages.forEach { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.denseSupportingText,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            is ThreadItem.DynamicToolCall -> {
                JsonSection(label = "Arguments", value = item.arguments)
                item.success?.let { success -> MetadataLine("Success", success.toString()) }
                item.durationMs?.let { durationMs -> MetadataLine("Duration", "${durationMs} ms") }
                if (item.contentItems.isNotEmpty()) {
                    TechnicalSectionTitle("Returned content")
                    item.contentItems.forEach { contentItem ->
                        when (contentItem) {
                            is ToolContentItem.Text -> ThreadRichText(
                                text = contentItem.text,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                textStyle = MaterialTheme.typography.bodySmall,
                            )

                            is ToolContentItem.Image -> ThreadUserAttachment(
                                item = UserInputContent.Image(contentItem.imageUrl),
                                isUser = false,
                            )
                        }
                    }
                }
            }

            is ThreadItem.CollabToolCall -> {
                MetadataLine("Sender", item.senderThreadId)
                MetadataLine("Receivers", item.receiverThreadIds.joinToString())
                item.prompt?.let { prompt ->
                    ThreadRichText(
                        text = prompt,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                if (item.agentStates.isNotEmpty()) {
                    item.agentStates.forEach { state -> AgentStateRow(state) }
                }
            }

            is ThreadItem.WebSearch -> {
                MetadataLine("Query", item.query)
                item.actionLabel?.let { actionLabel -> MetadataLine("Action", actionLabel) }
            }

            is ThreadItem.ImageView -> {
                MetadataLine("Path", item.path)
            }

            is ThreadItem.ImageGeneration -> {
                MetadataLine("Result", item.result)
                item.revisedPrompt?.let { revisedPrompt ->
                    ThreadRichText(
                        text = revisedPrompt,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            is ThreadItem.ReviewMode -> {
                ThreadRichText(
                    text = item.review,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            is ThreadItem.ContextCompaction -> {
                Text(
                    text = "Codex compacted the thread history to continue the conversation.",
                    style = MaterialTheme.typography.denseSupportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            is ThreadItem.Unknown -> {
                CodeBlock(text = item.payload)
            }

            is ThreadItem.UserMessage,
            is ThreadItem.AgentMessage,
            -> Unit
        }
    }
}

@Composable
private fun TechnicalSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.sectionLabel,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MetadataLine(
    label: String,
    value: String,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.denseSupportingText,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun JsonSection(
    label: String,
    value: String,
) {
    TechnicalSectionTitle(label)
    CodeBlock(
        text = value,
        maxLines = 12,
    )
}

@Composable
private fun CodeBlock(
    text: String,
    maxLines: Int = Int.MAX_VALUE,
    showLiveCaret: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.codeBlock,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (showLiveCaret) {
                LiveStatusBadge(
                    label = "Streaming",
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun WrapPills(labels: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap)) {
        labels.forEach { label ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = CircleShape,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.codeInline,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun AgentStateRow(state: CollabAgentState) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "${state.threadId} • ${state.status}",
                style = MaterialTheme.typography.codeInline,
            )
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.denseSupportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun technicalPresentation(item: ThreadItem): TechnicalPillPresentation = when (item) {
    is ThreadItem.Plan -> TechnicalPillPresentation(
        badge = "PLAN",
        title = "Execution plan",
        preview = firstPreviewLine(item.text),
        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
        family = TechnicalPillFamily.Plan,
    )

    is ThreadItem.Reasoning -> TechnicalPillPresentation(
        badge = "REASON",
        title = "Model reasoning",
        preview = firstPreviewLine(item.summary.ifBlank { item.contentText }),
        icon = Icons.Rounded.Psychology,
        family = TechnicalPillFamily.Reasoning,
    )

    is ThreadItem.CommandExecution -> TechnicalPillPresentation(
        badge = "CMD",
        title = item.command,
        preview = commandPreview(item),
        icon = Icons.Rounded.Terminal,
        family = TechnicalPillFamily.Command,
        status = item.status,
    )

    is ThreadItem.FileChange -> TechnicalPillPresentation(
        badge = "PATCH",
        title = "${item.changes.size} file change(s)",
        preview = fileChangePreview(item),
        icon = Icons.Rounded.Description,
        family = TechnicalPillFamily.Patch,
        status = item.status,
    )

    is ThreadItem.McpToolCall -> TechnicalPillPresentation(
        badge = "MCP",
        title = "${item.server} / ${item.tool}",
        preview = mcpPreview(item),
        icon = Icons.Rounded.BuildCircle,
        family = TechnicalPillFamily.Mcp,
        status = item.status,
    )

    is ThreadItem.DynamicToolCall -> TechnicalPillPresentation(
        badge = "TOOL",
        title = item.tool,
        preview = dynamicToolPreview(item),
        icon = Icons.Rounded.Code,
        family = TechnicalPillFamily.Tool,
        status = item.status,
    )

    is ThreadItem.CollabToolCall -> TechnicalPillPresentation(
        badge = "COLLAB",
        title = item.tool,
        preview = collabPreview(item),
        icon = Icons.AutoMirrored.Rounded.CallSplit,
        family = TechnicalPillFamily.Collab,
        status = item.status,
    )

    is ThreadItem.WebSearch -> TechnicalPillPresentation(
        badge = "WEB",
        title = "Web search",
        preview = webPreview(item),
        icon = Icons.AutoMirrored.Rounded.ManageSearch,
        family = TechnicalPillFamily.Web,
    )

    is ThreadItem.ImageView -> TechnicalPillPresentation(
        badge = "IMAGE",
        title = "Image viewer",
        preview = firstPreviewLine(item.path),
        icon = Icons.Rounded.Visibility,
        family = TechnicalPillFamily.Image,
    )

    is ThreadItem.ImageGeneration -> TechnicalPillPresentation(
        badge = "IMAGE",
        title = "Image generation",
        preview = firstPreviewLine(item.revisedPrompt ?: item.result),
        icon = Icons.Rounded.Image,
        family = TechnicalPillFamily.Image,
        statusLabel = item.status,
    )

    is ThreadItem.ReviewMode -> TechnicalPillPresentation(
        badge = "REVIEW",
        title = if (item.entered) "Entered review mode" else "Exited review mode",
        preview = firstPreviewLine(item.review),
        icon = Icons.Rounded.Lightbulb,
        family = TechnicalPillFamily.Review,
    )

    is ThreadItem.ContextCompaction -> TechnicalPillPresentation(
        badge = "SYSTEM",
        title = "Conversation compacted",
        preview = "Codex compacted the thread history to continue the conversation.",
        icon = Icons.Rounded.AutoAwesome,
        family = TechnicalPillFamily.System,
    )

    is ThreadItem.Unknown -> TechnicalPillPresentation(
        badge = "SYSTEM",
        title = item.typeName.ifBlank { "Unknown item" },
        preview = firstPreviewLine(item.payload),
        icon = Icons.Rounded.Code,
        family = TechnicalPillFamily.System,
    )

    is ThreadItem.UserMessage,
    is ThreadItem.AgentMessage,
    -> error("Bubbles are rendered outside the technical strip.")
}

@Composable
private fun technicalPalette(family: TechnicalPillFamily): TechnicalPillPalette = when (family) {
    TechnicalPillFamily.Plan -> technicalPaletteFrom(Color(0xFF2B6FE8))
    TechnicalPillFamily.Reasoning -> technicalPaletteFrom(Color(0xFF8F6A20))
    TechnicalPillFamily.Command -> technicalPaletteFrom(Color(0xFF4B6375))
    TechnicalPillFamily.Patch -> technicalPaletteFrom(Color(0xFF2F9A58))
    TechnicalPillFamily.Mcp -> technicalPaletteFrom(Color(0xFF0F8B8D))
    TechnicalPillFamily.Tool -> technicalPaletteFrom(Color(0xFF5364E7))
    TechnicalPillFamily.Web -> technicalPaletteFrom(Color(0xFFD59734))
    TechnicalPillFamily.Collab -> technicalPaletteFrom(Color(0xFF1D9CB6))
    TechnicalPillFamily.Review -> technicalPaletteFrom(Color(0xFFCC6F2C))
    TechnicalPillFamily.Image -> technicalPaletteFrom(MaterialTheme.colorScheme.secondary)
    TechnicalPillFamily.System -> technicalPaletteFrom(MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun technicalPaletteFrom(accent: Color): TechnicalPillPalette = TechnicalPillPalette(
    accent = accent,
    border = accent.copy(alpha = 0.26f),
    container = accent.copy(alpha = 0.09f),
)

@Composable
private fun technicalStatusColor(status: ThreadItemStatus): Color = when (status) {
    ThreadItemStatus.InProgress -> MaterialTheme.colorScheme.primary
    ThreadItemStatus.Completed -> Color(0xFF2F9A58)
    ThreadItemStatus.Failed -> MaterialTheme.colorScheme.error
    ThreadItemStatus.Declined -> Color(0xFFD59734)
}

private fun technicalStatusLabel(status: ThreadItemStatus): String = when (status) {
    ThreadItemStatus.InProgress -> "In Progress"
    ThreadItemStatus.Completed -> "Completed"
    ThreadItemStatus.Failed -> "Failed"
    ThreadItemStatus.Declined -> "Declined"
}

private fun commandActionLabel(action: CommandActionHint): String = action.displayLabel()

private fun firstPreviewLine(text: String): String = text
    .lines()
    .map { line -> line.trim() }
    .firstOrNull { line -> line.isNotBlank() }
    ?: "No details"

private fun commandPreview(entry: ThreadItem.CommandExecution): String = when {
    !entry.aggregatedOutput.isNullOrBlank() -> firstPreviewLine(entry.aggregatedOutput)
    entry.exitCode != null -> "Exit code ${entry.exitCode}"
    entry.cwd != null -> entry.cwd
    entry.commandActions.isNotEmpty() -> commandActionLabel(entry.commandActions.first())
    else -> entry.command
}

private fun fileChangePreview(entry: ThreadItem.FileChange): String {
    val firstChange: FileChangeEntry = entry.changes.firstOrNull() ?: return "No file changes"
    return if (entry.changes.size == 1) {
        "${firstChange.kind} • ${firstChange.path}"
    } else {
        "${firstChange.path} +${entry.changes.size - 1} more"
    }
}

private fun mcpPreview(entry: ThreadItem.McpToolCall): String = when {
    entry.errorMessage != null -> entry.errorMessage
    entry.progressMessages.isNotEmpty() -> entry.progressMessages.last()
    entry.result != null -> firstPreviewLine(entry.result)
    else -> firstPreviewLine(entry.arguments)
}

private fun dynamicToolPreview(entry: ThreadItem.DynamicToolCall): String = when {
    entry.contentItems.isNotEmpty() -> when (val firstItem: ToolContentItem = entry.contentItems.first()) {
        is ToolContentItem.Text -> firstPreviewLine(firstItem.text)
        is ToolContentItem.Image -> if (firstItem.imageUrl.startsWith("data:image/")) {
            "Image attached"
        } else {
            firstItem.imageUrl
        }
    }

    entry.success != null -> if (entry.success) "Completed successfully" else "Marked unsuccessful"
    else -> firstPreviewLine(entry.arguments)
}

private fun collabPreview(entry: ThreadItem.CollabToolCall): String = when {
    !entry.prompt.isNullOrBlank() -> firstPreviewLine(entry.prompt)
    entry.receiverThreadIds.isNotEmpty() -> {
        val firstReceiver: String = entry.receiverThreadIds.first()
        if (entry.receiverThreadIds.size == 1) {
            "Receiver $firstReceiver"
        } else {
            "$firstReceiver +${entry.receiverThreadIds.size - 1} more receivers"
        }
    }

    else -> "Sender ${entry.senderThreadId}"
}

private fun webPreview(entry: ThreadItem.WebSearch): String = buildString {
    append(entry.query)
    entry.actionLabel?.takeIf { label -> label.isNotBlank() }?.let { label ->
        append(" • $label")
    }
}

private fun ThreadItem.isLive(activeItemIds: Set<String>): Boolean = when (this) {
    is ThreadItem.CommandExecution -> status == ThreadItemStatus.InProgress || id in activeItemIds
    is ThreadItem.FileChange -> status == ThreadItemStatus.InProgress || id in activeItemIds
    is ThreadItem.McpToolCall -> status == ThreadItemStatus.InProgress || id in activeItemIds
    is ThreadItem.DynamicToolCall -> status == ThreadItemStatus.InProgress || id in activeItemIds
    is ThreadItem.CollabToolCall -> status == ThreadItemStatus.InProgress || id in activeItemIds
    else -> id in activeItemIds
}

private fun loadingLabelForItem(item: ThreadItem): String = when (item) {
    is ThreadItem.Plan -> "Planning"
    is ThreadItem.Reasoning -> "Reasoning"
    is ThreadItem.CommandExecution -> "Running"
    is ThreadItem.FileChange -> "Patching"
    is ThreadItem.McpToolCall -> "Calling"
    is ThreadItem.DynamicToolCall -> "Running"
    is ThreadItem.CollabToolCall -> "Coordinating"
    is ThreadItem.WebSearch -> "Searching"
    is ThreadItem.ImageView -> "Opening"
    is ThreadItem.ImageGeneration -> "Generating"
    is ThreadItem.ReviewMode -> "Reviewing"
    is ThreadItem.ContextCompaction -> "Compacting"
    is ThreadItem.Unknown -> "Working"
    is ThreadItem.UserMessage,
    is ThreadItem.AgentMessage,
    -> "Working"
}

