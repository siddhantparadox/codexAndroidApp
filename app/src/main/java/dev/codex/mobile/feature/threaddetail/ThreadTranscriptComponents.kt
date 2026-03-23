package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ThreadActivity
import dev.codex.mobile.core.model.ThreadActivityEmphasis
import dev.codex.mobile.core.model.ThreadDynamicToolRequest
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.UserInputContent
import dev.codex.mobile.core.model.decisionLabel
import dev.codex.mobile.core.model.detailLines
import dev.codex.mobile.core.model.headline

@Composable
internal fun ThreadTranscriptRowView(
    row: TranscriptRow,
    activeItemIds: Set<String>,
    selectionEnabled: Boolean,
    animationsEnabled: Boolean,
    autoRevealExpandedContent: Boolean,
    onDecision: (String, ApprovalDecision) -> Unit,
    onSubmitUserInput: (String, ThreadUserInputResponse) -> Unit,
    onChooseDynamicTool: (ThreadDynamicToolRequest) -> Unit,
    onCancelDynamicTool: (String) -> Unit,
    onOpenTechnicalItemDetail: (ThreadItem) -> Unit,
) {
    when (row) {
        is TranscriptRow.UserMessage -> UserBubble(
            entry = row.item,
            selectionEnabled = selectionEnabled,
        )
        is TranscriptRow.AgentMessage -> AgentBubble(
            entry = row.item,
            isLive = row.item.id in activeItemIds,
            selectionEnabled = selectionEnabled,
            animationsEnabled = animationsEnabled,
        )
        TranscriptRow.PendingAgentPlaceholder -> PendingAgentBubble(
            animationsEnabled = animationsEnabled,
        )
        is TranscriptRow.TechnicalStrip -> TechnicalPillStrip(
            items = row.items,
            approvals = row.approvals,
            userInputRequests = row.userInputRequests,
            activeItemIds = activeItemIds,
            animationsEnabled = animationsEnabled,
            autoRevealExpandedContent = autoRevealExpandedContent,
            onOpenFullContent = onOpenTechnicalItemDetail,
            onDecision = onDecision,
            onSubmitUserInput = onSubmitUserInput,
        )

        is TranscriptRow.ApprovalCard -> InlineApprovalCard(
            approval = row.approval,
            onDecision = onDecision,
        )

        is TranscriptRow.UserInputRequestCard -> InlineUserInputRequestCard(
            request = row.request,
            onSubmit = onSubmitUserInput,
        )

        is TranscriptRow.DynamicToolRequestCard -> InlineDynamicToolRequestCard(
            request = row.request,
            onChoosePhoto = onChooseDynamicTool,
            onCancel = onCancelDynamicTool,
        )
    }
}

@Composable
internal fun ThreadActivityPanel(
    activities: List<ThreadActivity>,
) {
    var expanded by remember { mutableStateOf(false) }

    CodexCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(CodexSpacing.cardPadding),
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
                Spacer(modifier = Modifier.height(CodexSpacing.listGap))
                Surface(
                    color = activityBackground(activity.emphasis),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Column(modifier = Modifier.padding(CodexSpacing.sectionGap)) {
                        Text(
                            text = activity.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = activityContent(activity.emphasis),
                        )
                        activity.detail?.let { detail ->
                            Spacer(modifier = Modifier.height(CodexSpacing.microGap))
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
        contentPadding = PaddingValues(CodexSpacing.cardPadding),
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
                androidx.compose.material3.Icon(
                    imageVector = when (approval.kind) {
                        ApprovalKind.CommandExecution -> Icons.Rounded.Terminal
                        ApprovalKind.FileChange,
                        ApprovalKind.Permissions,
                        -> Icons.Rounded.Description
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(CodexSpacing.listGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = approval.headline(),
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
                label = if (approval.kind == ApprovalKind.Permissions) "Permissions" else "Approval",
                color = Color(0xFFD59734),
            )
        }
        approval.detailLines().forEach { detail ->
            Spacer(modifier = Modifier.height(CodexSpacing.listGap))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.tightGap)) {
            approval.availableDecisions.forEach { decision ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = approvalDecisionBackground(decision),
                            shape = MaterialTheme.shapes.small,
                        )
                        .clickable { onDecision(approval.id, decision) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = approval.decisionLabel(decision),
                        style = MaterialTheme.typography.labelLarge,
                        color = approvalDecisionContent(decision),
                    )
                }
            }
        }
    }
}

