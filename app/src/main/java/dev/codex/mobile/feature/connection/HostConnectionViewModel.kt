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
    val connectionCode: String = "",
    val bootstrapError: String? = null,
    val pendingBootstrap: ConnectionBootstrap? = null,
    val showManualEntry: Boolean = false,
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
        formState.update { it.copy(hostName = value, bootstrapError = null) }
    }

    fun onAddressChanged(value: String) {
        formState.update { it.copy(address = value, bootstrapError = null) }
    }

    fun onPortChanged(value: String) {
        formState.update {
            it.copy(
                port = value.filter(Char::isDigit).take(5),
                bootstrapError = null,
            )
        }
    }

    fun onConnectionCodeChanged(value: String) {
        formState.update { it.copy(connectionCode = value, bootstrapError = null) }
    }

    fun submitConnectionCode() {
        val rawCode = formState.value.connectionCode
        resolveBootstrap(rawCode)
    }

    fun handleScannedBootstrap(rawValue: String) {
        resolveBootstrap(rawValue)
    }

    fun dismissPendingBootstrap() {
        formState.update { it.copy(pendingBootstrap = null) }
    }

    fun showBootstrapError(message: String) {
        formState.update { it.copy(bootstrapError = message, pendingBootstrap = null) }
    }

    fun confirmPendingBootstrap() {
        val bootstrap = formState.value.pendingBootstrap ?: return
        viewModelScope.launch {
            repository.saveHost(
                name = bootstrap.desktopName,
                address = bootstrap.host,
                port = bootstrap.port,
                desktopId = bootstrap.desktopId,
                activate = true,
            )
            formState.update {
                it.copy(
                    connectionCode = "",
                    bootstrapError = null,
                    pendingBootstrap = null,
                )
            }
        }
    }

    fun toggleManualEntry() {
        formState.update { current ->
            current.copy(showManualEntry = !current.showManualEntry, bootstrapError = null)
        }
    }

    fun saveConnection() {
        val snapshot = formState.value
        val resolvedName = snapshot.hostName.trim().ifEmpty {
            snapshot.address.trim().ifEmpty { "Desktop" }
        }
        val port = snapshot.port.toIntOrNull() ?: 4500
        viewModelScope.launch {
            repository.saveHost(
                name = resolvedName,
                address = snapshot.address,
                port = port,
                activate = true,
            )
            formState.update {
                it.copy(
                    hostName = "",
                    address = "",
                    port = "4500",
                    showManualEntry = false,
                    bootstrapError = null,
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

    private fun resolveBootstrap(rawValue: String) {
        val parsedBootstrap = parseConnectionBootstrap(rawValue)
        formState.update { current ->
            parsedBootstrap.fold(
                onSuccess = { bootstrap ->
                    current.copy(
                        pendingBootstrap = bootstrap,
                        bootstrapError = null,
                    )
                },
                onFailure = { error ->
                    current.copy(
                        pendingBootstrap = null,
                        bootstrapError = error.message ?: "Unable to read the desktop code.",
                    )
                },
            )
        }
    }
}
