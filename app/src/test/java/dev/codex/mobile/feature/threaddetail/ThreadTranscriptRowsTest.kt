package dev.codex.mobile.feature.threaddetail

import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ThreadDynamicToolKind
import dev.codex.mobile.core.model.ThreadDynamicToolRequest
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadUserInputPayload
import dev.codex.mobile.core.model.ThreadUserInputQuestion
import dev.codex.mobile.core.model.ThreadUserInputRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            dynamicToolRequests = emptyList(),
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
            dynamicToolRequests = emptyList(),
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
            dynamicToolRequests = emptyList(),
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
            dynamicToolRequests = emptyList(),
        )

        assertEquals(2, rows.size)
        assertTrue(rows[0] is TranscriptRow.AgentMessage)
        assertTrue(rows[1] is TranscriptRow.ApprovalCard)
    }

    @Test
    fun appendsPendingAgentPlaceholderWhenRequested() {
        val rows = buildTranscriptRows(
            items = listOf(
                ThreadItem.UserMessage(id = "user-1", text = "Check this"),
            ),
            approvals = emptyList(),
            userInputRequests = emptyList(),
            dynamicToolRequests = emptyList(),
            showPendingAgentPlaceholder = true,
        )

        assertEquals(2, rows.size)
        assertTrue(rows.last() === TranscriptRow.PendingAgentPlaceholder)
    }

    @Test
    fun showsPendingAgentPlaceholderOnlyWhileActiveTurnHasNoVisibleActiveItems() {
        val userMessage = ThreadItem.UserMessage(
            id = "user-1",
            text = "Check this",
        )

        assertTrue(
            shouldShowPendingAgentPlaceholder(
                status = ThreadStatus(type = ThreadStatusType.Active),
                items = listOf(userMessage),
                activeItemIds = emptySet(),
                approvals = emptyList(),
                userInputRequests = emptyList(),
                dynamicToolRequests = emptyList(),
            ),
        )
        assertFalse(
            shouldShowPendingAgentPlaceholder(
                status = ThreadStatus(type = ThreadStatusType.Active),
                items = listOf(userMessage),
                activeItemIds = setOf(userMessage.id),
                approvals = emptyList(),
                userInputRequests = emptyList(),
                dynamicToolRequests = emptyList(),
            ),
        )
        assertFalse(
            shouldShowPendingAgentPlaceholder(
                status = ThreadStatus(
                    type = ThreadStatusType.Active,
                    activeFlags = setOf("waitingOnApproval"),
                ),
                items = listOf(userMessage),
                activeItemIds = emptySet(),
                approvals = emptyList(),
                userInputRequests = emptyList(),
                dynamicToolRequests = emptyList(),
            ),
        )
    }

    @Test
    fun attachesDynamicToolRequestAfterTechnicalStrip() {
        val item = ThreadItem.DynamicToolCall(
            id = "dynamic-1",
            tool = "pick_photo",
            status = ThreadItemStatus.InProgress,
            arguments = """{"reason":"Attach a screenshot"}""",
        )

        val rows = buildTranscriptRows(
            items = listOf(
                ThreadItem.UserMessage(id = "user-1", text = "Look at my screenshot"),
                item,
            ),
            approvals = emptyList(),
            userInputRequests = emptyList(),
            dynamicToolRequests = listOf(
                ThreadDynamicToolRequest(
                    requestId = "dynamic-request-1",
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = item.id,
                    tool = "pick_photo",
                    kind = ThreadDynamicToolKind.PickPhoto,
                    prompt = "Attach a screenshot",
                ),
            ),
        )

        assertEquals(3, rows.size)
        assertTrue(rows[1] is TranscriptRow.TechnicalStrip)
        assertTrue(rows[2] is TranscriptRow.DynamicToolRequestCard)
    }
}
