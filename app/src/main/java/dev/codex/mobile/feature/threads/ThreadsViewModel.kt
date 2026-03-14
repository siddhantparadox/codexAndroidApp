package dev.codex.mobile.feature.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isConnected
import dev.codex.mobile.core.model.isWaitingOnApproval
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    val canRefresh: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastRefreshAtEpochSeconds: Long? = null,
    val refreshErrorMessage: String? = null,
    val unreadResultDigests: Map<String, ThreadResultDigest> = emptyMap(),
    val threads: List<ThreadSummary> = emptyList(),
)

private data class ThreadsRefreshState(
    val isRefreshing: Boolean = false,
    val lastRefreshAtEpochSeconds: Long? = null,
    val refreshErrorMessage: String? = null,
)

private data class ThreadListingState(
    val canCreateThread: Boolean = false,
    val canRefresh: Boolean = false,
    val unreadResultDigests: Map<String, ThreadResultDigest> = emptyMap(),
    val threads: List<ThreadSummary> = emptyList(),
)

class ThreadsViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(ThreadFilter.All)
    private val refreshState = MutableStateFlow(ThreadsRefreshState())
    private var refreshJob: Job? = null

    private val threadListingState = combine(
        repository.observeThreads(),
        repository.observeConnection(),
        repository.observeUnreadThreadResultDigests(),
        query,
        selectedFilter,
    ) { threads, connection, unreadResultDigests, searchQuery, filter ->
        ThreadListingState(
            canCreateThread = connection.isConnected,
            canRefresh = connection.isConnected,
            unreadResultDigests = unreadResultDigests,
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
    }

    val uiState: StateFlow<ThreadsUiState> = combine(
        threadListingState,
        query,
        selectedFilter,
        refreshState,
    ) { listing, searchQuery, filter, refresh ->
        ThreadsUiState(
            query = searchQuery,
            selectedFilter = filter,
            canCreateThread = listing.canCreateThread,
            canRefresh = listing.canRefresh,
            isRefreshing = refresh.isRefreshing,
            lastRefreshAtEpochSeconds = refresh.lastRefreshAtEpochSeconds,
            refreshErrorMessage = refresh.refreshErrorMessage,
            unreadResultDigests = listing.unreadResultDigests,
            threads = listing.threads,
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

    fun refreshThreads() {
        requestThreadRefresh(
            showRefreshingIndicator = true,
            surfaceErrors = true,
        )
    }

    fun refreshThreadsInBackground() {
        requestThreadRefresh(
            showRefreshingIndicator = false,
            surfaceErrors = false,
        )
    }

    private fun requestThreadRefresh(
        showRefreshingIndicator: Boolean,
        surfaceErrors: Boolean,
    ) {
        if (refreshJob?.isActive == true) return
        if (!uiState.value.canRefresh) {
            if (surfaceErrors) {
                refreshState.update { current ->
                    current.copy(
                        isRefreshing = false,
                        refreshErrorMessage = "Connect to refresh threads.",
                    )
                }
            }
            return
        }

        refreshState.update { current ->
            current.copy(
                isRefreshing = showRefreshingIndicator,
                refreshErrorMessage = null,
            )
        }
        refreshJob = viewModelScope.launch {
            runCatching {
                repository.refreshThreads()
            }.onSuccess {
                refreshState.update { current ->
                    current.copy(
                        isRefreshing = false,
                        lastRefreshAtEpochSeconds = currentEpochSeconds(),
                        refreshErrorMessage = null,
                    )
                }
            }.onFailure {
                refreshState.update { current ->
                    current.copy(
                        isRefreshing = false,
                        refreshErrorMessage = if (surfaceErrors) {
                            "Refresh failed. Showing cached threads."
                        } else {
                            current.refreshErrorMessage
                        },
                    )
                }
            }
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { ThreadsViewModel(repository) }
        }
    }
}

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000
