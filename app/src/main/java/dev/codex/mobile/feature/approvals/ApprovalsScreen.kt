package dev.codex.mobile.feature.approvals

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ApprovalRisk
import dev.codex.mobile.core.model.ApprovalState

@Composable
fun ApprovalsScreen(
    onOpenThread: (String) -> Unit,
    viewModel: ApprovalsViewModel = viewModel(
        factory = ApprovalsViewModel.factory(CodexAppGraph.repository),
    ),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tabs = listOf(
        ApprovalState.Pending,
        ApprovalState.Approved,
        ApprovalState.Declined,
        ApprovalState.Archived,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = "Approval Queue".uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    tabs.forEach { tab ->
                        val selected = uiState.selectedTab == tab
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.selectTab(tab) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = tab.name.lowercase().replaceFirstChar(Char::uppercase),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        items(uiState.approvals, key = { approval -> approval.id }) { approval ->
            ApprovalCard(
                approval = approval,
                onPrimaryAction = {
                    when (approval.kind) {
                        ApprovalKind.Command -> viewModel.approve(approval.id)
                        ApprovalKind.FileChange -> viewModel.approve(approval.id)
                        ApprovalKind.Deployment -> viewModel.review(approval.id)
                    }
                },
                onSecondaryAction = {
                    when (approval.state) {
                        ApprovalState.Pending -> viewModel.decline(approval.id)
                        ApprovalState.Archived -> viewModel.review(approval.id)
                        else -> viewModel.archive(approval.id)
                    }
                },
                onTertiaryAction = { viewModel.archive(approval.id) },
                onClick = { onOpenThread(approval.threadId) },
            )
        }
    }
}

@Composable
private fun ApprovalCard(
    approval: ApprovalItem,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: () -> Unit,
    onTertiaryAction: () -> Unit,
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = approval.title.uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                RiskPill(risk = approval.risk)
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = approval.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = approval.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(18.dp))
                ApprovalButton(
                    label = approval.primaryActionLabel,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    onClick = onPrimaryAction,
                )
                Spacer(modifier = Modifier.height(10.dp))
                ApprovalButton(
                    label = approval.secondaryActionLabel,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = onSecondaryAction,
                )
                if (approval.tertiaryActionLabel != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ApprovalButton(
                        label = approval.tertiaryActionLabel,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onTertiaryAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskPill(risk: ApprovalRisk) {
    val background = when (risk) {
        ApprovalRisk.HighImpact -> Color(0xFFFCE9C6)
        ApprovalRisk.MediumRisk -> Color(0xFFDCEAF5)
        ApprovalRisk.LowRisk -> Color(0xFFDFF4E5)
    }
    val contentColor = when (risk) {
        ApprovalRisk.HighImpact -> Color(0xFFA66A00)
        ApprovalRisk.MediumRisk -> Color(0xFF24617C)
        ApprovalRisk.LowRisk -> Color(0xFF2F8F4E)
    }

    Box(
        modifier = Modifier
            .background(background, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = when (risk) {
                ApprovalRisk.HighImpact -> "High Impact"
                ApprovalRisk.MediumRisk -> "Medium Risk"
                ApprovalRisk.LowRisk -> "Low Risk"
            },
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
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
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
        )
    }
}
