package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.cardBorder
import dev.codex.mobile.core.model.ComposerPersonality
import dev.codex.mobile.core.model.ComposerReasoningEffort
import dev.codex.mobile.core.model.ComposerSandboxMode
import dev.codex.mobile.core.model.ComposerSkillOption

internal enum class ThreadComposerSheetContent {
    QuickActions,
    Model,
    Effort,
    Personality,
    Permissions,
    Skill,
}

@Composable
internal fun ThreadComposerSheetContentView(
    content: ThreadComposerSheetContent,
    uiState: ThreadDetailUiState,
    onShowModels: () -> Unit,
    onShowEfforts: () -> Unit,
    onShowPersonality: () -> Unit,
    onShowPermissions: () -> Unit,
    onShowSkills: () -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (ComposerReasoningEffort) -> Unit,
    onSelectPersonality: (ComposerPersonality) -> Unit,
    onSelectSandboxMode: (ComposerSandboxMode) -> Unit,
    onSelectSkill: (ComposerSkillOption) -> Unit,
    onPickPhoto: () -> Unit,
) {
    when (content) {
        ThreadComposerSheetContent.QuickActions -> QuickActionsSheet(
            personalityLabel = uiState.selectedPersonality.displayLabel(),
            personalitySupported = uiState.selectedModel?.supportsPersonality == true,
            permissionLabel = uiState.selectedSandboxMode.displayLabel(),
            canChangePersonality = uiState.selectedModel?.supportsPersonality == true && !uiState.canInterrupt,
            canChangePermissions = !uiState.canInterrupt,
            canAttachImage = uiState.selectedModel?.supportsImageInput != false,
            onShowPersonality = onShowPersonality,
            onShowPermissions = onShowPermissions,
            onShowSkills = onShowSkills,
            onPickPhoto = onPickPhoto,
        )

        ThreadComposerSheetContent.Model -> ModelSelectionSheet(
            title = "Choose model",
            options = uiState.composerCatalog.models.map { model ->
                SheetListOption(
                    key = model.id,
                    title = model.displayName,
                    subtitle = if (model.isDefault) "Default model" else null,
                    selected = model.id == uiState.selectedModel?.id,
                )
            },
            onSelect = onSelectModel,
        )

        ThreadComposerSheetContent.Effort -> ModelSelectionSheet(
            title = "Reasoning effort",
            options = uiState.selectedModel?.supportedReasoningEfforts
                ?.map { option ->
                    SheetListOption(
                        key = option.effort.name,
                        title = option.effort.displayLabel(),
                        subtitle = option.description.takeIf { it.isNotBlank() },
                        selected = option.effort == uiState.selectedEffort,
                    )
                }
                .orEmpty(),
            onSelect = { key ->
                ComposerReasoningEffort.entries.firstOrNull { it.name == key }?.let(onSelectEffort)
            },
        )

        ThreadComposerSheetContent.Personality -> ModelSelectionSheet(
            title = "Personality",
            options = ComposerPersonality.entries.map { personality ->
                SheetListOption(
                    key = personality.name,
                    title = personality.displayLabel(),
                    subtitle = when (personality) {
                        ComposerPersonality.Default -> "Use the model default"
                        ComposerPersonality.Friendly -> "Warmer, more conversational tone"
                        ComposerPersonality.Pragmatic -> "Direct, concise, execution-focused tone"
                    },
                    selected = personality == uiState.selectedPersonality,
                )
            },
            onSelect = { key ->
                ComposerPersonality.entries.firstOrNull { it.name == key }?.let(onSelectPersonality)
            },
        )

        ThreadComposerSheetContent.Permissions -> ModelSelectionSheet(
            title = "Permissions",
            options = ComposerSandboxMode.entries.map { sandboxMode ->
                SheetListOption(
                    key = sandboxMode.name,
                    title = sandboxMode.displayLabel(),
                    subtitle = sandboxMode.subtitle(),
                    selected = sandboxMode == uiState.selectedSandboxMode,
                )
            },
            onSelect = { key ->
                ComposerSandboxMode.entries.firstOrNull { it.name == key }?.let(onSelectSandboxMode)
            },
        )

        ThreadComposerSheetContent.Skill -> SkillSelectionSheet(
            skills = uiState.composerCatalog.skills,
            selectedSkillPath = uiState.selectedSkill?.path,
            onSelect = onSelectSkill,
        )
    }
}

