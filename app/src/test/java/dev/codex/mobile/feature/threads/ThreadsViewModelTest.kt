package dev.codex.mobile.feature.threads

import dev.codex.mobile.core.data.demo.DemoCodexRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadsViewModelTest {
    @Test
    fun filteringKeepsOnlyRunningThreads() = runTest {
        val viewModel = ThreadsViewModel(repository = DemoCodexRepository())

        viewModel.onFilterSelected(ThreadFilter.Running)

        val state = viewModel.uiState.first { it.selectedFilter == ThreadFilter.Running && it.threads.isNotEmpty() }

        assertTrue(state.threads.isNotEmpty())
        assertTrue(state.threads.all { it.status.name == "Running" })
    }
}
