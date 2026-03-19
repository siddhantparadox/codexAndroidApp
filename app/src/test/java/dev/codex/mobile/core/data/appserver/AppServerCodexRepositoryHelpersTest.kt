package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.ComposerSandboxMode
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun preservesLiveDesktopStateWhenSameHostWasAlreadyConnected() {
        val shouldPreserve = shouldPreserveLiveDesktopState(
            previousActiveHostId = "host-1",
            nextActiveHostId = "host-1",
            previousPhase = ConnectionPhase.Connected,
            currentThreads = emptyList(),
            currentThreadDetails = emptyMap(),
        )

        assertTrue(shouldPreserve)
    }

    @Test
    fun doesNotPreserveLiveDesktopStateWhenSwitchingHosts() {
        val shouldPreserve = shouldPreserveLiveDesktopState(
            previousActiveHostId = "host-1",
            nextActiveHostId = "host-2",
            previousPhase = ConnectionPhase.Connected,
            currentThreads = emptyList(),
            currentThreadDetails = emptyMap(),
        )

        assertFalse(shouldPreserve)
    }

    @Test
    fun usesReconnectingPhaseWhenDesktopStateCanResume() {
        val phase = connectionPhaseForAttempt(
            previousActiveHostId = "host-1",
            nextActiveHostId = "host-1",
            previousPhase = ConnectionPhase.Connected,
            currentThreads = emptyList(),
            currentThreadDetails = emptyMap(),
        )

        assertEquals(ConnectionPhase.Reconnecting, phase)
    }

    @Test
    fun usesConnectingPhaseForFreshHostConnection() {
        val phase = connectionPhaseForAttempt(
            previousActiveHostId = null,
            nextActiveHostId = "host-1",
            previousPhase = ConnectionPhase.Idle,
            currentThreads = emptyList(),
            currentThreadDetails = emptyMap(),
        )

        assertEquals(ConnectionPhase.Connecting, phase)
    }

    @Test
    fun desktopConnectionMessageIncludesRetryDelayForReconnects() {
        assertEquals(
            "Reconnecting to Work Desktop in 8s",
            desktopConnectionMessage(
                hostName = "Work Desktop",
                phase = ConnectionPhase.Reconnecting,
                nextRetryDelayMs = 8_000L,
            ),
        )
    }

    @Test
    fun desktopConnectionMessageIsNullWhenAlreadyConnected() {
        assertNull(
            desktopConnectionMessage(
                hostName = "Work Desktop",
                phase = ConnectionPhase.Connected,
            ),
        )
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
    fun authoritativeConversationOnlySnapshotPreservesTechnicalHistory() {
        val existingItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.AgentMessage(id = "item-2", text = "commentary", phase = "commentary"),
            ThreadItem.WebSearch(id = "item-3", query = "stale"),
        )
        val snapshotItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.AgentMessage(id = "item-2", text = "commentary", phase = "commentary"),
            ThreadItem.AgentMessage(
                id = "item-4",
                text = "Do you want the new section to cover OpenAI API pricing or ChatGPT subscription pricing?",
                phase = "final_answer",
            ),
        )

        val mergedItems = mergeThreadItems(
            existingItems = existingItems,
            snapshotItems = snapshotItems,
            snapshotIsAuthoritative = true,
        )

        assertEquals(
            listOf(
                ThreadItem.UserMessage(id = "item-1", text = "user"),
                ThreadItem.AgentMessage(id = "item-2", text = "commentary", phase = "commentary"),
                ThreadItem.WebSearch(id = "item-3", query = "stale"),
                ThreadItem.AgentMessage(
                    id = "item-4",
                    text = "Do you want the new section to cover OpenAI API pricing or ChatGPT subscription pricing?",
                    phase = "final_answer",
                ),
            ),
            mergedItems,
        )
    }

    @Test
    fun authoritativeSnapshotMissingSomeTechnicalItemsPreservesExistingHistory() {
        val existingItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.Reasoning(id = "item-2", summary = "thinking", contentText = "thinking"),
            ThreadItem.CommandExecution(
                id = "item-3",
                command = "rg \"tool call\"",
                status = dev.codex.mobile.core.model.ThreadItemStatus.Completed,
            ),
        )
        val snapshotItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.Reasoning(id = "item-2", summary = "thinking harder", contentText = "thinking harder"),
            ThreadItem.AgentMessage(id = "item-4", text = "answer"),
        )

        val mergedItems = mergeThreadItems(
            existingItems = existingItems,
            snapshotItems = snapshotItems,
            snapshotIsAuthoritative = true,
        )

        assertEquals(
            listOf(
                ThreadItem.UserMessage(id = "item-1", text = "user"),
                ThreadItem.Reasoning(id = "item-2", summary = "thinking harder", contentText = "thinking harder"),
                ThreadItem.CommandExecution(
                    id = "item-3",
                    command = "rg \"tool call\"",
                    status = dev.codex.mobile.core.model.ThreadItemStatus.Completed,
                ),
                ThreadItem.AgentMessage(id = "item-4", text = "answer"),
            ),
            mergedItems,
        )
    }

    @Test
    fun authoritativeSnapshotThatContainsKnownTechnicalItemsReplacesCache() {
        val existingItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.WebSearch(id = "item-2", query = "stale"),
            ThreadItem.AgentMessage(id = "item-3", text = "answer"),
        )
        val snapshotItems = listOf(
            ThreadItem.UserMessage(id = "item-1", text = "user"),
            ThreadItem.WebSearch(id = "item-2", query = "fresh"),
            ThreadItem.CommandExecution(
                id = "item-4",
                command = "rg \"tool call\"",
                status = dev.codex.mobile.core.model.ThreadItemStatus.Completed,
            ),
            ThreadItem.AgentMessage(id = "item-5", text = "answer"),
        )

        val mergedItems = mergeThreadItems(
            existingItems = existingItems,
            snapshotItems = snapshotItems,
            snapshotIsAuthoritative = true,
        )

        assertEquals(snapshotItems, mergedItems)
    }

    @Test
    fun completedSnapshotWithoutActiveTurnIsAuthoritative() {
        val shouldUseSnapshot = shouldUseAuthoritativeThreadSnapshot(
            snapshotItems = listOf(
                ThreadItem.AgentMessage(id = "item-1", text = "done"),
            ),
            snapshotStatus = ThreadStatus(type = ThreadStatusType.Idle),
            activeTurnId = null,
        )

        assertTrue(shouldUseSnapshot)
    }

    @Test
    fun activeThreadSnapshotIsNotAuthoritative() {
        val shouldUseSnapshot = shouldUseAuthoritativeThreadSnapshot(
            snapshotItems = listOf(
                ThreadItem.AgentMessage(id = "item-1", text = "still running"),
            ),
            snapshotStatus = ThreadStatus(type = ThreadStatusType.Active),
            activeTurnId = "turn-1",
        )

        assertFalse(shouldUseSnapshot)
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

    @Test
    fun reconnectDelayStartsAtOneSecondAndBacksOffExponentially() {
        assertEquals(1_000L, reconnectDelayMillis(attempt = 1))
        assertEquals(2_000L, reconnectDelayMillis(attempt = 2))
        assertEquals(4_000L, reconnectDelayMillis(attempt = 3))
        assertEquals(8_000L, reconnectDelayMillis(attempt = 4))
    }

    @Test
    fun reconnectDelayCapsAtThirtySeconds() {
        assertEquals(30_000L, reconnectDelayMillis(attempt = 6))
        assertEquals(30_000L, reconnectDelayMillis(attempt = 9))
    }
}
