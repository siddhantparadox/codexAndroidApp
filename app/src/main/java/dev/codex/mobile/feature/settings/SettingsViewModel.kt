package dev.codex.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ThemePreference
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val hosts: List<HostProfile> = emptyList(),
)

class SettingsViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.observePreferences(),
        repository.observeHosts(),
    ) { preferences, hosts ->
        SettingsUiState(
            preferences = preferences,
            hosts = hosts,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setThemePreference(preference: ThemePreference) {
        viewModelScope.launch {
            repository.setThemePreference(preference)
        }
    }

    fun setConnectionAlerts(enabled: Boolean) {
        viewModelScope.launch {
            repository.setConnectionAlerts(enabled)
        }
    }

    fun setSecureShell(enabled: Boolean) {
        viewModelScope.launch {
            repository.setSecureShell(enabled)
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
