package dev.codex.mobile.feature.threads

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.denseSupportingText
import dev.codex.mobile.core.designsystem.theme.metaText
import dev.codex.mobile.core.designsystem.theme.panelHeadline
import dev.codex.mobile.core.designsystem.theme.supportingText
import dev.codex.mobile.core.util.relativeTimeLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadCwdPickerSheet(
    query: String,
    options: List<ThreadCwdOption>,
    hasAnyOptions: Boolean,
    isCreatingThread: Boolean,
    errorMessage: String?,
    onDismissRequest: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onSelectCwd: (String) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CodexSpacing.screenHorizontal,
                    end = CodexSpacing.screenHorizontal,
                    bottom = CodexSpacing.screenBottom,
                ),
            verticalArrangement = Arrangement.spacedBy(CodexSpacing.sectionGap),
        ) {
            Text(
                text = "Start new thread",
                style = MaterialTheme.typography.panelHeadline,
            )
            Text(
                text = "Choose a folder used by an earlier thread on this desktop.",
                style = MaterialTheme.typography.supportingText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !isCreatingThread,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                placeholder = {
                    Text("Search folders")
                },
                singleLine = true,
            )
            if (isCreatingThread) {
                Text(
                    text = "Starting thread…",
                    style = MaterialTheme.typography.metaText,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.supportingText,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            when {
                !hasAnyOptions -> {
                    ThreadCwdPickerEmptyState(
                        title = "No folders yet",
                        message = "Open or create a few desktop threads first, then pick from their working folders here.",
                    )
                }

                options.isEmpty() -> {
                    ThreadCwdPickerEmptyState(
                        title = "No matching folders",
                        message = "Try a different search term.",
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(bottom = CodexSpacing.screenBottom),
                        verticalArrangement = Arrangement.spacedBy(CodexSpacing.listGap),
                    ) {
                        items(
                            items = options,
                            key = { option -> option.path },
                        ) { option ->
                            ThreadCwdOptionCard(
                                option = option,
                                enabled = !isCreatingThread,
                                onClick = { onSelectCwd(option.path) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadCwdOptionCard(
    option: ThreadCwdOption,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    CodexCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CodexSpacing.listGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
            ) {
                Text(
                    text = option.path,
                    style = MaterialTheme.typography.denseSupportingText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Last used ${relativeTimeLabel(option.lastUsedAtEpochSeconds)}",
                    style = MaterialTheme.typography.metaText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThreadCwdPickerEmptyState(
    title: String,
    message: String,
) {
    CodexCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(CodexSpacing.sectionGap))
            Column(
                verticalArrangement = Arrangement.spacedBy(CodexSpacing.microGap),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.panelHeadline,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
