package dev.codex.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CodexRootUiState(
    val themePreference: ThemePreference = ThemePreference.System,
    val pendingApprovals: Int = 0,
)

class CodexRootViewModel(
    repository: CodexRepository,
) : ViewModel() {
    val uiState: StateFlow<CodexRootUiState> = combine(
        repository.observePreferences(),
        repository.observeApprovals(),
    ) { preferences, approvals ->
        CodexRootUiState(
            themePreference = preferences.themePreference,
            pendingApprovals = approvals.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CodexRootUiState(),
    )

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CodexRootViewModel(repository) }
        }
    }
}
