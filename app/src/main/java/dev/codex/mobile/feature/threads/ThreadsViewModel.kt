package dev.codex.mobile.feature.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ThreadFilter {
    All,
    Running,
    NeedsReview,
    Failed,
}

data class ThreadsUiState(
    val query: String = "",
    val selectedFilter: ThreadFilter = ThreadFilter.All,
    val threads: List<ThreadSummary> = emptyList(),
)

class ThreadsViewModel(
    repository: CodexRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(ThreadFilter.All)

    val uiState: StateFlow<ThreadsUiState> = combine(
        repository.observeThreads(),
        query,
        selectedFilter,
    ) { threads, searchQuery, filter ->
        ThreadsUiState(
            query = searchQuery,
            selectedFilter = filter,
            threads = threads.filter { thread ->
                val matchesQuery = searchQuery.isBlank() ||
                    thread.title.contains(searchQuery, ignoreCase = true) ||
                    thread.projectLabel.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (filter) {
                    ThreadFilter.All -> true
                    ThreadFilter.Running -> thread.status == ThreadStatus.Running
                    ThreadFilter.NeedsReview -> thread.status == ThreadStatus.NeedsReview
                    ThreadFilter.Failed -> thread.status == ThreadStatus.Failed
                }
                matchesQuery && matchesFilter
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ThreadsUiState(),
    )

    fun onQueryChanged(value: String) {
        query.update { value }
    }

    fun onFilterSelected(filter: ThreadFilter) {
        selectedFilter.update { filter }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ThreadsViewModel(repository) }
        }
    }
}
