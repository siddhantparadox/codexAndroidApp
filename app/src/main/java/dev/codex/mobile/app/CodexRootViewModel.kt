package dev.codex.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CodexRootUiState(
    val themePreference: ThemePreference = ThemePreference.System,
    val pendingApprovals: Int = 0,
    val notifications: List<InAppThreadNotification> = emptyList(),
)

class CodexRootViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    val uiState: StateFlow<CodexRootUiState> = combine(
        repository.observePreferences(),
        repository.observeApprovals(),
        repository.observeInAppThreadNotifications(),
    ) { preferences, approvals, notifications ->
        CodexRootUiState(
            themePreference = preferences.themePreference,
            pendingApprovals = approvals.size,
            notifications = notifications,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CodexRootUiState(),
    )

    fun dismissThreadNotification(notificationId: String) {
        viewModelScope.launch {
            repository.dismissInAppThreadNotification(notificationId)
        }
    }

    fun setVisibleThread(threadId: String?) {
        repository.setVisibleThread(threadId)
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CodexRootViewModel(repository) }
        }
    }
}