@Composable
private fun UserBubble(
    entry: ThreadItem.UserMessage,
    selectionEnabled: Boolean,
) {
    val copyText: String = remember(entry) { entry.copyableMessageText() }
    val textParts: List<UserInputContent.Text> = remember(entry.content) {
        entry.content.filterIsInstance<UserInputContent.Text>()
    }
    val nonTextParts: List<UserInputContent> = remember(entry.content) {
        entry.content.filterNot { item -> item is UserInputContent.Text }
    }
    BubbleRow(
        isUser = true,
        label = "You",
        supportingLabel = null,
        liveLabel = null,
        liveColor = MaterialTheme.colorScheme.primary,
        bubbleColor = MaterialTheme.colorScheme.primary,
        textColor = MaterialTheme.colorScheme.onPrimary,
        headerAction = {
            HeaderCopyAction(
                copyText = copyText,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        if (textParts.isEmpty()) {
            if (nonTextParts.isEmpty()) {
                ThreadRichText(
                    text = entry.text,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    selectionEnabled = selectionEnabled,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    codeBackground = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    codeColor = MaterialTheme.colorScheme.onPrimary,
                    linkColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            textParts.forEachIndexed { index, textPart ->
                if (index > 0) Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
                ThreadRichText(
                    text = textPart.text,
                    textColor = MaterialTheme.colorScheme.onPrimary,
                    selectionEnabled = selectionEnabled,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    codeBackground = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                    codeColor = MaterialTheme.colorScheme.onPrimary,
                    linkColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        if (nonTextParts.isNotEmpty()) {
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
            NonTextUserInputs(
                items = nonTextParts,
                isUser = true,
            )
        }
    }
}

@Composable
private fun AgentBubble(
    entry: ThreadItem.AgentMessage,
    isLive: Boolean,
    selectionEnabled: Boolean,
    animationsEnabled: Boolean,
) {
    val copyText: String = remember(entry.text) { entry.text.trim() }
    val bubbleColor: Color = if (entry.phase == "commentary") {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }
    BubbleRow(
        isUser = false,
        label = "Codex",
        supportingLabel = if (isLive) null else staticAgentLabel(entry.phase),
        liveLabel = if (isLive) liveAgentLabel(entry.phase) else null,
        liveColor = MaterialTheme.colorScheme.primary,
        bubbleColor = bubbleColor,
        textColor = MaterialTheme.colorScheme.onSurface,
        headerAction = {
            HeaderCopyAction(
                copyText = copyText,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        animationsEnabled = animationsEnabled,
    ) {
        if (isLive) {
            LiveAccentLine(
                color = MaterialTheme.colorScheme.primary,
                animate = animationsEnabled,
            )
            Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        }
        ThreadRichText(
            text = entry.text,
            textColor = MaterialTheme.colorScheme.onSurface,
            selectionEnabled = selectionEnabled,
            textStyle = MaterialTheme.typography.bodyLarge,
            codeBackground = bubbleColor.copy(alpha = 0.85f),
            codeColor = MaterialTheme.colorScheme.onSurface,
            linkColor = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PendingAgentBubble(
    animationsEnabled: Boolean,
) {
    BubbleRow(
        isUser = false,
        label = "Codex",
        supportingLabel = null,
        liveLabel = "Thinking",
        liveColor = MaterialTheme.colorScheme.primary,
        bubbleColor = MaterialTheme.colorScheme.surfaceVariant,
        textColor = MaterialTheme.colorScheme.onSurface,
        headerAction = null,
        animationsEnabled = animationsEnabled,
    ) {
        LiveAccentLine(
            color = MaterialTheme.colorScheme.primary,
            animate = animationsEnabled,
        )
        Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
        Row(
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LivePulseDot(
                color = MaterialTheme.colorScheme.primary,
                animate = animationsEnabled,
            )
            Text(
                text = "Thinking…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BubbleRow(
    isUser: Boolean,
    label: String,
    supportingLabel: String?,
    liveLabel: String?,
    liveColor: Color,
    bubbleColor: Color,
    textColor: Color,
    headerAction: (@Composable (() -> Unit))?,
    animationsEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(ThreadTranscriptBubbleWidthFraction)
                .widthIn(max = ThreadTranscriptBubbleMaxWidth),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (isUser) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    liveLabel?.let { live ->
                        LiveStatusBadge(
                            label = live,
                            color = liveColor,
                            animate = animationsEnabled,
                        )
                    } ?: supportingLabel?.let { tag ->
                        StatusChip(
                            label = tag,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    headerAction?.invoke()
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        liveLabel?.let { live ->
                            LiveStatusBadge(
                                label = live,
                                color = liveColor,
                                animate = animationsEnabled,
                            )
                        } ?: supportingLabel?.let { tag ->
                            StatusChip(
                                label = tag,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    headerAction?.let { action ->
                        Spacer(modifier = Modifier.width(CodexSpacing.microGap))
                        action()
                    }
                }
            }
            Spacer(modifier = Modifier.height(CodexSpacing.microGap))
            Surface(
                color = bubbleColor,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = ThreadBubbleHorizontalPadding,
                        vertical = ThreadBubbleVerticalPadding,
                    ),
                ) {
                    content()
                }
            }
        }
    }
}

private fun staticAgentLabel(phase: String?): String? = when (phase) {
    "commentary" -> "Done"
    "final_answer" -> "Final"
    else -> null
}

private fun liveAgentLabel(phase: String?): String = when (phase) {
    "commentary" -> "Thinking"
    "final_answer" -> "Answering"
    else -> "Live"
}

@Composable
private fun NonTextUserInputs(
    items: List<UserInputContent>,
    isUser: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap)) {
        items.forEach { item ->
            ThreadUserAttachment(
                item = item,
                isUser = isUser,
            )
        }
    }
}

@Composable
private fun HeaderCopyAction(
    copyText: String,
    tint: Color,
) {
    if (copyText.isBlank()) return

    val copyTextToClipboard: (String) -> Unit = rememberThreadClipboardCopy()

    DisableSelection {
        TranscriptCopyIconButton(
            tint = tint,
            onClick = {
                copyTextToClipboard(copyText)
            },
        )
    }
}

@Composable
private fun approvalDecisionBackground(decision: ApprovalDecision): Color = when (decision) {
    ApprovalDecision.Accept -> MaterialTheme.colorScheme.primary
    ApprovalDecision.AcceptForSession,
    is ApprovalDecision.AcceptWithExecpolicyAmendment,
    is ApprovalDecision.ApplyNetworkPolicyAmendment,
    -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ApprovalDecision.Decline -> MaterialTheme.colorScheme.surfaceVariant
    ApprovalDecision.Cancel -> Color.Transparent
}

@Composable
private fun approvalDecisionContent(decision: ApprovalDecision): Color = when (decision) {
    ApprovalDecision.Accept -> MaterialTheme.colorScheme.onPrimary
    ApprovalDecision.AcceptForSession,
    is ApprovalDecision.AcceptWithExecpolicyAmendment,
    is ApprovalDecision.ApplyNetworkPolicyAmendment,
    -> MaterialTheme.colorScheme.primary
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

private fun ThreadItem.UserMessage.copyableMessageText(): String {
    val textParts: List<String> = content
        .filterIsInstance<UserInputContent.Text>()
        .map(UserInputContent.Text::text)
        .filter(String::isNotBlank)
    return when {
        textParts.isNotEmpty() -> textParts.joinToString(separator = "\n\n")
        text.isNotBlank() -> text.trim()
        else -> ""
    }
}

