package dev.codex.mobile.feature.threaddetail

import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
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
                    questions = listOf(
                        ThreadUserInputQuestion(
                            id = "q-1",
                            header = "Styles",
                            prompt = "Use the minimal stylesheet?",
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
                    questions = listOf(
                        ThreadUserInputQuestion(
                            id = "q-1",
                            header = "Styles",
                            prompt = "Use the minimal stylesheet?",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(rows.single() is TranscriptRow.OrphanUserInputRequest)
    }
}
