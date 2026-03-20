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
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.UsageWrappedActivityDay
import dev.codex.mobile.core.model.UsageWrappedSummary
import dev.codex.mobile.core.model.isActive
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.isWaitingOnUserInput
import dev.codex.mobile.core.model.preferredBucket
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val FiveHourWindowDurationMins: Int = 300
private const val WeeklyWindowDurationMins: Int = 10_080

data class DashboardUsageWindowUiModel(
    val usedPercent: Int? = null,
    val resetsAtEpochSeconds: Long? = null,
    val windowDurationMins: Int? = null,
)

data class DashboardWrappedPreviewUiModel(
    val approximateUsd: Double? = null,
    val sessionCount: Int? = null,
    val totalTokens: Long? = null,
)

data class DashboardUsageSheetUiModel(
    val fiveHourWindow: DashboardUsageWindowUiModel = DashboardUsageWindowUiModel(),
    val weeklyWindow: DashboardUsageWindowUiModel = DashboardUsageWindowUiModel(),
    val wrapped: DashboardWrappedPreviewUiModel = DashboardWrappedPreviewUiModel(),
)

data class DashboardTodayUiModel(
    val dateLabel: String = LocalDate.now().format(DashboardTodayDateFormatter),
    val sessionCount: Int = 0,
    val totalTokens: Long = 0L,
    val lastActiveAtEpochSeconds: Long? = null,
    val currentStreakDays: Int = 0,
    val activity: List<DashboardTodayActivityPoint> = emptyList(),
    val hasUsageHistory: Boolean = false,
    val canSync: Boolean = false,
    val isSyncing: Boolean = false,
)

data class DashboardTodayActivityPoint(
    val sessionCount: Int = 0,
    val isToday: Boolean = false,
)

data class DashboardUiState(
    val activeHost: HostProfile? = null,
    val connection: ConnectionState = ConnectionState(),
    val account: AccountState = AccountState(),
    val activeThread: ThreadSummary? = null,
    val recentThreads: List<ThreadSummary> = emptyList(),
    val today: DashboardTodayUiModel = DashboardTodayUiModel(),
    val usageSheet: DashboardUsageSheetUiModel = DashboardUsageSheetUiModel(),
    val syncedThreadCount: Int = 0,
    val attentionCount: Int = 0,
)

private data class DashboardContext(
    val hosts: List<HostProfile> = emptyList(),
    val connection: ConnectionState = ConnectionState(),
    val account: AccountState = AccountState(),
    val rateLimits: AccountRateLimits? = null,
    val wrappedSummary: UsageWrappedSummary? = null,
)

class DashboardViewModel(
    private val repository: CodexRepository,
) : ViewModel() {
    private val isSyncing: MutableStateFlow<Boolean> = MutableStateFlow(false)

    private val contextFlow = combine(
        repository.observeHosts(),
        repository.observeConnection(),
        repository.observeAccount(),
        repository.observeRateLimits(),
        repository.observeUsageWrapped(),
    ) { hosts, connection, account, rateLimits, wrapped ->
        DashboardContext(
            hosts = hosts,
            connection = connection,
            account = account,
            rateLimits = rateLimits,
            wrappedSummary = wrapped.summary,
        )
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        contextFlow,
        repository.observeThreads(),
        isSyncing,
    ) { context, threads, syncing ->
        val activeThread: ThreadSummary? = threads.firstOrNull { thread ->
            thread.status.isWaitingOnApproval || thread.status.isWaitingOnUserInput
        } ?: threads.firstOrNull { thread ->
            thread.status.isActive
        }
        val lastActiveAtEpochSeconds: Long? = threads.maxOfOrNull(ThreadSummary::updatedAtEpochSeconds)
        val activeHost: HostProfile? = context.hosts.firstOrNull { it.isActive } ?: context.hosts.firstOrNull()
        DashboardUiState(
            activeHost = activeHost,
            connection = context.connection,
            account = context.account,
            activeThread = activeThread,
            recentThreads = threads.recentThreadsForHome(activeThreadId = activeThread?.id),
            today = context.wrappedSummary.toDashboardToday(
                lastActiveAtEpochSeconds = lastActiveAtEpochSeconds,
                canSync = activeHost != null && context.connection.phase == ConnectionPhase.Connected,
                isSyncing = syncing,
            ),
            usageSheet = context.rateLimits.toDashboardUsageSheet(wrapped = context.wrappedSummary),
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

    fun refreshUsageWrapped() {
        viewModelScope.launch {
            repository.refreshUsageWrapped()
        }
    }

    fun syncNow() {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            try {
                runCatching { repository.refreshThreads() }
                runCatching { repository.refreshUsageWrapped() }
            } finally {
                isSyncing.value = false
            }
        }
    }

    companion object {
        fun factory(repository: CodexRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(repository) }
        }
    }
}

private fun AccountRateLimits?.toDashboardUsageSheet(
    wrapped: UsageWrappedSummary?,
): DashboardUsageSheetUiModel {
    val bucket: AccountRateLimit? = this?.preferredBucket()
    return DashboardUsageSheetUiModel(
        fiveHourWindow = bucket.findWindow(windowDurationMins = FiveHourWindowDurationMins)
            .toDashboardUsageWindow(),
        weeklyWindow = bucket.findWindow(windowDurationMins = WeeklyWindowDurationMins)
            .toDashboardUsageWindow(),
        wrapped = wrapped.toDashboardWrappedPreview(),
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

private fun UsageWrappedSummary?.toDashboardWrappedPreview(): DashboardWrappedPreviewUiModel =
    DashboardWrappedPreviewUiModel(
        approximateUsd = this?.costEstimate?.approximateUsd,
        sessionCount = this?.overview?.sessionCount,
        totalTokens = this?.tokenTotals?.total,
    )

private fun UsageWrappedSummary?.toDashboardToday(
    lastActiveAtEpochSeconds: Long?,
    canSync: Boolean,
    isSyncing: Boolean,
): DashboardTodayUiModel {
    val todayDate: LocalDate = LocalDate.now()
    val todayIsoDate: String = todayDate.toString()
    val todayActivity: UsageWrappedActivityDay? = this?.activity?.firstOrNull { day ->
        day.date == todayIsoDate
    }
    val activityByDate: Map<String, UsageWrappedActivityDay> = this?.activity
        ?.associateBy(UsageWrappedActivityDay::date)
        .orEmpty()
    val sparklineActivity: List<DashboardTodayActivityPoint> = (6 downTo 0).map { daysAgo ->
        val date: LocalDate = todayDate.minusDays(daysAgo.toLong())
        val activityDay: UsageWrappedActivityDay? = activityByDate[date.toString()]
        DashboardTodayActivityPoint(
            sessionCount = activityDay?.sessionCount ?: 0,
            isToday = date == todayDate,
        )
    }
    return DashboardTodayUiModel(
        sessionCount = todayActivity?.sessionCount ?: 0,
        totalTokens = todayActivity?.totalTokens ?: 0L,
        lastActiveAtEpochSeconds = lastActiveAtEpochSeconds,
        currentStreakDays = this?.overview?.currentStreakDays ?: 0,
        activity = sparklineActivity,
        hasUsageHistory = this != null,
        canSync = canSync,
        isSyncing = isSyncing,
    )
}

private fun List<ThreadSummary>.recentThreadsForHome(
    activeThreadId: String?,
): List<ThreadSummary> = sortedByDescending(ThreadSummary::updatedAtEpochSeconds)
    .filterNot { thread -> thread.id == activeThreadId }
    .take(3)

private val DashboardTodayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)
