package dev.codex.mobile.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codex.mobile.core.designsystem.component.CodexCard
import dev.codex.mobile.core.designsystem.theme.CodexSpacing
import dev.codex.mobile.core.designsystem.theme.cardBorder
import dev.codex.mobile.core.model.InAppThreadNotification
import kotlinx.coroutines.delay

@Composable
internal fun InAppThreadNotificationHost(
    notifications: List<InAppThreadNotification>,
    onOpenThread: (String) -> Unit,
    onDismissNotification: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notifications.isEmpty()) return

    val visibleNotifications: List<InAppThreadNotification> = notifications.take(MAX_VISIBLE_NOTIFICATIONS)
    val overflowCount: Int = (notifications.size - visibleNotifications.size).coerceAtLeast(0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                horizontal = CodexSpacing.screenHorizontal,
                vertical = CodexSpacing.screenTop,
            ),
        verticalArrangement = Arrangement.spacedBy(CodexSpacing.compactGap),
    ) {
        visibleNotifications.forEach { notification ->
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(initialOffsetY = { offset -> -offset / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { offset -> -offset / 2 }) + fadeOut(),
            ) {
                ThreadNotificationBanner(
                    notification = notification,
                    onOpenThread = onOpenThread,
                    onDismiss = onDismissNotification,
                )
            }
        }
        if (overflowCount > 0) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.cardBorder),
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "+$overflowCount more",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadNotificationBanner(
    notification: InAppThreadNotification,
    onOpenThread: (String) -> Unit,
    onDismiss: (String) -> Unit,
) {
    LaunchedEffect(notification.id) {
        delay(NOTIFICATION_AUTO_DISMISS_MS)
        onDismiss(notification.id)
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDismiss(notification.id)
            }
            true
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
    ) {
        CodexCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onOpenThread(notification.threadId)
                    onDismiss(notification.id)
                },
        ) {
            Text(
                text = notification.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = CodexSpacing.tightGap),
            )
        }
    }
}

private const val MAX_VISIBLE_NOTIFICATIONS: Int = 3
private const val NOTIFICATION_AUTO_DISMISS_MS: Long = 4_000L
