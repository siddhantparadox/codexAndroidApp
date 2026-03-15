package dev.codex.mobile.feature.threaddetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.component.StatusChip
import dev.codex.mobile.core.designsystem.theme.CodexSpacing

@Composable
internal fun PendingRequestUnavailableCard(
    waitingOnApproval: Boolean,
    waitingOnUserInput: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!waitingOnApproval && !waitingOnUserInput) return

    val title: String
    val label: String
    val body: String
    when {
        waitingOnApproval && waitingOnUserInput -> {
            title = "Interactive prompt unavailable"
            label = "Approval + Input"
            body = "This thread is waiting on approval and follow-up input, but this client does not have the active prompt payload. This usually happens when the prompt was opened in another client or before the app reconnected."
        }

        waitingOnApproval -> {
            title = "Approval prompt unavailable"
            label = "Approval"
            body = "This thread is waiting on an approval request, but this client does not have the active prompt payload. This usually happens when the prompt was opened in another client or before the app reconnected."
        }

        else -> {
            title = "Follow-up question unavailable"
            label = "Question"
            body = "This thread is waiting on follow-up input, but this client does not have the active prompt payload. This usually happens when the prompt was opened in another client or before the app reconnected."
        }
    }

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
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(CodexSpacing.listGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(modifier = Modifier.height(CodexSpacing.microGap))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusChip(
                label = label,
                color = Color(0xFFD59734),
            )
        }
        Spacer(modifier = Modifier.height(CodexSpacing.listGap))
        Text(
            text = "Continue the thread on the client that opened the prompt, or ask Codex again from this app to get a new interactive request here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
