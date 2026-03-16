package dev.codex.mobile.app

import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AccountRateLimits
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ComposerCatalog
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadDynamicToolRequest
import dev.codex.mobile.core.model.ThreadDynamicToolResponse
import dev.codex.mobile.core.model.ThreadReplyRequest
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadSourceKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThemePreference
import dev.codex.mobile.core.model.UsageWrappedState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CodexRootViewModelTest {
    @Test
    fun connectionAlertsToggleHidesApprovalAndDisconnectAlertsButKeepsThreadNotifications() = runTest {
        val approval = sampleApproval()
        val notification = sampleThreadNotification()
        val repository = FakeCodexRepository(
            preferences = AppPreferences(
                themePreference = ThemePreference.System,
                connectionAlerts = true,
            ),
            hosts = listOf(sampleHost()),
            connection = ConnectionState(
                activeHostId = "desktop-1",
                phase = ConnectionPhase.Disconnected,
                message = "Connection lost.",
            ),
            approvals = listOf(approval),
            threads = listOf(sampleThread()),
            notifications = listOf(notification),
        )
        val viewModel = CodexRootViewModel(repository = repository)

        val initialState = viewModel.uiState.first { state -> state.alerts.size == 3 }

        assertTrue(initialState.alerts.any { alert -> alert.id == notification.id })
        assertTrue(initialState.alerts.any { alert -> alert.id == approvalAlertId(approval.id) })
        assertTrue(initialState.alerts.any { alert -> alert.id.startsWith(CONNECTION_ALERT_PREFIX) })

        repository.setPreferences(
            AppPreferences(
                themePreference = ThemePreference.System,
                connectionAlerts = false,
            ),
        )

        val updatedState = viewModel.uiState.first { state -> state.alerts.size == 1 }

        assertEquals(listOf(notification.id), updatedState.alerts.map(InAppAlert::id))
    }

    @Test
    fun reconnectingStateDoesNotShowConnectionAlert() = runTest {
        val approval = sampleApproval()
        val notification = sampleThreadNotification()
        val repository = FakeCodexRepository(
            preferences = AppPreferences(
                themePreference = ThemePreference.System,
                connectionAlerts = true,
            ),
            hosts = listOf(sampleHost()),
            connection = ConnectionState(
                activeHostId = "desktop-1",
                phase = ConnectionPhase.Reconnecting,
                message = "Reconnecting to Work Desktop in 4s",
            ),
            approvals = listOf(approval),
            threads = listOf(sampleThread()),
            notifications = listOf(notification),
        )
        val viewModel = CodexRootViewModel(repository = repository)

        val state = viewModel.uiState.first { current -> current.alerts.size == 2 }

        assertTrue(state.alerts.none { alert -> alert.id.startsWith(CONNECTION_ALERT_PREFIX) })
        assertTrue(state.alerts.any { alert -> alert.id == approvalAlertId(approval.id) })
        assertTrue(state.alerts.any { alert -> alert.id == notification.id })
    }

    @Test
    fun dismissAlertRemovesApprovalBanner() = runTest {
        val approval = sampleApproval()
        val repository = FakeCodexRepository(
            approvals = listOf(approval),
            threads = listOf(sampleThread()),
        )
        val viewModel = CodexRootViewModel(repository = repository)
        val alertId = approvalAlertId(approval.id)

        viewModel.uiState.first { state -> state.alerts.any { alert -> alert.id == alertId } }
        viewModel.dismissAlert(alertId)

        val updatedState = viewModel.uiState.first { state -> state.alerts.none { alert -> alert.id == alertId } }

        assertTrue(updatedState.alerts.none { alert -> alert.id == alertId })
    }


    @Test
    fun dismissAlertDelegatesThreadNotificationsToRepository() = runTest {
        val notification = sampleThreadNotification()
        val repository = FakeCodexRepository(
            notifications = listOf(notification),
        )
        val viewModel = CodexRootViewModel(repository = repository)

        viewModel.uiState.first { state -> state.alerts.any { alert -> alert.id == notification.id } }
        viewModel.dismissAlert(notification.id)
        val updatedState = viewModel.uiState.first { state -> state.alerts.none { alert -> alert.id == notification.id } }

        assertTrue(updatedState.alerts.none { alert -> alert.id == notification.id })
        assertEquals(listOf(notification.id), repository.dismissedNotificationIds)
    }
}

