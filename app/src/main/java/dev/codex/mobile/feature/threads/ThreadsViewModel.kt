package dev.codex.mobile.feature.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isConnected
import dev.codex.mobile.core.model.isWaitingOnApproval
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ThreadFilter {
    All,
    Active,
    WaitingOnApproval,
    SystemError,
}

data class ThreadsUiState(
    val query: String = "",
    val selectedFilter: ThreadFilter = ThreadFilter.All,
    val canCreateThread: Boolean = false,
    val threads: List<ThreadSummary> = emptyList(),
)

class ThreadsViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(ThreadFilter.All)

    val uiState: StateFlow<ThreadsUiState> = combine(
        repository.observeThreads(),
        repository.observeConnection(),
        query,
        selectedFilter,
    ) { threads, connection, searchQuery, filter ->
        ThreadsUiState(
            query = searchQuery,
            selectedFilter = filter,
            canCreateThread = connection.isConnected,
            threads = threads.filter { thread ->
                val matchesQuery = searchQuery.isBlank() ||
                    thread.name.orEmpty().contains(searchQuery, ignoreCase = true) ||
                    thread.preview.contains(searchQuery, ignoreCase = true)
                val matchesFilter = when (filter) {
                    ThreadFilter.All -> true
                    ThreadFilter.Active -> thread.status.isActive
                    ThreadFilter.WaitingOnApproval -> thread.status.isWaitingOnApproval
                    ThreadFilter.SystemError -> thread.status.type == ThreadStatusType.SystemError
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

    fun createThread(onThreadCreated: (String) -> Unit) {
        viewModelScope.launch {
            repository.createThread()?.let(onThreadCreated)
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ThreadsViewModel(repository) }
        }
    }
}
