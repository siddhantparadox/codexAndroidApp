package dev.codex.mobile.feature.threaddetail

import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadUserInputRequest

internal sealed interface TranscriptRow {
    val id: String

    data class UserMessage(
        val item: ThreadItem.UserMessage,
    ) : TranscriptRow {
        override val id: String = item.id
    }

    data class AgentMessage(
        val item: ThreadItem.AgentMessage,
    ) : TranscriptRow {
        override val id: String = item.id
    }

    data class TechnicalStrip(
        override val id: String,
        val items: List<ThreadItem>,
        val approvals: List<ApprovalItem>,
        val userInputRequests: List<ThreadUserInputRequest>,
    ) : TranscriptRow

    data class ApprovalCard(
        val approval: ApprovalItem,
    ) : TranscriptRow {
        override val id: String = "approval-${approval.id}"
    }

    data class UserInputRequestCard(
        val request: ThreadUserInputRequest,
    ) : TranscriptRow {
        override val id: String = "user-input-${request.requestId}"
    }
}

internal fun buildTranscriptRows(
    items: List<ThreadItem>,
    approvals: List<ApprovalItem>,
    userInputRequests: List<ThreadUserInputRequest>,
): List<TranscriptRow> {
    if (items.isEmpty() && approvals.isEmpty() && userInputRequests.isEmpty()) return emptyList()

    val itemIds: Set<String> = items.map { item -> item.id }.toSet()
    val approvalsByItemId: Map<String, List<ApprovalItem>> = approvals
        .filter { approval -> approval.itemId in itemIds }
        .groupBy { approval -> approval.itemId }
    val userInputRequestsByItemId: Map<String, List<ThreadUserInputRequest>> = userInputRequests
        .filter { request -> request.itemId != null && request.itemId in itemIds }
        .groupBy { request -> requireNotNull(request.itemId) }
    val orphanApprovals: List<ApprovalItem> = approvals.filter { approval -> approval.itemId !in itemIds }
    val orphanUserInputRequests: List<ThreadUserInputRequest> = userInputRequests
        .filter { request -> request.itemId == null || request.itemId !in itemIds }

    val rows: MutableList<TranscriptRow> = mutableListOf()
    var index: Int = 0
    while (index < items.size) {
        when (val item: ThreadItem = items[index]) {
            is ThreadItem.UserMessage -> {
                rows += TranscriptRow.UserMessage(item)
                rows += approvalsByItemId[item.id].orEmpty().map { approval ->
                    TranscriptRow.ApprovalCard(approval)
                }
                rows += userInputRequestsByItemId[item.id].orEmpty().map { request ->
                    TranscriptRow.UserInputRequestCard(request)
                }
                index += 1
            }

            is ThreadItem.AgentMessage -> {
                rows += TranscriptRow.AgentMessage(item)
                rows += approvalsByItemId[item.id].orEmpty().map { approval ->
                    TranscriptRow.ApprovalCard(approval)
                }
                rows += userInputRequestsByItemId[item.id].orEmpty().map { request ->
                    TranscriptRow.UserInputRequestCard(request)
                }
                index += 1
            }

            else -> {
                val stripItems: MutableList<ThreadItem> = mutableListOf()
                val stripApprovals: MutableList<ApprovalItem> = mutableListOf()
                val stripUserInputRequests: MutableList<ThreadUserInputRequest> = mutableListOf()

                while (index < items.size) {
                    val nextItem: ThreadItem = items[index]
                    if (nextItem is ThreadItem.UserMessage || nextItem is ThreadItem.AgentMessage) {
                        break
                    }
                    stripItems += nextItem
                    stripApprovals += approvalsByItemId[nextItem.id].orEmpty()
                    stripUserInputRequests += userInputRequestsByItemId[nextItem.id].orEmpty()
                    index += 1
                }

                if (stripItems.isNotEmpty()) {
                    rows += TranscriptRow.TechnicalStrip(
                        id = "strip-${stripItems.first().id}-${stripItems.last().id}",
                        items = stripItems,
                        approvals = stripApprovals,
                        userInputRequests = stripUserInputRequests,
                    )
                }
            }
        }
    }

    rows += orphanApprovals.map { approval -> TranscriptRow.ApprovalCard(approval) }
    rows += orphanUserInputRequests.map { request -> TranscriptRow.UserInputRequestCard(request) }
    return rows
}
