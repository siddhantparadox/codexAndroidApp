package dev.codex.mobile.feature.threaddetail

import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadUserInputPayload
import dev.codex.mobile.core.model.ThreadUserInputQuestion
import dev.codex.mobile.core.model.ThreadUserInputRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadTranscriptRowsTest {
    @Test
    fun attachesUserInputRequestToMatchingTechnicalStrip() {
        val item = ThreadItem.CommandExecution(
            id = "cmd-1",
            command = "npm test",
            status = ThreadItemStatus.InProgress,
        )

        val rows = buildTranscriptRows(
            items = listOf(
                ThreadItem.UserMessage(id = "user-1", text = "Run the checks"),
                item,
            ),
            approvals = emptyList(),
            userInputRequests = listOf(
                ThreadUserInputRequest(
                    requestId = "req-1",
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = item.id,
                    payload = ThreadUserInputPayload.ToolQuestions(
                        questions = listOf(
                            ThreadUserInputQuestion(
                                id = "q-1",
                                header = "Styles",
                                prompt = "Use the minimal stylesheet?",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val technicalStrip = rows.filterIsInstance<TranscriptRow.TechnicalStrip>().single()
        assertEquals(1, technicalStrip.userInputRequests.size)
        assertEquals("req-1", technicalStrip.userInputRequests.single().requestId)
    }

    @Test
    fun createsOrphanRowWhenRelatedItemIsMissing() {
        val rows = buildTranscriptRows(
            items = emptyList(),
            approvals = emptyList(),
            userInputRequests = listOf(
                ThreadUserInputRequest(
                    requestId = "req-1",
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = "missing-item",
                    payload = ThreadUserInputPayload.ToolQuestions(
                        questions = listOf(
                            ThreadUserInputQuestion(
                                id = "q-1",
                                header = "Styles",
                                prompt = "Use the minimal stylesheet?",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(rows.single() is TranscriptRow.UserInputRequestCard)
    }

    @Test
    fun attachesUserInputRequestAfterAgentMessageWhenRequestTargetsMessageItem() {
        val agentMessage = ThreadItem.AgentMessage(
            id = "agent-1",
            text = "Which direction do you want?",
        )

        val rows = buildTranscriptRows(
            items = listOf(
                ThreadItem.UserMessage(id = "user-1", text = "Make css different now"),
                agentMessage,
            ),
            approvals = emptyList(),
            userInputRequests = listOf(
                ThreadUserInputRequest(
                    requestId = "req-1",
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = agentMessage.id,
                    payload = ThreadUserInputPayload.ToolQuestions(
                        questions = listOf(
                            ThreadUserInputQuestion(
                                id = "q-1",
                                header = "Styles",
                                prompt = "Which direction do you want instead?",
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(3, rows.size)
        assertTrue(rows[0] is TranscriptRow.UserMessage)
        assertTrue(rows[1] is TranscriptRow.AgentMessage)
        assertTrue(rows[2] is TranscriptRow.UserInputRequestCard)
    }

    @Test
    fun attachesApprovalAfterAgentMessageWhenApprovalTargetsMessageItem() {
        val agentMessage = ThreadItem.AgentMessage(
            id = "agent-1",
            text = "Need approval",
        )

        val rows = buildTranscriptRows(
            items = listOf(agentMessage),
            approvals = listOf(
                ApprovalItem(
                    id = "approval-1",
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = agentMessage.id,
                    kind = ApprovalKind.Permissions,
                    reason = "Need write access",
                    availableDecisions = listOf(
                        ApprovalDecision.Accept,
                        ApprovalDecision.Decline,
                    ),
                ),
            ),
            userInputRequests = emptyList(),
        )

        assertEquals(2, rows.size)
        assertTrue(rows[0] is TranscriptRow.AgentMessage)
        assertTrue(rows[1] is TranscriptRow.ApprovalCard)
    }
}
