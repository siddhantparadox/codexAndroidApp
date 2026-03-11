package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.CollabAgentState
import dev.codex.mobile.core.model.CommandActionHint
import dev.codex.mobile.core.model.ThreadActivity
import dev.codex.mobile.core.model.ThreadActivityEmphasis
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ToolContentItem
import dev.codex.mobile.core.model.UserInputContent
import dev.codex.mobile.core.model.label

@Composable
internal fun ThreadTranscriptEntry(
    entry: ThreadItem,
    approvals: List<ApprovalItem>,
    onDecision: (String, ApprovalDecision) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (entry) {
            is ThreadItem.UserMessage -> UserBubble(entry)
            is ThreadItem.AgentMessage -> AgentBubble(entry)
            is ThreadItem.Plan -> PlanCard(entry)
            is ThreadItem.Reasoning -> ReasoningCard(entry)
            is ThreadItem.CommandExecution -> CommandExecutionCard(entry)
            is ThreadItem.FileChange -> FileChangeCard(entry)
            is ThreadItem.McpToolCall -> McpToolCallCard(entry)
            is ThreadItem.DynamicToolCall -> DynamicToolCallCard(entry)
            is ThreadItem.CollabToolCall -> CollabToolCallCard(entry)
            is ThreadItem.WebSearch -> WebSearchCard(entry)
            is ThreadItem.ImageView -> ImageViewCard(entry)
            is ThreadItem.ImageGeneration -> ImageGenerationCard(entry)
            is ThreadItem.ReviewMode -> ReviewModeCard(entry)
            is ThreadItem.ContextCompaction -> ContextCompactionCard()
            is ThreadItem.Unknown -> UnknownItemCard(entry)
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
internal fun ThreadActivityPanel(
    activities: List<ThreadActivity>,
) {
    var expanded by remember { mutableStateOf(false) }

    CodexCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Thread Activity".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${activities.size} server event(s)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            StatusChip(
                label = if (expanded) "Hide" else "Show",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            activities.forEach { activity ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = activityBackground(activity.emphasis),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = activity.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = activityContent(activity.emphasis),
                        )
                        activity.detail?.let { detail ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InlineApprovalCard(
    approval: ApprovalItem,
    onDecision: (String, ApprovalDecision) -> Unit,
) {
    CodexCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
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
                Icon(
                    imageVector = if (approval.kind == ApprovalKind.CommandExecution) {
                        Icons.Rounded.Terminal
                    } else {
                        Icons.Rounded.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = approvalHeadline(approval),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    approval.reason?.let { reason ->
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            StatusChip(
                label = "Approval",
                color = Color(0xFFD59734),
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            approval.availableDecisions.forEach { decision ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = approvalDecisionBackground(decision),
                            shape = MaterialTheme.shapes.small,
                        )
                        .clickable { onDecision(approval.id, decision) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = decision.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = approvalDecisionContent(decision),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserBubble(entry: ThreadItem.UserMessage) {
    BubbleRow(
        isUser = true,
        label = "You",
        supportingLabel = null,
        bubbleColor = MaterialTheme.colorScheme.primary,
        textColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        val textParts = entry.content.filterIsInstance<UserInputContent.Text>()
        if (textParts.isEmpty()) {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            textParts.forEachIndexed { index, textPart ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = textPart.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        val nonTextParts = entry.content.filterNot { it is UserInputContent.Text }
        if (nonTextParts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            NonTextUserInputs(
                items = nonTextParts,
                isUser = true,
            )
        }
    }
}

@Composable
private fun AgentBubble(entry: ThreadItem.AgentMessage) {
    BubbleRow(
        isUser = false,
        label = "Codex",
        supportingLabel = when (entry.phase) {
            "commentary" -> "Working"
            "final_answer" -> "Final"
            else -> null
        },
        bubbleColor = if (entry.phase == "commentary") {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surface
        },
        textColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlanCard(entry: ThreadItem.Plan) {
    TranscriptCard(
        title = "Plan",
        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
    ) {
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReasoningCard(entry: ThreadItem.Reasoning) {
    var expanded by remember(entry.id) { mutableStateOf(false) }

    TranscriptCard(
        title = "Reasoning",
        icon = Icons.Rounded.Psychology,
        trailing = {
            StatusChip(
                label = if (expanded) "Hide" else "Expand",
                color = MaterialTheme.colorScheme.primary,
            )
        },
        onHeaderClick = { expanded = !expanded },
    ) {
        entry.summarySections.ifEmpty { listOf(entry.summary) }.forEach { section ->
            Text(
                text = section,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (expanded && entry.contentText.isNotBlank()) {
            Text(
                text = "Raw reasoning",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeBlock(entry.contentText)
        }
    }
}

@Composable
private fun CommandExecutionCard(entry: ThreadItem.CommandExecution) {
    var expanded by remember(entry.id) { mutableStateOf(false) }

    TranscriptCard(
        title = entry.command,
        icon = Icons.Rounded.Terminal,
        status = entry.status,
        onHeaderClick = { expanded = !expanded },
    ) {
        entry.cwd?.let { cwd ->
            MetadataLine("CWD", cwd)
            Spacer(modifier = Modifier.height(6.dp))
        }
        if (entry.commandActions.isNotEmpty()) {
            WrapPills(entry.commandActions.map(::commandActionLabel))
            Spacer(modifier = Modifier.height(10.dp))
        }
        entry.durationMs?.let { durationMs ->
            MetadataLine("Duration", "${durationMs} ms")
            Spacer(modifier = Modifier.height(6.dp))
        }
        entry.processId?.let { processId ->
            MetadataLine("Process", processId)
            Spacer(modifier = Modifier.height(6.dp))
        }
        entry.exitCode?.let { exitCode ->
            MetadataLine("Exit code", exitCode.toString())
            Spacer(modifier = Modifier.height(6.dp))
        }
        if (entry.aggregatedOutput?.isNotBlank() == true) {
            Text(
                text = if (expanded) "Output" else "Output preview",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeBlock(
                text = entry.aggregatedOutput,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (entry.interactions.isNotEmpty()) {
            Text(
                text = "Terminal input",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            entry.interactions.forEach { interaction ->
                CodeBlock(interaction, maxLines = 4)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun FileChangeCard(entry: ThreadItem.FileChange) {
    var expanded by remember(entry.id) { mutableStateOf(false) }

    TranscriptCard(
        title = "${entry.changes.size} file change(s)",
        icon = Icons.Rounded.Description,
        status = entry.status,
        onHeaderClick = { expanded = !expanded },
    ) {
        entry.changes.forEach { change ->
            Text(
                text = change.path,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = change.kind,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (change.diff.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                CodeBlock(
                    text = change.diff,
                    maxLines = if (expanded) Int.MAX_VALUE else 8,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        entry.toolOutput?.takeIf { it.isNotBlank() }?.let { output ->
            Text(
                text = "apply_patch output",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(6.dp))
            CodeBlock(
                text = output,
                maxLines = if (expanded) Int.MAX_VALUE else 6,
            )
        }
    }
}

@Composable
private fun McpToolCallCard(entry: ThreadItem.McpToolCall) {
    ToolCard(
        title = "${entry.server} / ${entry.tool}",
        icon = Icons.Rounded.BuildCircle,
        status = entry.status,
    ) {
        JsonSection(label = "Arguments", value = entry.arguments)
        entry.result?.let { result ->
            Spacer(modifier = Modifier.height(10.dp))
            JsonSection(label = "Result", value = result)
        }
        entry.errorMessage?.let { error ->
            Spacer(modifier = Modifier.height(10.dp))
            MetadataLine("Error", error)
        }
        if (entry.progressMessages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Progress",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            entry.progressMessages.forEach { message ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DynamicToolCallCard(entry: ThreadItem.DynamicToolCall) {
    ToolCard(
        title = entry.tool,
        icon = Icons.Rounded.Code,
        status = entry.status,
    ) {
        JsonSection(label = "Arguments", value = entry.arguments)
        entry.success?.let { success ->
            Spacer(modifier = Modifier.height(10.dp))
            MetadataLine("Success", success.toString())
        }
        if (entry.contentItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Returned content",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            entry.contentItems.forEach { item ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (item) {
                        is ToolContentItem.Text -> item.text
                        is ToolContentItem.Image -> item.imageUrl
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CollabToolCallCard(entry: ThreadItem.CollabToolCall) {
    ToolCard(
        title = entry.tool,
        icon = Icons.AutoMirrored.Rounded.CallSplit,
        status = entry.status,
    ) {
        MetadataLine("Sender", entry.senderThreadId)
        Spacer(modifier = Modifier.height(6.dp))
        MetadataLine("Receivers", entry.receiverThreadIds.joinToString())
        entry.prompt?.let { prompt ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (entry.agentStates.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            entry.agentStates.forEach { state ->
                AgentStateRow(state)
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun WebSearchCard(entry: ThreadItem.WebSearch) {
    ToolCard(
        title = entry.query,
        icon = Icons.AutoMirrored.Rounded.ManageSearch,
    ) {
        entry.actionLabel?.let { actionLabel ->
            MetadataLine("Action", actionLabel)
        }
    }
}

@Composable
private fun ImageViewCard(entry: ThreadItem.ImageView) {
    ToolCard(
        title = "Image viewer",
        icon = Icons.Rounded.Visibility,
    ) {
        MetadataLine("Path", entry.path)
    }
}

@Composable
private fun ImageGenerationCard(entry: ThreadItem.ImageGeneration) {
    ToolCard(
        title = "Image generation",
        icon = Icons.Rounded.Image,
        statusLabel = entry.status,
    ) {
        MetadataLine("Result", entry.result)
        entry.revisedPrompt?.let { revisedPrompt ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = revisedPrompt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewModeCard(entry: ThreadItem.ReviewMode) {
    ToolCard(
        title = if (entry.entered) "Entered review mode" else "Exited review mode",
        icon = Icons.Rounded.Lightbulb,
    ) {
        Text(
            text = entry.review,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ContextCompactionCard() {
    ToolCard(
        title = "Conversation compacted",
        icon = Icons.Rounded.AutoAwesome,
    ) {
        Text(
            text = "Codex compacted the thread history to continue the conversation.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnknownItemCard(entry: ThreadItem.Unknown) {
    ToolCard(
        title = entry.typeName.ifBlank { "Unknown item" },
        icon = Icons.Rounded.Code,
    ) {
        CodeBlock(entry.payload, maxLines = 12)
    }
}

@Composable
private fun BubbleRow(
    isUser: Boolean,
    label: String,
    supportingLabel: String?,
    bubbleColor: Color,
    textColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 340.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                supportingLabel?.let { tag ->
                    StatusChip(
                        label = tag,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = bubbleColor,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun TranscriptCard(
    title: String,
    icon: ImageVector,
    status: ThreadItemStatus? = null,
    trailing: (@Composable () -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CodexCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier),
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
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                when {
                    trailing != null -> trailing()
                    status != null -> StatusChip(
                        label = threadItemStatusLabel(status),
                        color = threadItemStatusColor(status),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ToolCard(
    title: String,
    icon: ImageVector,
    status: ThreadItemStatus? = null,
    statusLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    TranscriptCard(
        title = title,
        icon = icon,
        trailing = {
            when {
                status != null -> StatusChip(
                    label = threadItemStatusLabel(status),
                    color = threadItemStatusColor(status),
                )

                statusLabel != null -> StatusChip(
                    label = statusLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        content = content,
    )
}

@Composable
private fun NonTextUserInputs(
    items: List<UserInputContent>,
    isUser: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            Surface(
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                },
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = when (item) {
                        is UserInputContent.Text -> item.text
                        is UserInputContent.Image -> "Image: ${item.url}"
                        is UserInputContent.LocalImage -> "Local image: ${item.path}"
                        is UserInputContent.Skill -> "Skill: ${item.name}"
                        is UserInputContent.Mention -> "Mention: ${item.name}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun MetadataLine(
    label: String,
    value: String,
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun JsonSection(
    label: String,
    value: String,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(6.dp))
    CodeBlock(value, maxLines = 12)
}

@Composable
private fun CodeBlock(
    text: String,
    maxLines: Int = Int.MAX_VALUE,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun WrapPills(labels: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { label ->
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = CircleShape,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = "${state.threadId} • ${state.status}",
                style = MaterialTheme.typography.labelLarge,
            )
            state.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun commandActionLabel(action: CommandActionHint): String = buildString {
    append(action.type)
    action.name?.let { append(" • $it") }
    action.path?.let { append(" • $it") }
    action.query?.let { append(" • $it") }
}

private fun approvalHeadline(approval: ApprovalItem): String = when (approval.kind) {
    ApprovalKind.CommandExecution -> approval.command ?: "Command approval requested"
    ApprovalKind.FileChange -> {
        val firstPath = approval.filePaths.firstOrNull() ?: "File changes pending"
        if (approval.filePaths.size <= 1) firstPath else "$firstPath +${approval.filePaths.size - 1} more"
    }
}

@Composable
private fun approvalDecisionBackground(decision: ApprovalDecision): Color = when (decision) {
    ApprovalDecision.Accept -> MaterialTheme.colorScheme.primary
    ApprovalDecision.AcceptForSession -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ApprovalDecision.Decline -> MaterialTheme.colorScheme.surfaceVariant
    ApprovalDecision.Cancel -> Color.Transparent
}

@Composable
private fun approvalDecisionContent(decision: ApprovalDecision): Color = when (decision) {
    ApprovalDecision.Accept -> MaterialTheme.colorScheme.onPrimary
    ApprovalDecision.AcceptForSession -> MaterialTheme.colorScheme.primary
    ApprovalDecision.Decline -> MaterialTheme.colorScheme.onSurface
    ApprovalDecision.Cancel -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun threadItemStatusColor(status: ThreadItemStatus): Color = when (status) {
    ThreadItemStatus.InProgress -> MaterialTheme.colorScheme.primary
    ThreadItemStatus.Completed -> Color(0xFF2F9A58)
    ThreadItemStatus.Failed -> MaterialTheme.colorScheme.error
    ThreadItemStatus.Declined -> Color(0xFFD59734)
}

private fun threadItemStatusLabel(status: ThreadItemStatus): String = when (status) {
    ThreadItemStatus.InProgress -> "In Progress"
    ThreadItemStatus.Completed -> "Completed"
    ThreadItemStatus.Failed -> "Failed"
    ThreadItemStatus.Declined -> "Declined"
}

@Composable
private fun activityBackground(emphasis: ThreadActivityEmphasis): Color = when (emphasis) {
    ThreadActivityEmphasis.Active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    ThreadActivityEmphasis.Success -> Color(0xFF2F9A58).copy(alpha = 0.12f)
    ThreadActivityEmphasis.Warning -> Color(0xFFD59734).copy(alpha = 0.14f)
    ThreadActivityEmphasis.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
    ThreadActivityEmphasis.Neutral -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
}

@Composable
private fun activityContent(emphasis: ThreadActivityEmphasis): Color = when (emphasis) {
    ThreadActivityEmphasis.Active -> MaterialTheme.colorScheme.primary
    ThreadActivityEmphasis.Success -> Color(0xFF2F9A58)
    ThreadActivityEmphasis.Warning -> Color(0xFFD59734)
    ThreadActivityEmphasis.Error -> MaterialTheme.colorScheme.error
    ThreadActivityEmphasis.Neutral -> MaterialTheme.colorScheme.onSurface
}
