package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.model.ThreadDynamicToolKind
import dev.codex.mobile.core.model.ThreadDynamicToolRequest

private val DynamicToolAccent: Color = Color(0xFF3D9A78)

@Composable
internal fun InlineDynamicToolRequestCard(
    request: ThreadDynamicToolRequest,
    onChoosePhoto: (ThreadDynamicToolRequest) -> Unit,
    onCancel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    CodexCard(
        modifier = modifier.fillMaxWidth(),
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
                Icon(
                    imageVector = Icons.Rounded.Image,
                    contentDescription = null,
                    tint = DynamicToolAccent,
                )
                Spacer(modifier = Modifier.width(CodexSpacing.listGap))
                androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = requestTitle(request),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = request.prompt ?: defaultPrompt(request.kind),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(CodexSpacing.listGap))
            StatusChip(
                label = "Tool Request",
                color = DynamicToolAccent,
            )
        }

        Spacer(modifier = Modifier.height(CodexSpacing.sectionGap))
        when (request.kind) {
            ThreadDynamicToolKind.PickPhoto -> {
                Button(
                    onClick = { onChoosePhoto(request) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Choose Photo")
                }
            }
        }

        Spacer(modifier = Modifier.height(CodexSpacing.tightGap))
        OutlinedButton(
            onClick = { onCancel(request.requestId) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(text = "Cancel")
        }
    }
}

private fun requestTitle(request: ThreadDynamicToolRequest): String = when (request.kind) {
    ThreadDynamicToolKind.PickPhoto -> "Codex wants a photo"
}

private fun defaultPrompt(kind: ThreadDynamicToolKind): String = when (kind) {
    ThreadDynamicToolKind.PickPhoto -> "Choose one image from your device to continue this turn."
}
