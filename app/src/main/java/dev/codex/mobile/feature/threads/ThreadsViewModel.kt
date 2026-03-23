package dev.codex.mobile.feature.threads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isConnected
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.workspaceFolderName
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
    val connectionPhase: ConnectionPhase = ConnectionPhase.Idle,
    val createThreadUnavailableMessage: String = threadCreationUnavailableMessage(ConnectionPhase.Idle),
    val isRefreshing: Boolean = false,
    val lastRefreshAtEpochSeconds: Long? = null,
    val refreshErrorMessage: String? = null,
    val unreadResultDigests: Map<String, ThreadResultDigest> = emptyMap(),
    val existingCwdOptions: List<ThreadCwdOption> = emptyList(),
    val folderSections: List<ThreadFolderSection> = emptyList(),
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
    val connectionPhase: ConnectionPhase = ConnectionPhase.Idle,
    val createThreadUnavailableMessage: String = threadCreationUnavailableMessage(ConnectionPhase.Idle),
    val unreadResultDigests: Map<String, ThreadResultDigest> = emptyMap(),
    val existingCwdOptions: List<ThreadCwdOption> = emptyList(),
    val folderSections: List<ThreadFolderSection> = emptyList(),
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
        val filteredThreads: List<ThreadSummary> = threads.filter { thread ->
            val matchesQuery = threadMatchesSearchQuery(
                thread = thread,
                searchQuery = searchQuery,
            )
            val matchesFilter = when (filter) {
                ThreadFilter.All -> true
                ThreadFilter.Active -> thread.status.isActive
                ThreadFilter.WaitingOnApproval -> thread.status.isWaitingOnApproval
                ThreadFilter.SystemError -> thread.status.type == ThreadStatusType.SystemError
            }
            matchesQuery && matchesFilter
        }
        ThreadListingState(
            canCreateThread = connection.isConnected,
            canRefresh = connection.isConnected,
            connectionPhase = connection.phase,
            createThreadUnavailableMessage = threadCreationUnavailableMessage(connection.phase),
            unreadResultDigests = unreadResultDigests,
            existingCwdOptions = buildThreadCwdOptions(threads),
            folderSections = buildThreadFolderSections(filteredThreads),
            threads = filteredThreads,
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
            connectionPhase = listing.connectionPhase,
            createThreadUnavailableMessage = listing.createThreadUnavailableMessage,
            isRefreshing = refresh.isRefreshing,
            lastRefreshAtEpochSeconds = refresh.lastRefreshAtEpochSeconds,
            refreshErrorMessage = refresh.refreshErrorMessage,
            unreadResultDigests = listing.unreadResultDigests,
            existingCwdOptions = listing.existingCwdOptions,
            folderSections = listing.folderSections,
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

    fun createThread(
        cwd: String? = null,
        onThreadCreated: (String) -> Unit,
        onThreadCreationFailed: (String) -> Unit = {},
    ) {
        if (!uiState.value.canCreateThread) {
            onThreadCreationFailed(uiState.value.createThreadUnavailableMessage)
            return
        }
        viewModelScope.launch {
            val normalizedCwd = cwd?.trim()?.takeIf(String::isNotEmpty)
            val createdThreadId = runCatching {
                repository.createThread(cwd = normalizedCwd)
            }.getOrNull()

            if (createdThreadId != null) {
                onThreadCreated(createdThreadId)
            } else {
                onThreadCreationFailed(
                    if (normalizedCwd == null) {
                        "Unable to start a new thread."
                    } else {
                        "Unable to start a thread in that folder."
                    },
                )
            }
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
                        refreshErrorMessage = threadRefreshUnavailableMessage(uiState.value.connectionPhase),
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

internal fun threadCreationUnavailableMessage(phase: ConnectionPhase): String = when (phase) {
    ConnectionPhase.Reconnecting -> "Reconnecting to your desktop. Existing threads stay available, but new threads are disabled until the connection resumes."
    else -> "Connect to your desktop before creating a new thread."
}

internal fun threadRefreshUnavailableMessage(phase: ConnectionPhase): String = when (phase) {
    ConnectionPhase.Reconnecting -> "Reconnecting to your desktop. Thread updates will resume automatically."
    else -> "Connect to refresh threads."
}

internal fun threadMatchesSearchQuery(
    thread: ThreadSummary,
    searchQuery: String,
): Boolean {
    if (searchQuery.isBlank()) return true

    return thread.name.orEmpty().contains(searchQuery, ignoreCase = true) ||
        thread.preview.contains(searchQuery, ignoreCase = true) ||
        thread.workspaceFolderName().orEmpty().contains(searchQuery, ignoreCase = true)
}

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000

