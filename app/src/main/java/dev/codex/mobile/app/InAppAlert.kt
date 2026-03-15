package dev.codex.mobile.app

import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ThreadSummary

internal const val APPROVAL_ALERT_PREFIX: String = "approval:"
internal const val CONNECTION_ALERT_PREFIX: String = "connection:"

internal data class InAppAlert(
    val id: String,
    val title: String,
    val message: String,
    val threadId: String? = null,
)

internal fun buildRootInAppAlerts(
    preferences: AppPreferences,
    hosts: List<HostProfile>,
    connection: ConnectionState,
    approvals: List<ApprovalItem>,
    threadNotifications: List<InAppThreadNotification>,
    threads: List<ThreadSummary>,
    visibleThreadId: String?,
    dismissedApprovalAlertIds: Set<String>,
    dismissedConnectionAlertId: String?,
): List<InAppAlert> {
    val threadAlerts: List<InAppAlert> = threadNotifications.map { notification ->
        InAppAlert(
            id = notification.id,
            title = notification.title,
            message = notification.message,
            threadId = notification.threadId,
        )
    }
    if (!preferences.connectionAlerts) {
        return threadAlerts
    }

    val threadsById: Map<String, ThreadSummary> = threads.associateBy(ThreadSummary::id)
    val connectionAlert: InAppAlert? = buildConnectionAlert(
        connection = connection,
        hosts = hosts,
    )?.takeUnless { alert -> alert.id == dismissedConnectionAlertId }
    val approvalAlerts: List<InAppAlert> = approvals
        .asSequence()
        .filterNot { approval -> approval.threadId == visibleThreadId }
        .map { approval ->
            approval.toInAppAlert(
                threadTitle = threadsById[approval.threadId]?.name,
            )
        }
        .filterNot { alert -> alert.id in dismissedApprovalAlertIds }
        .toList()

    return buildList {
        connectionAlert?.let(::add)
        addAll(approvalAlerts)
        addAll(threadAlerts)
    }
}

internal fun approvalAlertId(approvalId: String): String = "$APPROVAL_ALERT_PREFIX$approvalId"

internal fun connectionAlertId(connection: ConnectionState): String? {
    val activeHostId: String = connection.activeHostId ?: return null
    val phase: ConnectionPhase = connection.phase
    if (phase != ConnectionPhase.Disconnected && phase != ConnectionPhase.Error) {
        return null
    }
    return buildString {
        append(CONNECTION_ALERT_PREFIX)
        append(activeHostId)
        append(':')
        append(phase.name)
        append(':')
        append(connection.message.orEmpty())
    }
}

private fun buildConnectionAlert(
    connection: ConnectionState,
    hosts: List<HostProfile>,
): InAppAlert? {
    val alertId: String = connectionAlertId(connection) ?: return null
    val hostName: String = hosts.firstOrNull { host -> host.id == connection.activeHostId }?.name ?: "Desktop"
    val title: String = when (connection.phase) {
        ConnectionPhase.Disconnected -> "$hostName went offline"
        ConnectionPhase.Error -> "$hostName connection error"
        else -> return null
    }
    val message: String = connection.message
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.singleLinePreview()
        ?: "Run codexremote on your desktop and reconnect."

    return InAppAlert(
        id = alertId,
        title = title,
        message = message,
    )
}

private fun ApprovalItem.toInAppAlert(threadTitle: String?): InAppAlert = InAppAlert(
    id = approvalAlertId(id),
    title = "${threadTitle?.takeIf(String::isNotBlank) ?: "Thread"} needs approval",
    message = approvalAlertMessage(),
    threadId = threadId,
)

private fun ApprovalItem.approvalAlertMessage(): String = when (kind) {
    ApprovalKind.CommandExecution -> command
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?.singleLinePreview()
        ?: reason
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.singleLinePreview()
            ?: "Review the requested command before Codex continues."

    ApprovalKind.FileChange -> filePaths.firstOrNull()
        ?.let { firstPath ->
            if (filePaths.size == 1) {
                firstPath
            } else {
                "$firstPath +${filePaths.size - 1} more"
            }
        }
        ?.singleLinePreview()
        ?: reason
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.singleLinePreview()
            ?: "Review the proposed file changes before Codex continues."
}

private fun String.singleLinePreview(maxLength: Int = 120): String = lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(separator = " ")
    .let { collapsed ->
        if (collapsed.length <= maxLength) {
            collapsed
        } else {
            collapsed.take(maxLength - 1).trimEnd() + "…"
        }
    }
