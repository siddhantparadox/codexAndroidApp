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
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ThreadActivity
import dev.codex.mobile.core.model.ThreadActivityEmphasis
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.UserInputContent
import dev.codex.mobile.core.model.label

@Composable
internal fun ThreadTranscriptRowView(
    row: TranscriptRow,
    activeItemIds: Set<String>,
    onDecision: (String, ApprovalDecision) -> Unit,
) {
    when (row) {
        is TranscriptRow.UserMessage -> UserBubble(row.item)
        is TranscriptRow.AgentMessage -> AgentBubble(
            entry = row.item,
            isLive = row.item.id in activeItemIds,
        )
        is TranscriptRow.TechnicalStrip -> TechnicalPillStrip(
            items = row.items,
            approvals = row.approvals,
            activeItemIds = activeItemIds,
            onDecision = onDecision,
        )

        is TranscriptRow.OrphanApproval -> InlineApprovalCard(
            approval = row.approval,
            onDecision = onDecision,
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
                androidx.compose.material3.Icon(
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
        liveLabel = null,
        liveColor = MaterialTheme.colorScheme.primary,
        bubbleColor = MaterialTheme.colorScheme.primary,
        textColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        val textParts: List<UserInputContent.Text> = entry.content.filterIsInstance<UserInputContent.Text>()
        val nonTextParts: List<UserInputContent> = entry.content.filterNot { item -> item is UserInputContent.Text }
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
private fun AgentBubble(
    entry: ThreadItem.AgentMessage,
    isLive: Boolean,
) {
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
    ) {
        if (isLive) {
            LiveAccentLine(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
        }
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
private fun BubbleRow(
    isUser: Boolean,
    label: String,
    supportingLabel: String?,
    liveLabel: String?,
    liveColor: Color,
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
                liveLabel?.let { live ->
                    LiveStatusBadge(
                        label = live,
                        color = liveColor,
                    )
                } ?: supportingLabel?.let { tag ->
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

private fun staticAgentLabel(phase: String?): String? = when (phase) {
    "commentary" -> "Working"
    "final_answer" -> "Final"
    else -> null
}

private fun liveAgentLabel(phase: String?): String = when (phase) {
    "commentary" -> "Working"
    "final_answer" -> "Answering"
    else -> "Live"
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

private fun approvalHeadline(approval: ApprovalItem): String = when (approval.kind) {
    ApprovalKind.CommandExecution -> approval.command ?: "Command approval requested"
    ApprovalKind.FileChange -> {
        val firstPath: String = approval.filePaths.firstOrNull() ?: "File changes pending"
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
