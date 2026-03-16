package dev.codex.mobile.feature.approvals

import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.isApprovalPrompt

sealed interface ApprovalQueueEntry {
    val id: String
    val threadId: String

    data class Standard(
        val approval: ApprovalItem,
    ) : ApprovalQueueEntry {
        override val id: String = approval.id
        override val threadId: String = approval.threadId
    }

    data class ToolPrompt(
        val request: ThreadUserInputRequest,
    ) : ApprovalQueueEntry {
        override val id: String = "tool-${request.requestId}"
        override val threadId: String = request.threadId
    }
}

internal fun buildApprovalQueueEntries(
    approvals: List<ApprovalItem>,
    userInputRequests: List<ThreadUserInputRequest>,
): List<ApprovalQueueEntry> = buildList {
    addAll(approvals.map(ApprovalQueueEntry::Standard))
    addAll(
        userInputRequests
            .filter(ThreadUserInputRequest::isApprovalPrompt)
            .map(ApprovalQueueEntry::ToolPrompt),
    )
}
