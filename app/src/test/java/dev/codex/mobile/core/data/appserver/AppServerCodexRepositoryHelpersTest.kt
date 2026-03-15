package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.ComposerSandboxMode
import dev.codex.mobile.core.model.ThreadItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class AppServerCodexRepositoryHelpersTest {
    @Test
    fun preservesThreadCacheOnColdStartReconnectToPersistedHost() {
        val shouldPreserve = shouldPreserveThreadCache(
            previousActiveHostId = null,
            nextActiveHostId = "host-1",
            currentThreadItemCache = mapOf(
                "thread-1" to listOf(
                    ThreadItem.AgentMessage(id = "item-1", text = "cached"),
                ),
            ),
        )

        assertTrue(shouldPreserve)
    }

    @Test
    fun doesNotPreserveThreadCacheWhenSwitchingHosts() {
        val shouldPreserve = shouldPreserveThreadCache(
            previousActiveHostId = "host-1",
            nextActiveHostId = "host-2",
            currentThreadItemCache = mapOf(
                "thread-1" to listOf(
                    ThreadItem.AgentMessage(id = "item-1", text = "cached"),
                ),
            ),
        )

        assertFalse(shouldPreserve)
    }

    @Test
    fun prefersRicherExistingTranscriptWhenSnapshotIsThinner() {
        val existingItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.WebSearch(id = "item-2", query = "animals"),
            ThreadItem.AgentMessage(id = "item-3", text = "answer"),
        )
        val snapshotItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.AgentMessage(id = "item-3", text = "answer"),
        )

        val mergedItems = mergeThreadItems(
            existingItems = existingItems,
            snapshotItems = snapshotItems,
        )

        assertEquals(existingItems, mergedItems)
    }

    @Test
    fun readOnlySandboxUsesOnRequestApprovalPolicy() {
        val payload = ComposerSandboxMode.ReadOnly.toApprovalPolicyPayload()

        assertEquals("on-request", payload?.content)
    }

    @Test
    fun defaultSandboxDoesNotOverrideApprovalPolicy() {
        val payload = ComposerSandboxMode.Default.toApprovalPolicyPayload()

        assertNull(payload)
    }
}
