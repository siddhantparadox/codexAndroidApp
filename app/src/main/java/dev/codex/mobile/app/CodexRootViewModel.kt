package dev.codex.mobile.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class CodexRootUiState(
    val themePreference: ThemePreference = ThemePreference.System,
    val pendingApprovals: Int = 0,
    val alerts: List<InAppAlert> = emptyList(),
    val activeHostId: String? = null,
    val activeHostName: String? = null,
)

private data class RootAlertPrimaryInputs(
    val preferences: AppPreferences,
    val hosts: List<HostProfile>,
    val connection: ConnectionState,
)

private data class RootAlertSecondaryInputs(
    val approvals: List<ApprovalItem>,
    val threads: List<ThreadSummary>,
    val notifications: List<InAppThreadNotification>,
)

class CodexRootViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val visibleThreadId: MutableStateFlow<String?> = MutableStateFlow(null)
    private val dismissedApprovalAlertIds: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())
    private val dismissedConnectionAlertId: MutableStateFlow<String?> = MutableStateFlow(null)

    private var lastConnectionAlertId: String? = null

    private val primaryInputs: Flow<RootAlertPrimaryInputs> = combine(
        repository.observePreferences(),
        repository.observeHosts(),
        repository.observeConnection(),
    ) { preferences, hosts, connection ->
        RootAlertPrimaryInputs(
            preferences = preferences,
            hosts = hosts,
            connection = connection,
        )
    }

    private val secondaryInputs: Flow<RootAlertSecondaryInputs> = combine(
        repository.observeApprovals(),
        repository.observeThreads(),
        repository.observeInAppThreadNotifications(),
    ) { approvals, threads, notifications ->
        RootAlertSecondaryInputs(
            approvals = approvals,
            threads = threads,
            notifications = notifications,
        )
    }

    internal val uiState: StateFlow<CodexRootUiState> = combine(
        primaryInputs,
        secondaryInputs,
        visibleThreadId,
        dismissedApprovalAlertIds,
        dismissedConnectionAlertId,
    ) { primary, secondary, currentVisibleThreadId, currentDismissedApprovalIds, currentDismissedConnectionId ->
        CodexRootUiState(
            themePreference = primary.preferences.themePreference,
            pendingApprovals = secondary.approvals.size,
            activeHostId = primary.hosts.firstOrNull { host -> host.isActive }?.id,
            activeHostName = primary.hosts.firstOrNull { host -> host.isActive }?.name,
            alerts = buildRootInAppAlerts(
                preferences = primary.preferences,
                hosts = primary.hosts,
                connection = primary.connection,
                approvals = secondary.approvals,
                threadNotifications = secondary.notifications,
                threads = secondary.threads,
                visibleThreadId = currentVisibleThreadId,
                dismissedApprovalAlertIds = currentDismissedApprovalIds,
                dismissedConnectionAlertId = currentDismissedConnectionId,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CodexRootUiState(),
    )

    init {
        viewModelScope.launch {
            repository.observeApprovals().collect { approvals ->
                val activeAlertIds: Set<String> = approvals
                    .mapTo(linkedSetOf()) { approval -> approvalAlertId(approval.id) }
                dismissedApprovalAlertIds.update { currentIds -> currentIds intersect activeAlertIds }
            }
        }
        viewModelScope.launch {
            repository.observeConnection().collect { connection ->
                val currentAlertId: String? = connectionAlertId(connection)
                if (currentAlertId != lastConnectionAlertId) {
                    lastConnectionAlertId = currentAlertId
                    dismissedConnectionAlertId.value = null
                }
            }
        }
    }

    fun dismissAlert(alertId: String) {
        when {
            alertId.startsWith(APPROVAL_ALERT_PREFIX) -> {
                dismissedApprovalAlertIds.update { currentIds -> currentIds + alertId }
            }

            alertId.startsWith(CONNECTION_ALERT_PREFIX) -> {
                dismissedConnectionAlertId.value = alertId
                lastConnectionAlertId = null
            }

            else -> {
                viewModelScope.launch {
                    repository.dismissInAppThreadNotification(alertId)
                }
            }
        }
    }

    fun setVisibleThread(threadId: String?) {
        visibleThreadId.value = threadId
        repository.setVisibleThread(threadId)
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { CodexRootViewModel(repository) }
        }
    }
}

