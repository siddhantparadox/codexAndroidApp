package dev.codex.mobile.feature.threads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.listItemTitle
import dev.codex.mobile.core.designsystem.theme.metaText
import dev.codex.mobile.core.designsystem.theme.sectionLabel
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadResultDigestKind
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.compactListMetadataLabel
import dev.codex.mobile.core.model.displayTitle
import dev.codex.mobile.core.model.displayText
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.isWaitingOnUserInput
import dev.codex.mobile.core.util.relativeTimeLabel

@Composable
internal fun ThreadFolderHeaderRow(
    section: ThreadFolderSection,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronRotation: Float by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 220),
        label = "folderChevronRotation",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("folder-header-${section.key}")
            .clickable(onClick = onClick)
            .padding(
                start = CodexSpacing.screenHorizontal,
                top = 10.dp,
                end = CodexSpacing.screenHorizontal,
                bottom = 6.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = section.folderName,
                style = MaterialTheme.typography.listItemTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            section.pathSubtitle?.let { pathSubtitle ->
                Text(
                    text = pathSubtitle,
                    style = MaterialTheme.typography.metaText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse folder" else "Expand folder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
        )
    }
}

@Composable
internal fun ThreadFolderThreadRow(
    thread: ThreadSummary,
    resultDigest: ThreadResultDigest?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBackgroundColor: Color = threadRowBackgroundColor(
        thread = thread,
        resultDigest = resultDigest,
    )
    val metadataText: String? = thread.compactListMetadataLabel()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("thread-row-${thread.id}")
            .background(
                color = rowBackgroundColor,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        val inlineDigestText: String? = threadInlineDigestText(thread, resultDigest)
        val shouldShowInlineDigest: Boolean = inlineDigestText != null && resultDigest != null && maxWidth >= 300.dp

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = thread.displayTitle(),
                        style = MaterialTheme.typography.listItemTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (shouldShowInlineDigest) {
                        Text(
                            text = inlineDigestText.orEmpty(),
                            style = MaterialTheme.typography.sectionLabel,
                            color = threadResultDigestColor(resultDigest = resultDigest),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                metadataText?.let { value ->
                    Text(
                        text = value,
                        style = MaterialTheme.typography.metaText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = relativeTimeLabel(thread.updatedAtEpochSeconds),
                style = MaterialTheme.typography.metaText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ThreadFolderShowMoreRow(
    hiddenThreadCount: Int,
    sectionKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("show-more-$sectionKey")
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Show more",
            style = MaterialTheme.typography.metaText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = hiddenThreadCount.toString(),
            style = MaterialTheme.typography.metaText,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

@Composable
internal fun ThreadsEmptyState(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodexSpacing.screenHorizontal, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "No threads found",
            style = MaterialTheme.typography.listItemTitle,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.metaText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun threadInlineDigestText(
    thread: ThreadSummary,
    resultDigest: ThreadResultDigest?,
): String? = if (
    resultDigest != null &&
    thread.status.type != ThreadStatusType.Active &&
    !thread.status.isWaitingOnApproval &&
    !thread.status.isWaitingOnUserInput
) {
    resultDigest.displayText
} else {
    null
}

@Composable
private fun threadRowBackgroundColor(
    thread: ThreadSummary,
    resultDigest: ThreadResultDigest?,
): Color = when {
    resultDigest != null -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f)
    thread.status.isWaitingOnApproval -> Color(0xFFD59734).copy(alpha = 0.1f)
    thread.status.isWaitingOnUserInput -> Color(0xFF3A7BD5).copy(alpha = 0.1f)
    thread.status.type == ThreadStatusType.Active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    else -> Color.Transparent
}

@Composable
private fun threadResultDigestColor(resultDigest: ThreadResultDigest): Color = when (resultDigest.kind) {
    ThreadResultDigestKind.PatchReady -> MaterialTheme.colorScheme.primary
    ThreadResultDigestKind.ReplyReady -> MaterialTheme.colorScheme.primary
    ThreadResultDigestKind.Failed -> MaterialTheme.colorScheme.error
    ThreadResultDigestKind.Completed -> MaterialTheme.colorScheme.onSurfaceVariant
}
