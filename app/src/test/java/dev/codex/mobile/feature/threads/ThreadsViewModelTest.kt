package dev.codex.mobile.feature.threads

import dev.codex.mobile.core.data.demo.DemoCodexRepository
import dev.codex.mobile.core.model.isActive
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadsViewModelTest {
    @Test
    fun filteringKeepsOnlyActiveThreads() = runTest {
        val viewModel = ThreadsViewModel(repository = DemoCodexRepository())

        viewModel.onFilterSelected(ThreadFilter.Active)

        val state = viewModel.uiState.first { it.selectedFilter == ThreadFilter.Active && it.threads.isNotEmpty() }

        assertTrue(state.threads.isNotEmpty())
        assertTrue(state.threads.all { it.status.isActive })
    }
}
