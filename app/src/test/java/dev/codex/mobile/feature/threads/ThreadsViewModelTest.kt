package dev.codex.mobile.feature.threads

import dev.codex.mobile.core.data.demo.DemoCodexRepository
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.isActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadsViewModelTest {
    @Test
    fun reconnectingCreateThreadMessageExplainsRecoveryState() {
        assertEquals(
            "Reconnecting to your desktop. Existing threads stay available, but new threads are disabled until the connection resumes.",
            threadCreationUnavailableMessage(ConnectionPhase.Reconnecting),
        )
    }

    @Test
    fun reconnectingRefreshMessageExplainsRecoveryState() {
        assertEquals(
            "Reconnecting to your desktop. Thread updates will resume automatically.",
            threadRefreshUnavailableMessage(ConnectionPhase.Reconnecting),
        )
    }

    @Test
    fun filteringKeepsOnlyActiveThreads() = runTest {
        val viewModel = ThreadsViewModel(repository = DemoCodexRepository())

        viewModel.onFilterSelected(ThreadFilter.Active)

        val state = viewModel.uiState.first { it.selectedFilter == ThreadFilter.Active && it.threads.isNotEmpty() }

        assertTrue(state.threads.isNotEmpty())
        assertTrue(state.threads.all { it.status.isActive })
    }

    @Test
    fun exposesExistingCwdOptionsFromThreadHistory() = runTest {
        val viewModel = ThreadsViewModel(repository = DemoCodexRepository())

        val state = viewModel.uiState.first { it.existingCwdOptions.isNotEmpty() }

        assertFalse(state.existingCwdOptions.isEmpty())
        assertTrue(state.existingCwdOptions.all { option -> option.path.isNotBlank() })
    }

    @Test
    fun exposesFolderSectionsForFilteredThreads() = runTest {
        val viewModel = ThreadsViewModel(repository = DemoCodexRepository())

        val state = viewModel.uiState.first { it.folderSections.isNotEmpty() }

        assertFalse(state.folderSections.isEmpty())
        assertTrue(state.folderSections.all { section -> section.threads.isNotEmpty() })
    }

    @Test
    fun searchMatchesVisibleDirectoryName() {
        val thread = testThreadSummary(
            id = "directory-only",
            name = "Unrelated title",
            preview = "No matching preview text",
            cwd = "D:/projects/codexAndroidApp",
        )

        assertTrue(threadMatchesSearchQuery(thread = thread, searchQuery = "codexAndroidApp"))
        assertFalse(threadMatchesSearchQuery(thread = thread, searchQuery = "projects"))
    }
}

private fun testThreadSummary(
    id: String,
    name: String,
    preview: String,
    cwd: String,
): ThreadSummary = ThreadSummary(
    id = id,
    name = name,
    preview = preview,
    createdAtEpochSeconds = 0L,
    updatedAtEpochSeconds = 0L,
    modelProvider = "openai",
    ephemeral = false,
    status = ThreadStatus(type = ThreadStatusType.Idle),
    cwd = cwd,
)
