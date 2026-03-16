package dev.codex.mobile.feature.approvals

import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ThreadUserInputPayload
import dev.codex.mobile.core.model.ThreadUserInputQuestion
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThreadUserInputOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApprovalQueueModelsTest {
    @Test
    fun buildApprovalQueueEntriesIncludesClassicAndApprovalShapedRequests() {
        val entries = buildApprovalQueueEntries(
            approvals = listOf(
                ApprovalItem(
                    id = "approval-1",
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = "item-1",
                    kind = ApprovalKind.CommandExecution,
                    command = "Get-ChildItem",
                    availableDecisions = listOf(ApprovalDecision.Accept),
                ),
            ),
            userInputRequests = listOf(
                ThreadUserInputRequest(
                    requestId = "request-clarification",
                    threadId = "thread-1",
                    turnId = "turn-1",
                    itemId = "item-2",
                    payload = ThreadUserInputPayload.ToolQuestions(
                        questions = listOf(
                            ThreadUserInputQuestion(
                                id = "q-1",
                                header = "Styles",
                                prompt = "Use the minimal stylesheet only?",
                                options = listOf(
                                    ThreadUserInputOption(
                                        value = "Minimal stylesheet only",
                                        label = "Minimal stylesheet only",
                                    ),
                                ),
                                isOtherAllowed = true,
                            ),
                        ),
                    ),
                ),
                ThreadUserInputRequest(
                    requestId = "request-approval",
                    threadId = "thread-2",
                    turnId = "turn-2",
                    itemId = "item-3",
                    payload = ThreadUserInputPayload.ToolQuestions(
                        questions = listOf(
                            ThreadUserInputQuestion(
                                id = "q-approval",
                                header = "Approval",
                                prompt = "Allow the GitHub tool call to continue?",
                                options = listOf(
                                    ThreadUserInputOption(value = "Accept", label = "Accept"),
                                    ThreadUserInputOption(value = "Decline", label = "Decline"),
                                    ThreadUserInputOption(value = "Cancel", label = "Cancel"),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(2, entries.size)
        assertTrue(entries[0] is ApprovalQueueEntry.Standard)
        assertTrue(entries[1] is ApprovalQueueEntry.ToolPrompt)
    }
}
