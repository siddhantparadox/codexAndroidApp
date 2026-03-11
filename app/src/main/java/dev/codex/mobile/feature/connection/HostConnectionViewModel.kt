package dev.codex.mobile.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HostConnectionUiState(
    val hosts: List<HostProfile> = emptyList(),
    val connection: ConnectionState = ConnectionState(),
    val account: AccountState = AccountState(),
    val hostName: String = "",
    val address: String = "",
    val port: String = "4500",
)

class HostConnectionViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val formState = MutableStateFlow(HostConnectionUiState())

    val uiState: StateFlow<HostConnectionUiState> = combine(
        repository.observeHosts(),
        repository.observeConnection(),
        repository.observeAccount(),
        formState,
    ) { hosts, connection, account, form ->
        form.copy(
            hosts = hosts,
            connection = connection,
            account = account,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HostConnectionUiState(),
    )

    fun onHostNameChanged(value: String) {
        formState.update { it.copy(hostName = value) }
    }

    fun onAddressChanged(value: String) {
        formState.update { it.copy(address = value) }
    }

    fun onPortChanged(value: String) {
        formState.update { it.copy(port = value.filter(Char::isDigit).take(5)) }
    }

    fun saveConnection() {
        val snapshot = formState.value
        val port = snapshot.port.toIntOrNull() ?: 4500
        viewModelScope.launch {
            repository.saveHost(
                name = snapshot.hostName,
                address = snapshot.address,
                port = port,
            )
            formState.update {
                it.copy(
                    hostName = "",
                    address = "",
                    port = "4500",
                )
            }
        }
    }

    fun activateHost(hostId: String) {
        viewModelScope.launch {
            repository.setActiveHost(hostId)
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HostConnectionViewModel(repository) }
        }
    }
}
