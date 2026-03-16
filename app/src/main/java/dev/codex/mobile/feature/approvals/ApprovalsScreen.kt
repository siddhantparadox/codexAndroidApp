package dev.codex.mobile.feature.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.codex.mobile.app.CodexAppGraph
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.approvalPrompt
import dev.codex.mobile.core.model.decisionLabel
import dev.codex.mobile.core.model.detailLines
import dev.codex.mobile.core.model.headline

@Composable
fun ApprovalsScreen(
    onOpenThread: (String) -> Unit,
    viewModel: ApprovalsViewModel = viewModel(
        factory = ApprovalsViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = CodexSpacing.screenHorizontal,
            top = CodexSpacing.topLevelHeaderGap,
            end = CodexSpacing.screenHorizontal,
            bottom = CodexSpacing.screenBottom,
        ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Codex Mobile",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = "Pending approvals",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text = "Pending Approvals".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (uiState.entries.isEmpty()) {
                        "No active approval requests."
                    } else {
                        "${uiState.entries.size} request(s) need a decision."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (uiState.entries.isEmpty()) {
            item {
                CodexCard {
                    Text(
                        text = "Approval queue is clear.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
                    Text(
                        text = "New approval requests will appear here when Codex needs a decision from the desktop session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(uiState.entries, key = { entry -> entry.id }) { entry ->
                when (entry) {
                    is ApprovalQueueEntry.Standard -> ApprovalCard(
                        approval = entry.approval,
                        onDecision = { decision -> viewModel.resolveApproval(entry.approval.id, decision) },
                        onClick = { onOpenThread(entry.threadId) },
                    )

                    is ApprovalQueueEntry.ToolPrompt -> ToolApprovalCard(
                        entry = entry,
                        onDecision = { response ->
                            viewModel.respondToUserInput(entry.request.requestId, response)
                        },
                        onClick = { onOpenThread(entry.threadId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: ApprovalItem,
    onDecision: (ApprovalDecision) -> Unit,
    onClick: () -> Unit,
) {
    CodexCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when (approval.kind) {
                            ApprovalKind.CommandExecution -> Icons.Rounded.Terminal
                            ApprovalKind.FileChange,
                            ApprovalKind.Permissions,
                            -> Icons.Rounded.Description
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (approval.kind) {
                            ApprovalKind.CommandExecution -> "Command Execution"
                            ApprovalKind.FileChange -> "File Change"
                            ApprovalKind.Permissions -> "Permissions"
                        }.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(modifier = Modifier.padding(CodexSpacing.cardPadding)) {
                Text(
                    text = approval.headline(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                approval.reason?.let { reason ->
                    Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
                    Text(
                        text = reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                approval.detailLines().forEach { detail ->
                    Spacer(modifier = Modifier.height(CodexSpacing.listGap))
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
                approval.availableDecisions.forEachIndexed { index, decision ->
                    ApprovalButton(
                        label = approval.decisionLabel(decision),
                        containerColor = approvalDecisionBackground(decision),
                        contentColor = approvalDecisionContent(decision),
                        onClick = { onDecision(decision) },
                    )
                    if (index != approval.availableDecisions.lastIndex) {
                        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolApprovalCard(
    entry: ApprovalQueueEntry.ToolPrompt,
    onDecision: (ThreadUserInputResponse) -> Unit,
    onClick: () -> Unit,
) {
    val prompt = requireNotNull(entry.request.approvalPrompt)
    CodexCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Tool Approval".uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(modifier = Modifier.padding(CodexSpacing.cardPadding)) {
                Text(
                    text = prompt.prompt,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (prompt.header.isNotBlank()) {
                    Spacer(modifier = Modifier.height(CodexSpacing.compactGap))
                    Text(
                        text = prompt.header,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(CodexSpacing.listGap))
                Text(
                    text = "Responding here resolves the pending tool approval for this thread.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
                prompt.actions.forEachIndexed { index, action ->
                    ToolApprovalButton(
                        label = action.label,
                        response = action.response,
                        onClick = { onDecision(action.response) },
                    )
                    if (index != prompt.actions.lastIndex) {
                        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolApprovalButton(
    label: String,
    response: ThreadUserInputResponse,
    onClick: () -> Unit,
) {
    when (response) {
        is ThreadUserInputResponse.Accept -> Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = label)
        }

        ThreadUserInputResponse.Decline -> OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = label)
        }

        ThreadUserInputResponse.Cancel -> OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = label)
        }
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
private fun ApprovalButton(
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(containerColor, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}


