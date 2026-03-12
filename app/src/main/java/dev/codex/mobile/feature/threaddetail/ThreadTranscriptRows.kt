package dev.codex.mobile.feature.threaddetail

import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ThreadItem

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
    ) : TranscriptRow

    data class OrphanApproval(
        val approval: ApprovalItem,
    ) : TranscriptRow {
        override val id: String = approval.id
    }
}

internal fun buildTranscriptRows(
    items: List<ThreadItem>,
    approvals: List<ApprovalItem>,
): List<TranscriptRow> {
    if (items.isEmpty() && approvals.isEmpty()) return emptyList()

    val itemIds: Set<String> = items.map { item -> item.id }.toSet()
    val approvalsByItemId: Map<String, List<ApprovalItem>> = approvals
        .filter { approval -> approval.itemId in itemIds }
        .groupBy { approval -> approval.itemId }
    val orphanApprovals: List<ApprovalItem> = approvals.filter { approval -> approval.itemId !in itemIds }

    val rows: MutableList<TranscriptRow> = mutableListOf()
    var index: Int = 0
    while (index < items.size) {
        when (val item: ThreadItem = items[index]) {
            is ThreadItem.UserMessage -> {
                rows += TranscriptRow.UserMessage(item)
                index += 1
            }

            is ThreadItem.AgentMessage -> {
                rows += TranscriptRow.AgentMessage(item)
                index += 1
            }

            else -> {
                val stripItems: MutableList<ThreadItem> = mutableListOf()
                val stripApprovals: MutableList<ApprovalItem> = mutableListOf()

                while (index < items.size) {
                    val nextItem: ThreadItem = items[index]
                    if (nextItem is ThreadItem.UserMessage || nextItem is ThreadItem.AgentMessage) {
                        break
                    }
                    stripItems += nextItem
                    stripApprovals += approvalsByItemId[nextItem.id].orEmpty()
                    index += 1
                }

                if (stripItems.isNotEmpty()) {
                    rows += TranscriptRow.TechnicalStrip(
                        id = "strip-${stripItems.first().id}-${stripItems.last().id}",
                        items = stripItems,
                        approvals = stripApprovals,
                    )
                }
            }
        }
    }

    rows += orphanApprovals.map { approval -> TranscriptRow.OrphanApproval(approval) }
    return rows
}
