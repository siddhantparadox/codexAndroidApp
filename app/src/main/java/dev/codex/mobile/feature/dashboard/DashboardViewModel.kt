package dev.codex.mobile.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AccountRateLimit
import dev.codex.mobile.core.model.AccountRateLimits
import dev.codex.mobile.core.model.AccountRateLimitWindow
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.isWaitingOnUserInput
import dev.codex.mobile.core.model.preferredBucket
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private const val FiveHourWindowDurationMins: Int = 300
private const val WeeklyWindowDurationMins: Int = 10_080

data class DashboardUsageWindowUiModel(
    val usedPercent: Int? = null,
    val resetsAtEpochSeconds: Long? = null,
    val windowDurationMins: Int? = null,
)

data class DashboardUsageSheetUiModel(
    val fiveHourWindow: DashboardUsageWindowUiModel = DashboardUsageWindowUiModel(),
    val weeklyWindow: DashboardUsageWindowUiModel = DashboardUsageWindowUiModel(),
)

data class DashboardUiState(
    val activeHost: HostProfile? = null,
    val connection: ConnectionState = ConnectionState(),
    val account: AccountState = AccountState(),
    val activeThread: ThreadSummary? = null,
    val usageSheet: DashboardUsageSheetUiModel = DashboardUsageSheetUiModel(),
    val syncedThreadCount: Int = 0,
    val attentionCount: Int = 0,
)

class DashboardViewModel(
    repository: CodexRepository,
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState> = combine(
        repository.observeHosts(),
        repository.observeConnection(),
        repository.observeAccount(),
        repository.observeRateLimits(),
        repository.observeThreads(),
    ) { hosts, connection, account, rateLimits, threads ->
        val activeThread: ThreadSummary? = threads.firstOrNull { thread ->
            thread.status.isWaitingOnApproval || thread.status.isWaitingOnUserInput
        } ?: threads.firstOrNull { thread ->
            thread.status.isActive
        }
        DashboardUiState(
            activeHost = hosts.firstOrNull { it.isActive } ?: hosts.firstOrNull(),
            connection = connection,
            account = account,
            activeThread = activeThread,
            usageSheet = rateLimits.toDashboardUsageSheet(),
            syncedThreadCount = threads.size,
            attentionCount = threads.count { thread ->
                thread.status.isWaitingOnApproval ||
                    thread.status.isWaitingOnUserInput ||
                    thread.status.type == ThreadStatusType.SystemError
            },
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

private fun AccountRateLimits?.toDashboardUsageSheet(): DashboardUsageSheetUiModel {
    val bucket: AccountRateLimit? = this?.preferredBucket()
    return DashboardUsageSheetUiModel(
        fiveHourWindow = bucket.findWindow(windowDurationMins = FiveHourWindowDurationMins)
            .toDashboardUsageWindow(),
        weeklyWindow = bucket.findWindow(windowDurationMins = WeeklyWindowDurationMins)
            .toDashboardUsageWindow(),
    )
}

private fun AccountRateLimit?.findWindow(windowDurationMins: Int): AccountRateLimitWindow? = listOfNotNull(
    this?.primary,
    this?.secondary,
).firstOrNull { window ->
    window.windowDurationMins == windowDurationMins
}

private fun AccountRateLimitWindow?.toDashboardUsageWindow(): DashboardUsageWindowUiModel =
    DashboardUsageWindowUiModel(
        usedPercent = this?.usedPercent,
        resetsAtEpochSeconds = this?.resetsAtEpochSeconds,
        windowDurationMins = this?.windowDurationMins,
    )
