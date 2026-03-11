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
        val nonTextParts = entry.content.filterNot { it is UserInputContent.Text }
        if (textParts.isEmpty()) {
            if (nonTextParts.isEmpty()) {
                ThreadRichText(
                    text = entry.text,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    codeBackground = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    codeColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            textParts.forEachIndexed { index, textPart ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                ThreadRichText(
                    text = textPart.text,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    codeBackground = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    codeColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
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
    val bubbleColor = if (entry.phase == "commentary") {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    BubbleRow(
        isUser = false,
        label = "Codex",
        supportingLabel = when (entry.phase) {
            "commentary" -> "Working"
            "final_answer" -> "Final"
            else -> null
        },
        bubbleColor = bubbleColor,
        textColor = MaterialTheme.colorScheme.onSurface,
    ) {
        ThreadRichText(
            text = entry.text,
            textColor = MaterialTheme.colorScheme.onSurface,
            textStyle = MaterialTheme.typography.bodyLarge,
            codeBackground = bubbleColor.copy(alpha = 0.85f),
            codeColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PlanCard(entry: ThreadItem.Plan) {
    TechnicalCard(
        rememberKey = entry.id,
        title = "Execution plan",
        badge = "PLAN",
        preview = firstPreviewLine(entry.text),
        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
        family = TechnicalCardFamily.Plan,
    ) {
        ThreadRichText(
            text = entry.text,
            textColor = MaterialTheme.colorScheme.onSurface,
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ReasoningCard(entry: ThreadItem.Reasoning) {
    TechnicalCard(
        rememberKey = entry.id,
        title = "Model reasoning",
        badge = "REASONING",
        preview = firstPreviewLine(entry.summary.ifBlank { entry.contentText }),
        icon = Icons.Rounded.Psychology,
        family = TechnicalCardFamily.Reasoning,
    ) {
        entry.summarySections.ifEmpty { listOf(entry.summary) }.forEach { section ->
            ThreadRichText(
                text = section,
                textColor = MaterialTheme.colorScheme.onSurface,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
        if (entry.contentText.isNotBlank()) {
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
    TechnicalCard(
        rememberKey = entry.id,
        title = entry.command,
        badge = "CMD",
        preview = commandPreview(entry),
        icon = Icons.Rounded.Terminal,
        family = TechnicalCardFamily.Command,
        status = entry.status,
    ) {
        entry.cwd?.let { cwd ->
            MetadataLine("CWD", cwd)
        }
        if (entry.commandActions.isNotEmpty()) {
            WrapPills(entry.commandActions.map(::commandActionLabel))
        }
        entry.durationMs?.let { durationMs ->
            MetadataLine("Duration", "${durationMs} ms")
        }
        entry.processId?.let { processId ->
            MetadataLine("Process", processId)
        }
        entry.exitCode?.let { exitCode ->
            MetadataLine("Exit code", exitCode.toString())
        }
        if (entry.aggregatedOutput?.isNotBlank() == true) {
            Text(
                text = "Output",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            CodeBlock(text = entry.aggregatedOutput)
        }
        if (entry.interactions.isNotEmpty()) {
            Text(
                text = "Terminal input",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            entry.interactions.forEach { interaction ->
                CodeBlock(interaction, maxLines = 8)
            }
        }
    }
}

@Composable
private fun FileChangeCard(entry: ThreadItem.FileChange) {
    TechnicalCard(
        rememberKey = entry.id,
        title = "${entry.changes.size} file change(s)",
        badge = "PATCH",
        preview = fileChangePreview(entry),
        icon = Icons.Rounded.Description,
        family = TechnicalCardFamily.Patch,
        status = entry.status,
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
                CodeBlock(text = change.diff)
            }
        }
        entry.toolOutput?.takeIf { it.isNotBlank() }?.let { output ->
            Text(
                text = "apply_patch output",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            CodeBlock(text = output)
        }
    }
}

@Composable
private fun McpToolCallCard(entry: ThreadItem.McpToolCall) {
    TechnicalCard(
        rememberKey = entry.id,
        title = "${entry.server} / ${entry.tool}",
        badge = "MCP",
        preview = mcpPreview(entry),
        icon = Icons.Rounded.BuildCircle,
        family = TechnicalCardFamily.Mcp,
        status = entry.status,
    ) {
        JsonSection(label = "Arguments", value = entry.arguments)
        entry.result?.let { result ->
            JsonSection(label = "Result", value = result)
        }
        entry.errorMessage?.let { error ->
            MetadataLine("Error", error)
        }
        entry.durationMs?.let { durationMs ->
            MetadataLine("Duration", "${durationMs} ms")
        }
        if (entry.progressMessages.isNotEmpty()) {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            entry.progressMessages.forEach { message ->
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
    TechnicalCard(
        rememberKey = entry.id,
        title = entry.tool,
        badge = "TOOL",
        preview = dynamicToolPreview(entry),
        icon = Icons.Rounded.Code,
        family = TechnicalCardFamily.Tool,
        status = entry.status,
    ) {
        JsonSection(label = "Arguments", value = entry.arguments)
        entry.success?.let { success ->
            MetadataLine("Success", success.toString())
        }
        entry.durationMs?.let { durationMs ->
            MetadataLine("Duration", "${durationMs} ms")
        }
        if (entry.contentItems.isNotEmpty()) {
            Text(
                text = "Returned content",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            entry.contentItems.forEach { item ->
                when (item) {
                    is ToolContentItem.Text -> ThreadRichText(
                        text = item.text,
                        textColor = MaterialTheme.colorScheme.onSurface,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )

                    is ToolContentItem.Image -> MetadataLine("Image", item.imageUrl)
                }
            }
        }
    }
}

@Composable
private fun CollabToolCallCard(entry: ThreadItem.CollabToolCall) {
    TechnicalCard(
        rememberKey = entry.id,
        title = entry.tool,
        badge = "COLLAB",
        preview = collabPreview(entry),
        icon = Icons.AutoMirrored.Rounded.CallSplit,
        family = TechnicalCardFamily.Collab,
        status = entry.status,
    ) {
        MetadataLine("Sender", entry.senderThreadId)
        MetadataLine("Receivers", entry.receiverThreadIds.joinToString())
        entry.prompt?.let { prompt ->
            ThreadRichText(
                text = prompt,
                textColor = MaterialTheme.colorScheme.onSurface,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
        if (entry.agentStates.isNotEmpty()) {
            entry.agentStates.forEach { state ->
                AgentStateRow(state)
            }
        }
    }
}

@Composable
private fun WebSearchCard(entry: ThreadItem.WebSearch) {
    TechnicalCard(
        rememberKey = entry.id,
        title = "Web search",
        badge = "WEB",
        preview = webPreview(entry),
        icon = Icons.AutoMirrored.Rounded.ManageSearch,
        family = TechnicalCardFamily.Web,
    ) {
        MetadataLine("Query", entry.query)
        entry.actionLabel?.let { actionLabel ->
            MetadataLine("Action", actionLabel)
        }
    }
}

@Composable
private fun ImageViewCard(entry: ThreadItem.ImageView) {
    TechnicalCard(
        rememberKey = entry.id,
        title = "Image viewer",
        badge = "IMAGE",
        preview = firstPreviewLine(entry.path),
        icon = Icons.Rounded.Visibility,
        family = TechnicalCardFamily.Image,
    ) {
        MetadataLine("Path", entry.path)
    }
}

@Composable
private fun ImageGenerationCard(entry: ThreadItem.ImageGeneration) {
    TechnicalCard(
        rememberKey = entry.id,
        title = "Image generation",
        badge = "IMAGE",
        preview = firstPreviewLine(entry.revisedPrompt ?: entry.result),
        icon = Icons.Rounded.Image,
        family = TechnicalCardFamily.Image,
        statusLabel = entry.status,
    ) {
        MetadataLine("Result", entry.result)
        entry.revisedPrompt?.let { revisedPrompt ->
            ThreadRichText(
                text = revisedPrompt,
                textColor = MaterialTheme.colorScheme.onSurface,
                textStyle = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ReviewModeCard(entry: ThreadItem.ReviewMode) {
    TechnicalCard(
        rememberKey = entry.id,
        title = if (entry.entered) "Entered review mode" else "Exited review mode",
        badge = "REVIEW",
        preview = firstPreviewLine(entry.review),
        icon = Icons.Rounded.Lightbulb,
        family = TechnicalCardFamily.Review,
    ) {
        ThreadRichText(
            text = entry.review,
            textColor = MaterialTheme.colorScheme.onSurface,
            textStyle = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ContextCompactionCard() {
    TechnicalCard(
        rememberKey = "context_compaction",
        title = "Conversation compacted",
        badge = "SYSTEM",
        preview = "Codex compacted the thread history to continue the conversation.",
        icon = Icons.Rounded.AutoAwesome,
        family = TechnicalCardFamily.System,
    ) {
        Text(
            text = "Codex compacted the thread history to continue the conversation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnknownItemCard(entry: ThreadItem.Unknown) {
    TechnicalCard(
        rememberKey = entry.id,
        title = entry.typeName.ifBlank { "Unknown item" },
        badge = "SYSTEM",
        preview = firstPreviewLine(entry.payload),
        icon = Icons.Rounded.Code,
        family = TechnicalCardFamily.System,
    ) {
        CodeBlock(entry.payload)
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
private fun NonTextUserInputs(
    items: List<UserInputContent>,
    isUser: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item ->
            ThreadUserAttachment(
                item = item,
                isUser = isUser,
            )
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

private fun firstPreviewLine(text: String): String = text
    .lines()
    .map { it.trim() }
    .firstOrNull { it.isNotBlank() }
    ?: "No details"

private fun commandPreview(entry: ThreadItem.CommandExecution): String = when {
    !entry.aggregatedOutput.isNullOrBlank() -> firstPreviewLine(entry.aggregatedOutput)
    entry.exitCode != null -> "Exit code ${entry.exitCode}"
    entry.cwd != null -> entry.cwd
    entry.commandActions.isNotEmpty() -> commandActionLabel(entry.commandActions.first())
    else -> entry.command
}

private fun fileChangePreview(entry: ThreadItem.FileChange): String {
    val firstChange = entry.changes.firstOrNull() ?: return "No file changes"
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
    entry.contentItems.isNotEmpty() -> when (val firstItem = entry.contentItems.first()) {
        is ToolContentItem.Text -> firstPreviewLine(firstItem.text)
        is ToolContentItem.Image -> firstItem.imageUrl
    }

    entry.success != null -> if (entry.success) "Completed successfully" else "Marked unsuccessful"
    else -> firstPreviewLine(entry.arguments)
}

private fun collabPreview(entry: ThreadItem.CollabToolCall): String = when {
    !entry.prompt.isNullOrBlank() -> firstPreviewLine(entry.prompt)
    entry.receiverThreadIds.isNotEmpty() -> {
        val firstReceiver = entry.receiverThreadIds.first()
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
    entry.actionLabel?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
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
