package dev.codex.mobile.feature.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AccountRateLimit
import dev.codex.mobile.core.model.AccountRateLimits
import dev.codex.mobile.core.model.AccountRateLimitWindow
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.UsageWrappedState
import dev.codex.mobile.core.model.preferredBucket
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FiveHourWindowDurationMins: Int = 300
private const val WeeklyWindowDurationMins: Int = 10_080

internal data class UsageWrappedQuotaWindowUiModel(
    val usedPercent: Int? = null,
    val resetsAtEpochSeconds: Long? = null,
    val windowDurationMins: Int? = null,
)

internal data class UsageWrappedQuotaUiModel(
    val fiveHourWindow: UsageWrappedQuotaWindowUiModel = UsageWrappedQuotaWindowUiModel(),
    val weeklyWindow: UsageWrappedQuotaWindowUiModel = UsageWrappedQuotaWindowUiModel(),
)

internal data class UsageWrappedUiState(
    val activeHost: HostProfile? = null,
    val quota: UsageWrappedQuotaUiModel = UsageWrappedQuotaUiModel(),
    val wrapped: UsageWrappedState = UsageWrappedState(),
)

internal class UsageWrappedViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    val uiState: StateFlow<UsageWrappedUiState> = combine(
        repository.observeHosts(),
        repository.observeRateLimits(),
        repository.observeUsageWrapped(),
    ) { hosts, rateLimits, wrapped ->
        UsageWrappedUiState(
            activeHost = hosts.firstOrNull { it.isActive } ?: hosts.firstOrNull(),
            quota = rateLimits.toQuotaUiModel(),
            wrapped = wrapped,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UsageWrappedUiState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.refreshUsageWrapped()
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { UsageWrappedViewModel(repository) }
        }
    }
}

private fun AccountRateLimits?.toQuotaUiModel(): UsageWrappedQuotaUiModel {
    val bucket: AccountRateLimit? = this?.preferredBucket()
    return UsageWrappedQuotaUiModel(
        fiveHourWindow = bucket.findWindow(windowDurationMins = FiveHourWindowDurationMins)
            .toQuotaWindowUiModel(),
        weeklyWindow = bucket.findWindow(windowDurationMins = WeeklyWindowDurationMins)
            .toQuotaWindowUiModel(),
    )
}

private fun AccountRateLimit?.findWindow(windowDurationMins: Int): AccountRateLimitWindow? = listOfNotNull(
    this?.primary,
    this?.secondary,
).firstOrNull { window ->
    window.windowDurationMins == windowDurationMins
}

private fun AccountRateLimitWindow?.toQuotaWindowUiModel(): UsageWrappedQuotaWindowUiModel =
    UsageWrappedQuotaWindowUiModel(
        usedPercent = this?.usedPercent,
        resetsAtEpochSeconds = this?.resetsAtEpochSeconds,
        windowDurationMins = this?.windowDurationMins,
    )