private class FakeCodexRepository(
    preferences: AppPreferences = AppPreferences(),
    hosts: List<HostProfile> = emptyList(),
    connection: ConnectionState = ConnectionState(),
    approvals: List<ApprovalItem> = emptyList(),
    threads: List<ThreadSummary> = emptyList(),
    notifications: List<InAppThreadNotification> = emptyList(),
) : CodexRepository {
    private val preferencesFlow = MutableStateFlow(preferences)
    private val hostsFlow = MutableStateFlow(hosts)
    private val connectionFlow = MutableStateFlow(connection)
    private val approvalsFlow = MutableStateFlow(approvals)
    private val threadsFlow = MutableStateFlow(threads)
    private val notificationsFlow = MutableStateFlow(notifications)

    val dismissedNotificationIds: MutableList<String> = mutableListOf()

    fun setPreferences(preferences: AppPreferences) {
        preferencesFlow.value = preferences
    }

    fun setConnection(connection: ConnectionState) {
        connectionFlow.value = connection
    }

    override fun observePreferences(): Flow<AppPreferences> = preferencesFlow

    override fun observeHosts(): Flow<List<HostProfile>> = hostsFlow

    override fun observeConnection(): Flow<ConnectionState> = connectionFlow

    override fun observeAccount(): Flow<AccountState> = flowOf(AccountState())

    override fun observeRateLimits(): Flow<AccountRateLimits?> = flowOf(null)

    override fun observeUsageWrapped(): Flow<UsageWrappedState> = flowOf(UsageWrappedState())

    override fun observeThreads(): Flow<List<ThreadSummary>> = threadsFlow

    override fun observeThreadDetail(threadId: String): Flow<ThreadDetail?> = flowOf(null)

    override fun observeActiveItemIds(threadId: String): Flow<Set<String>> = flowOf(emptySet())

    override fun observeApprovals(): Flow<List<ApprovalItem>> = approvalsFlow

    override fun observeUserInputRequests(): Flow<List<ThreadUserInputRequest>> = flowOf(emptyList())

    override fun observeDynamicToolRequests(): Flow<List<ThreadDynamicToolRequest>> = flowOf(emptyList())

    override fun observeComposerCatalog(): Flow<ComposerCatalog> = flowOf(ComposerCatalog())

    override fun observeUnreadThreadResultDigests(): Flow<Map<String, ThreadResultDigest>> = flowOf(emptyMap())

    override fun observeInAppThreadNotifications(): Flow<List<InAppThreadNotification>> = notificationsFlow

    override suspend fun saveHost(
        name: String,
        address: String,
        port: Int,
        desktopId: String?,
        activate: Boolean,
    ): String? = null

    override suspend fun setActiveHost(hostId: String) = Unit

    override suspend fun renameHost(hostId: String, name: String): Boolean = false

    override suspend fun removeHost(hostId: String): Boolean = false

    override suspend fun setThemePreference(preference: ThemePreference) = Unit

    override suspend fun setConnectionAlerts(enabled: Boolean) {
        preferencesFlow.update { current -> current.copy(connectionAlerts = enabled) }
    }

    override suspend fun resolveApproval(
        approvalId: String,
        decision: ApprovalDecision,
    ) = Unit

    override suspend fun createThread(cwd: String?): String? = null

    override suspend fun openThread(threadId: String) = Unit

    override fun setVisibleThread(threadId: String?) = Unit

    override suspend fun refreshThreads() = Unit

    override suspend fun refreshUsageWrapped() = Unit

    override suspend fun dismissInAppThreadNotification(notificationId: String) {
        dismissedNotificationIds += notificationId
        notificationsFlow.update { currentNotifications ->
            currentNotifications.filterNot { notification -> notification.id == notificationId }
        }
    }

    override suspend fun respondToUserInput(
        requestId: String,
        response: dev.codex.mobile.core.model.ThreadUserInputResponse,
    ) = Unit

    override suspend fun respondToDynamicTool(
        requestId: String,
        response: ThreadDynamicToolResponse,
    ) = Unit

    override suspend fun refreshComposerCatalog() = Unit

    override suspend fun sendReply(
        threadId: String,
        request: ThreadReplyRequest,
    ) = Unit

    override suspend fun interruptThread(threadId: String) = Unit
}

private fun sampleHost(): HostProfile = HostProfile(
    id = "desktop-1",
    desktopId = "desktop-1",
    name = "Work Desktop",
    address = "10.0.0.5",
    port = 4500,
    kind = HostKind.Desktop,
    isActive = true,
)

private fun sampleThread(): ThreadSummary = ThreadSummary(
    id = "thread-1",
    name = "Auth refactor",
    preview = "Waiting on approval.",
    createdAtEpochSeconds = 1L,
    updatedAtEpochSeconds = 2L,
    modelProvider = "openai",
    ephemeral = false,
    status = ThreadStatus(
        type = ThreadStatusType.Active,
        activeFlags = setOf("waitingOnApproval"),
    ),
    source = ThreadSourceKind.Cli,
)

private fun sampleApproval(): ApprovalItem = ApprovalItem(
    id = "approval-1",
    threadId = "thread-1",
    turnId = "turn-1",
    itemId = "item-1",
    kind = ApprovalKind.CommandExecution,
    command = "npm test",
    availableDecisions = listOf(
        ApprovalDecision.Accept,
        ApprovalDecision.Decline,
    ),
)

private fun sampleThreadNotification(): InAppThreadNotification = InAppThreadNotification(
    id = "thread-alert-1",
    threadId = "thread-1",
    title = "Auth refactor complete",
    message = "Patch ready · 1 file",
    createdAtEpochSeconds = 10L,
)

