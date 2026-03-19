package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppServerThreadItemMapperTest {
    @Test
    fun collabToolCallMapperAcceptsDocumentedSingularFields() {
        val item = buildJsonObject {
            put("type", "collabToolCall")
            put("id", "item-1")
            put("tool", "delegate")
            put("status", "completed")
            put("senderThreadId", "thread-parent")
            put("receiverThreadId", "thread-child")
            put("newThreadId", "thread-child")
            put("prompt", "Investigate the regression")
            putJsonObject("agentStatus") {
                put("status", "completed")
                put("message", "Done")
            }
        }.toThreadItem()

        assertTrue(item is ThreadItem.CollabToolCall)
        val collabItem = item as ThreadItem.CollabToolCall
        assertEquals("delegate", collabItem.tool)
        assertEquals(ThreadItemStatus.Completed, collabItem.status)
        assertEquals(listOf("thread-child"), collabItem.receiverThreadIds)
        assertEquals("Investigate the regression", collabItem.prompt)
        assertEquals(1, collabItem.agentStates.size)
        assertEquals("thread-child", collabItem.agentStates.single().threadId)
        assertEquals("completed", collabItem.agentStates.single().status)
        assertEquals("Done", collabItem.agentStates.single().message)
    }
}
