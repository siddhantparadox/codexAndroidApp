package dev.codex.mobile.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val activeHost: HostProfile? = null,
    val runningThread: ThreadSummary? = null,
    val recentThreads: List<ThreadSummary> = emptyList(),
)

class DashboardViewModel(
    repository: CodexRepository,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeHosts(),
        repository.observeThreads(),
    ) { hosts, threads ->
        DashboardUiState(
            activeHost = hosts.firstOrNull { it.isActive } ?: hosts.firstOrNull(),
            runningThread = threads.firstOrNull { it.status == ThreadStatus.Running },
            recentThreads = threads.take(3),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(repository) }
        }
    }
}
