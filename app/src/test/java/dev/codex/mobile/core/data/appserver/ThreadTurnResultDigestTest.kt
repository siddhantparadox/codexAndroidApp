package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.ThreadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.jsonObject

class ThreadTurnResultDigestTest {
    @Test
    fun buildsPatchReadyDigestFromFileChanges() {
        val digest = buildThreadResultDigest(
            items = listOf(
                ThreadItem.FileChange(
                    id = "file-change-1",
                    changes = listOf(
                        FileChangeEntry(
                            path = "src/Auth.kt",
                            kind = "Update",
                            diff = """
                                --- a/src/Auth.kt
                                +++ b/src/Auth.kt
                                @@ -1,2 +1,3 @@
                                -oldValue()
                                +newValue()
                                +audit()
                            """.trimIndent(),
                        ),
                    ),
                    status = dev.codex.mobile.core.model.ThreadItemStatus.Completed,
                ),
            ),
            turnStatus = "completed",
            turnError = null,
        )

        assertNotNull(digest)
        assertEquals("Patch ready", digest?.title)
        assertEquals("1 file · +2 -1", digest?.supportingText)
        assertEquals(1, digest?.fileCount)
    }

    @Test
    fun buildsReplyReadyDigestFromFinalAgentMessage() {
        val digest = buildThreadResultDigest(
            items = listOf(
                ThreadItem.AgentMessage(
                    id = "agent-1",
                    text = "Shipped the change and verified the thread list refresh path.",
                    phase = "final_answer",
                ),
            ),
            turnStatus = "completed",
            turnError = null,
        )

        assertNotNull(digest)
        assertEquals("Reply ready", digest?.title)
        assertEquals(
            "Shipped the change and verified the thread list refresh path.",
            digest?.supportingText,
        )
    }

    @Test
    fun buildsFailedDigestFromTurnError() {
        val digest = buildThreadResultDigest(
            items = listOf(
                ThreadItem.CommandExecution(
                    id = "command-1",
                    command = "./gradlew test",
                    cwd = null,
                    status = dev.codex.mobile.core.model.ThreadItemStatus.Failed,
                    aggregatedOutput = "Execution failed",
                ),
            ),
            turnStatus = "failed",
            turnError = "Gradle daemon crashed.",
        )

        assertNotNull(digest)
        assertEquals("Command failed", digest?.title)
        assertEquals("Gradle daemon crashed.", digest?.supportingText)
    }

    @Test
    fun toThreadTurnResultUsesRequestedTurnInsteadOfLatestHistory() {
        val thread = appServerJson.parseToJsonElement(
            """
                {
                  "id": "thread-1",
                  "name": "Refresh threads",
                  "preview": "Done",
                  "createdAt": 1,
                  "updatedAt": 2,
                  "modelProvider": "openai",
                  "ephemeral": false,
                  "status": { "type": "idle" },
                  "turns": [
                    {
                      "id": "turn-1",
                      "status": "completed",
                      "items": [
                        {
                          "id": "file-change-1",
                          "type": "fileChange",
                          "status": "completed",
                          "changes": [
                            {
                              "path": "src/Threads.kt",
                              "kind": { "type": "update" },
                              "diff": "@@ -1 +1 @@\n-old\n+new"
                            }
                          ]
                        }
                      ]
                    },
                    {
                      "id": "turn-2",
                      "status": "completed",
                      "items": [
                        {
                          "id": "agent-2",
                          "type": "agentMessage",
                          "text": "Later reply"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        ).jsonObject

        val result = thread.toThreadTurnResult(
            turnId = "turn-1",
            fallbackTurnStatus = "completed",
            fallbackTurnError = null,
        )

        assertNotNull(result)
        assertEquals("Patch ready", result?.digest?.title)
    }

    @Test
    fun toThreadTurnResultSkipsInterruptedTurns() {
        val thread = appServerJson.parseToJsonElement(
            """
                {
                  "id": "thread-1",
                  "name": "Refresh threads",
                  "preview": "Done",
                  "createdAt": 1,
                  "updatedAt": 2,
                  "modelProvider": "openai",
                  "ephemeral": false,
                  "status": { "type": "idle" },
                  "turns": [
                    {
                      "id": "turn-1",
                      "status": "interrupted",
                      "items": []
                    }
                  ]
                }
            """.trimIndent(),
        ).jsonObject

        val result = thread.toThreadTurnResult(
            turnId = "turn-1",
            fallbackTurnStatus = "interrupted",
            fallbackTurnError = null,
        )

        assertNull(result)
    }

    @Test
    fun toThreadTurnResultReturnsNullWhenRequestedTurnIsMissing() {
        val thread = appServerJson.parseToJsonElement(
            """
                {
                  "id": "thread-1",
                  "name": "Refresh threads",
                  "preview": "Done",
                  "createdAt": 1,
                  "updatedAt": 2,
                  "modelProvider": "openai",
                  "ephemeral": false,
                  "status": { "type": "idle" },
                  "turns": [
                    {
                      "id": "turn-1",
                      "status": "completed",
                      "items": [
                        {
                          "id": "agent-1",
                          "type": "agentMessage",
                          "text": "Later reply"
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
        ).jsonObject

        val result = thread.toThreadTurnResult(
            turnId = "turn-2",
            fallbackTurnStatus = "completed",
            fallbackTurnError = null,
        )

        assertNull(result)
    }
}