@Composable
private fun QuickActionsSheet(
    personalityLabel: String,
    personalitySupported: Boolean,
    permissionLabel: String,
    canChangePersonality: Boolean,
    canChangePermissions: Boolean,
    canAttachImage: Boolean,
    onShowPersonality: () -> Unit,
    onShowPermissions: () -> Unit,
    onShowSkills: () -> Unit,
    onPickPhoto: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodexSpacing.screenHorizontal, vertical = CodexSpacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Text(
            text = "Add to reply",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        QuickActionCard(
            title = "Personality",
            subtitle = if (!personalitySupported) {
                "This model does not support personality"
            } else if (canChangePersonality) {
                personalityLabel
            } else {
                "$personalityLabel • applies next turn"
            },
            enabled = canChangePersonality,
            onClick = onShowPersonality,
        )
        QuickActionCard(
            title = "Permissions",
            subtitle = if (canChangePermissions) {
                permissionLabel
            } else {
                "$permissionLabel • applies next turn"
            },
            enabled = canChangePermissions,
            onClick = onShowPermissions,
        )
        QuickActionCard(
            title = "Photo",
            subtitle = if (canAttachImage) "Attach an image from your phone" else "This model does not support image input",
            enabled = canAttachImage,
            onClick = onPickPhoto,
        )
        QuickActionCard(
            title = "Skill",
            subtitle = "Insert a skill token and structured skill input",
            enabled = true,
            onClick = onShowSkills,
        )
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.cardBorder),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class SheetListOption(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val selected: Boolean = false,
)

@Composable
private fun ModelSelectionSheet(
    title: String,
    options: List<SheetListOption>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodexSpacing.screenHorizontal, vertical = CodexSpacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
        ) {
            items(
                items = options,
                key = { option -> option.key },
            ) { option ->
                QuickActionCard(
                    title = option.title,
                    subtitle = option.subtitle ?: if (option.selected) "Selected" else "",
                    enabled = true,
                    onClick = { onSelect(option.key) },
                )
            }
        }
    }
}

@Composable
private fun SkillSelectionSheet(
    skills: List<ComposerSkillOption>,
    selectedSkillPath: String?,
    onSelect: (ComposerSkillOption) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CodexSpacing.screenHorizontal, vertical = CodexSpacing.sectionGap),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        Text(
            text = "Choose skill",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (skills.isEmpty()) {
            Text(
                text = "No skills are available from the current host session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
            ) {
                items(
                    items = skills,
                    key = { skill -> skill.path },
                ) { skill ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(skill) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (skill.path == selectedSkillPath) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                            } else {
                                MaterialTheme.colorScheme.cardBorder
                            },
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = skill.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = skill.shortDescription ?: skill.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (skill.path == selectedSkillPath) {
                                Text(
                                    text = "Selected",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun ComposerReasoningEffort.displayLabel(): String = when (this) {
    ComposerReasoningEffort.None -> "None"
    ComposerReasoningEffort.Minimal -> "Minimal"
    ComposerReasoningEffort.Low -> "Low"
    ComposerReasoningEffort.Medium -> "Medium"
    ComposerReasoningEffort.High -> "High"
    ComposerReasoningEffort.XHigh -> "XHigh"
}

private fun ComposerPersonality.displayLabel(): String = when (this) {
    ComposerPersonality.Default -> "Default"
    ComposerPersonality.Friendly -> "Friendly"
    ComposerPersonality.Pragmatic -> "Pragmatic"
}

private fun ComposerSandboxMode.displayLabel(): String = when (this) {
    ComposerSandboxMode.Default -> "Default"
    ComposerSandboxMode.ReadOnly -> "Read Only"
    ComposerSandboxMode.WorkspaceWrite -> "Workspace Write"
    ComposerSandboxMode.FullAccess -> "Full Access"
}

private fun ComposerSandboxMode.subtitle(): String = when (this) {
    ComposerSandboxMode.Default -> "Use the host's current sandbox settings"
    ComposerSandboxMode.ReadOnly -> "Inspect files and run safe reads only"
    ComposerSandboxMode.WorkspaceWrite -> "Allow edits inside the workspace"
    ComposerSandboxMode.FullAccess -> "Allow unrestricted execution on the host"
}
