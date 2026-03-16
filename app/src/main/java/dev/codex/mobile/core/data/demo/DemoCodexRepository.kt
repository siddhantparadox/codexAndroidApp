package dev.codex.mobile.core.data.demo

import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.data.removeHostProfile
import dev.codex.mobile.core.data.renameHostProfile
import dev.codex.mobile.core.data.upsertHostProfile
import dev.codex.mobile.core.model.AccountRateLimit
import dev.codex.mobile.core.model.AccountRateLimits
import dev.codex.mobile.core.model.AccountRateLimitWindow
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.AccountStatus
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ComposerCatalog
import dev.codex.mobile.core.model.ComposerModelOption
import dev.codex.mobile.core.model.ComposerReasoningEffort
import dev.codex.mobile.core.model.ComposerReasoningEffortOption
import dev.codex.mobile.core.model.ComposerSkillOption
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ThreadReplyRequest
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadDynamicToolRequest
import dev.codex.mobile.core.model.ThreadDynamicToolResponse
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadSourceKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThemePreference
import dev.codex.mobile.core.model.UsageWrappedActivityDay
import dev.codex.mobile.core.model.UsageWrappedCostEstimate
import dev.codex.mobile.core.model.UsageWrappedDaySummary
import dev.codex.mobile.core.model.UsageWrappedHighlights
import dev.codex.mobile.core.model.UsageWrappedOverview
import dev.codex.mobile.core.model.UsageWrappedProjectSummary
import dev.codex.mobile.core.model.UsageWrappedRange
import dev.codex.mobile.core.model.UsageWrappedSourceSummary
import dev.codex.mobile.core.model.UsageWrappedState
import dev.codex.mobile.core.model.UsageWrappedSummary
import dev.codex.mobile.core.model.UsageWrappedTokenTotals
import dev.codex.mobile.core.util.AppLog
import java.time.LocalDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private data class DemoStoreState(
    val preferences: AppPreferences = AppPreferences(),
    val hosts: List<HostProfile> = emptyList(),
    val connection: ConnectionState = ConnectionState(
        activeHostId = "work-laptop",
        phase = ConnectionPhase.Connected,
        message = "ws://192.168.1.15:4500",
    ),
    val account: AccountState = AccountState(
        status = AccountStatus.ChatGpt,
        email = "demo@example.com",
        planType = "pro",
        requiresOpenaiAuth = true,
    ),
    val rateLimits: AccountRateLimits = AccountRateLimits(),
    val usageWrapped: UsageWrappedState = UsageWrappedState(),
    val threads: List<ThreadSummary> = emptyList(),
    val threadDetails: Map<String, ThreadDetail> = emptyMap(),
    val activeItemIdsByThread: Map<String, Set<String>> = emptyMap(),
    val approvals: List<ApprovalItem> = emptyList(),
    val userInputRequests: List<ThreadUserInputRequest> = emptyList(),
    val composerCatalog: ComposerCatalog = demoComposerCatalog(),
    val unreadThreadResultDigests: Map<String, ThreadResultDigest> = emptyMap(),
    val inAppThreadNotifications: List<InAppThreadNotification> = emptyList(),
)

class DemoCodexRepository : CodexRepository {
    private val initialThreads: List<ThreadSummary> = demoThreads()

    private val store = MutableStateFlow(
        DemoStoreState(
            preferences = AppPreferences(
                themePreference = ThemePreference.Dark,
                connectionAlerts = true,
            ),
            hosts = listOf(
                HostProfile(
                    id = "work-laptop",
                    desktopId = "work-laptop",
                    name = "MacBook Pro 16\"",
                    address = "192.168.1.15",
                    port = 4500,
                    kind = HostKind.Laptop,
                    isActive = true,
                ),
                HostProfile(
                    id = "home-desktop",
                    desktopId = "home-desktop",
                    name = "Home Desktop",
                    address = "192.168.1.42",
                    port = 4500,
                    kind = HostKind.Desktop,
                ),
            ),
            rateLimits = demoRateLimits(),
            usageWrapped = demoUsageWrappedState(),
            threads = initialThreads,
            threadDetails = demoThreadDetails(initialThreads),
            activeItemIdsByThread = demoActiveItemIdsByThread(),
            approvals = demoApprovals(),
        ),
    )

    override fun observePreferences(): Flow<AppPreferences> = store.map { it.preferences }

    override fun observeHosts(): Flow<List<HostProfile>> = store.map { it.hosts }

    override fun observeConnection(): Flow<ConnectionState> = store.map { it.connection }

    override fun observeAccount(): Flow<AccountState> = store.map { it.account }

    override fun observeRateLimits(): Flow<AccountRateLimits?> = store.map { it.rateLimits }

    override fun observeUsageWrapped(): Flow<UsageWrappedState> = store.map { it.usageWrapped }

    override fun observeThreads(): Flow<List<ThreadSummary>> = store.map { it.threads }

    override fun observeThreadDetail(threadId: String): Flow<ThreadDetail?> =
        store.map { it.threadDetails[threadId] }

    override fun observeActiveItemIds(threadId: String): Flow<Set<String>> =
        store.map { it.activeItemIdsByThread[threadId].orEmpty() }

    override fun observeApprovals(): Flow<List<ApprovalItem>> = store.map { it.approvals }

    override fun observeUserInputRequests(): Flow<List<ThreadUserInputRequest>> =
        store.map { it.userInputRequests }

    override fun observeDynamicToolRequests(): Flow<List<ThreadDynamicToolRequest>> =
        store.map { emptyList() }

    override fun observeComposerCatalog(): Flow<ComposerCatalog> = store.map { it.composerCatalog }

    override fun observeUnreadThreadResultDigests(): Flow<Map<String, ThreadResultDigest>> =
        store.map { it.unreadThreadResultDigests }

    override fun observeInAppThreadNotifications(): Flow<List<InAppThreadNotification>> =
        store.map { it.inAppThreadNotifications }

    override suspend fun saveHost(
        name: String,
        address: String,
        port: Int,
        desktopId: String?,
        activate: Boolean,
    ): String? {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        if (trimmedName.isEmpty() || trimmedAddress.isEmpty()) return null

        AppLog.action(
            name = "save_host",
            detail = "$trimmedName@$trimmedAddress:$port",
        )
        val resolvedId = desktopId ?: trimmedName.lowercase().replace(" ", "-")
        val upsertResult = upsertHostProfile(
            currentHosts = store.value.hosts,
            generatedId = resolvedId,
            name = trimmedName,
            address = trimmedAddress,
            port = port,
            kind = HostKind.Desktop,
            desktopId = desktopId,
            activate = activate,
        )
        store.update { current ->
            current.copy(
                hosts = upsertResult.hosts,
                connection = if (activate) {
                    ConnectionState(
                        activeHostId = upsertResult.hostId,
                        phase = ConnectionPhase.Connected,
                        message = "ws://$trimmedAddress:$port",
                    )
                } else {
                    current.connection
                },
            )
        }
        return upsertResult.hostId
    }

    override suspend fun setActiveHost(hostId: String) {
        AppLog.action(name = "activate_host", detail = hostId)
        store.update { current ->
            val selectedHost = current.hosts.firstOrNull { host -> host.id == hostId } ?: return@update current
            current.copy(
                hosts = current.hosts.map { host ->
                    host.copy(isActive = host.id == hostId)
                },
                connection = ConnectionState(
                    activeHostId = hostId,
                    phase = ConnectionPhase.Connected,
                    message = "ws://${selectedHost.address}:${selectedHost.port}",
                ),
            )
        }
    }

    override suspend fun renameHost(hostId: String, name: String): Boolean {
        val updatedHosts = renameHostProfile(
            currentHosts = store.value.hosts,
            hostId = hostId,
            name = name,
        ) ?: return false

        AppLog.action(name = "rename_host", detail = hostId)
        store.update { current ->
            current.copy(hosts = updatedHosts)
        }
        return true
    }

    override suspend fun removeHost(hostId: String): Boolean {
        val removalResult = removeHostProfile(
            currentHosts = store.value.hosts,
            hostId = hostId,
        )
        val removedHost = removalResult.removedHost ?: return false
        val shouldDisconnect = removedHost.isActive || store.value.connection.activeHostId == hostId

        AppLog.action(name = "remove_host", detail = hostId)
        store.update { current ->
            current.copy(
                hosts = removalResult.hosts,
                connection = if (shouldDisconnect) {
                    ConnectionState(phase = ConnectionPhase.Idle)
                } else {
                    current.connection
                },
                account = if (shouldDisconnect) AccountState() else current.account,
                rateLimits = if (shouldDisconnect) AccountRateLimits() else current.rateLimits,
                usageWrapped = if (shouldDisconnect) UsageWrappedState() else current.usageWrapped,
                threads = if (shouldDisconnect) emptyList() else current.threads,
                threadDetails = if (shouldDisconnect) emptyMap() else current.threadDetails,
                activeItemIdsByThread = if (shouldDisconnect) emptyMap() else current.activeItemIdsByThread,
                approvals = if (shouldDisconnect) emptyList() else current.approvals,
                userInputRequests = if (shouldDisconnect) emptyList() else current.userInputRequests,
                composerCatalog = if (shouldDisconnect) ComposerCatalog() else current.composerCatalog,
                unreadThreadResultDigests = if (shouldDisconnect) emptyMap() else current.unreadThreadResultDigests,
                inAppThreadNotifications = if (shouldDisconnect) emptyList() else current.inAppThreadNotifications,
            )
        }
        return true
    }

    override suspend fun setThemePreference(preference: ThemePreference) {
        AppLog.action(name = "set_theme_preference", detail = preference.name)
        store.update { current ->
            current.copy(
                preferences = current.preferences.copy(themePreference = preference),
            )
        }
    }

    override suspend fun setConnectionAlerts(enabled: Boolean) {
        AppLog.action(name = "set_connection_alerts", detail = enabled.toString())
        store.update { current ->
            current.copy(
                preferences = current.preferences.copy(connectionAlerts = enabled),
            )
        }
    }

    override suspend fun resolveApproval(
        approvalId: String,
        decision: ApprovalDecision,
    ) {
        AppLog.action(name = "resolve_approval", detail = "$approvalId->$decision")
        store.update { current ->
            val approval = current.approvals.firstOrNull { it.id == approvalId } ?: return@update current
            val remainingApprovals = current.approvals.filterNot { it.id == approvalId }
            val updatedDetails = current.threadDetails.mapValues { (threadId, detail) ->
                if (threadId != approval.threadId) {
                    detail
                } else {
                    detail.copy(
                        items = detail.items.map { item ->
                            when {
                                item.id != approval.itemId -> item
                                item is ThreadItem.CommandExecution -> item.copy(
                                    status = decision.toThreadItemStatus(),
                                )

                                item is ThreadItem.FileChange -> item.copy(
                                    status = decision.toThreadItemStatus(),
                                )

                                else -> item
                            }
                        },
                    )
                }
            }
            val updatedThreads = current.threads.map { thread ->
                if (thread.id != approval.threadId) {
                    thread
                } else {
                    thread.copy(
                        preview = previewForApprovalDecision(decision),
                        updatedAtEpochSeconds = nowEpochSeconds(),
                        status = statusForApprovalResolution(
                            threadId = thread.id,
                            approvals = remainingApprovals,
                            decision = decision,
                        ),
                    )
                }
            }
            current.copy(
                approvals = remainingApprovals,
                threads = updatedThreads,
                threadDetails = syncThreadSummaries(updatedDetails, updatedThreads),
            )
        }
    }

    override suspend fun createThread(cwd: String?): String? = store.value.threads
        .firstOrNull { thread -> cwd.isNullOrBlank() || thread.cwd == cwd }
        ?.id

    override suspend fun openThread(threadId: String) {
        AppLog.action(name = "open_thread", detail = threadId)
        store.update { current ->
            current.copy(
                unreadThreadResultDigests = current.unreadThreadResultDigests - threadId,
                inAppThreadNotifications = current.inAppThreadNotifications.filterNot { notification ->
                    notification.threadId == threadId
                },
            )
        }
    }

    override fun setVisibleThread(threadId: String?) {
        if (threadId == null) return
        store.update { current ->
            current.copy(
                unreadThreadResultDigests = current.unreadThreadResultDigests - threadId,
                inAppThreadNotifications = current.inAppThreadNotifications.filterNot { notification ->
                    notification.threadId == threadId
                },
            )
        }
    }

    override suspend fun refreshThreads() {
        AppLog.action(name = "refresh_threads", detail = "demo")
        delay(250)
    }

    override suspend fun refreshUsageWrapped() {
        AppLog.action(name = "refresh_usage_wrapped", detail = "demo")
        delay(180)
        store.update { current ->
            current.copy(usageWrapped = demoUsageWrappedState())
        }
    }

    override suspend fun dismissInAppThreadNotification(notificationId: String) {
        store.update { current ->
            current.copy(
                inAppThreadNotifications = current.inAppThreadNotifications.filterNot { notification ->
                    notification.id == notificationId
                },
            )
        }
    }

    override suspend fun respondToUserInput(
        requestId: String,
        response: ThreadUserInputResponse,
    ) {
        AppLog.action(
            name = "respond_user_input",
            detail = "demo request=$requestId response=${response.demoLogLabel()}",
        )
        store.update { current ->
            current.copy(
                userInputRequests = current.userInputRequests.filterNot { request ->
                    request.requestId == requestId
                },
            )
        }
    }

    override suspend fun respondToDynamicTool(
        requestId: String,
        response: ThreadDynamicToolResponse,
    ) {
        AppLog.action(
            name = "respond_dynamic_tool",
            detail = "demo request=$requestId response=$response",
        )
    }

    override suspend fun refreshComposerCatalog() {
        AppLog.action(name = "refresh_composer_catalog", detail = "demo")
    }

    override suspend fun sendReply(threadId: String, request: ThreadReplyRequest) {
        val cleanMessage = request.message.trim().ifBlank {
            request.skill?.let { "$${it.name}" } ?: request.image?.label.orEmpty()
        }
        if (!request.hasPayload) return

        AppLog.action(
            name = "send_reply",
            detail = "thread=$threadId chars=${cleanMessage.length}",
        )
        store.update { current ->
            val detail = current.threadDetails[threadId] ?: return@update current
            val updatedThreads = current.threads.map { summary ->
                if (summary.id == threadId) {
                    summary.copy(
                        preview = cleanMessage,
                        updatedAtEpochSeconds = nowEpochSeconds(),
                        status = activeStatus(),
                    )
                } else {
                    summary
                }
            }
            val updatedDetails = current.threadDetails + (
                threadId to detail.copy(
                    items = detail.items + ThreadItem.UserMessage(
                        id = nextItemId("user"),
                        text = cleanMessage,
                    ),
                )
            )
            current.copy(
                threads = updatedThreads,
                activeItemIdsByThread = current.activeItemIdsByThread + (threadId to emptySet()),
                threadDetails = syncThreadSummaries(updatedDetails, updatedThreads),
            )
        }

        delay(650)

        store.update { current ->
            val detail = current.threadDetails[threadId] ?: return@update current
            val responseText = "Queued the follow-up and kept the next turn scoped to app-server supported actions."
            val updatedThreads = current.threads.map { summary ->
                if (summary.id == threadId) {
                    summary.copy(
                        preview = responseText,
                        updatedAtEpochSeconds = nowEpochSeconds(),
                        status = statusAfterAssistantReply(
                            threadId = threadId,
                            approvals = current.approvals,
                        ),
                    )
                } else {
                    summary
                }
            }
            val updatedDetails = current.threadDetails + (
                threadId to detail.copy(
                    items = detail.items + ThreadItem.AgentMessage(
                        id = nextItemId("agent"),
                        text = responseText,
                        phase = "commentary",
                    ),
                )
            )
            current.copy(
                threads = updatedThreads,
                activeItemIdsByThread = current.activeItemIdsByThread - threadId,
                threadDetails = syncThreadSummaries(updatedDetails, updatedThreads),
            )
        }
    }

    override suspend fun interruptThread(threadId: String) {
        AppLog.action(name = "interrupt_thread", detail = threadId)
        store.update { current ->
            val updatedThreads = current.threads.map { summary ->
                if (summary.id == threadId) {
                    summary.copy(
                        preview = "Turn interrupted from mobile.",
                        updatedAtEpochSeconds = nowEpochSeconds(),
                        status = statusAfterInterrupt(
                            threadId = threadId,
                            approvals = current.approvals,
                        ),
                    )
                } else {
                    summary
                }
            }
            current.copy(
                threads = updatedThreads,
                activeItemIdsByThread = current.activeItemIdsByThread - threadId,
                threadDetails = syncThreadSummaries(current.threadDetails, updatedThreads),
            )
        }
    }
}

private fun ThreadUserInputResponse.demoLogLabel(): String = when (this) {
    is ThreadUserInputResponse.Accept -> "accept"
    ThreadUserInputResponse.Decline -> "decline"
    ThreadUserInputResponse.Cancel -> "cancel"
}

private fun demoThreads(now: Long = nowEpochSeconds()): List<ThreadSummary> = listOf(
    ThreadSummary(
        id = "auth-refactor",
        name = "Refactor auth service",
        preview = "Waiting on a file-change approval for the auth middleware patch.",
        createdAtEpochSeconds = now - 4 * 60 * 60,
        updatedAtEpochSeconds = now - 2 * 60,
        modelProvider = "openai",
        ephemeral = false,
        status = waitingOnApprovalStatus(),
        source = ThreadSourceKind.Cli,
        cwd = "/projects/codex-mobile/auth-service",
        gitBranch = "auth-refactor",
        currentModelId = "gpt-5.4",
        currentModelName = "GPT-5.4",
        currentReasoningEffort = ComposerReasoningEffort.High,
        lastTurnTotalTokens = 3_200L,
        threadTotalTokens = 28_400L,
        contextRemainingPercent = 61,
    ),
    ThreadSummary(
        id = "auth-handshake",
        name = "Validate OAuth handshake",
        preview = "Waiting on approval before installing a new dependency for the OAuth callback flow.",
        createdAtEpochSeconds = now - 5 * 60 * 60,
        updatedAtEpochSeconds = now - 45 * 60,
        modelProvider = "openai",
        ephemeral = false,
        status = waitingOnApprovalStatus(),
        source = ThreadSourceKind.Cli,
        cwd = "/projects/codex-mobile/api",
        gitBranch = "oauth-hardening",
        currentModelId = "gpt-5.4",
        currentModelName = "GPT-5.4",
        currentReasoningEffort = ComposerReasoningEffort.Medium,
        lastTurnTotalTokens = 1_180L,
        threadTotalTokens = 12_460L,
        contextRemainingPercent = 77,
    ),
    ThreadSummary(
        id = "theme-sync",
        name = "Recover theme provider sync",
        preview = "The last validation command failed with a palette lookup error.",
        createdAtEpochSeconds = now - 8 * 60 * 60,
        updatedAtEpochSeconds = now - 60 * 60,
        modelProvider = "openai",
        ephemeral = false,
        status = ThreadStatus(type = ThreadStatusType.SystemError),
        source = ThreadSourceKind.VsCode,
        cwd = "/projects/ui-kit",
        gitBranch = "theme-provider-sync",
        currentModelId = "gpt-5.4-mini",
        currentModelName = "GPT-5.4 Mini",
        currentReasoningEffort = ComposerReasoningEffort.Low,
    ),
    ThreadSummary(
        id = "analytics-dashboard",
        name = "Generate analytics dashboard review",
        preview = "Stored thread with the last completed export summary.",
        createdAtEpochSeconds = now - 12 * 60 * 60,
        updatedAtEpochSeconds = now - 3 * 60 * 60,
        modelProvider = "openai",
        ephemeral = false,
        status = ThreadStatus(type = ThreadStatusType.NotLoaded),
        source = ThreadSourceKind.AppServer,
        cwd = "/projects/codex-mobile/analytics",
        gitBranch = "dashboard-review",
        currentModelId = "gpt-5.4",
        currentModelName = "GPT-5.4",
        currentReasoningEffort = ComposerReasoningEffort.Medium,
    ),
    ThreadSummary(
        id = "server-migration",
        name = "Prepare storage migration",
        preview = "Streaming the current rsync pass to the edge storage snapshot.",
        createdAtEpochSeconds = now - 18 * 60 * 60,
        updatedAtEpochSeconds = now - 5 * 60 * 60,
        modelProvider = "openai",
        ephemeral = true,
        status = activeStatus(),
        source = ThreadSourceKind.Exec,
        cwd = "/projects/platform",
        gitBranch = "storage-migration",
        currentModelId = "gpt-5.4",
        currentModelName = "GPT-5.4",
        currentReasoningEffort = ComposerReasoningEffort.XHigh,
    ),
)

private fun demoThreadDetails(threads: List<ThreadSummary>): Map<String, ThreadDetail> {
    val threadsById = threads.associateBy { it.id }
    return mapOf(
        "auth-refactor" to ThreadDetail(
            summary = requireNotNull(threadsById["auth-refactor"]),
            items = listOf(
                ThreadItem.UserMessage(
                    id = "auth-refactor-user",
                    text = "Refactor the authentication module to support multi-tenant JWT validation and keep the approval surface small.",
                ),
                ThreadItem.Reasoning(
                    id = "auth-refactor-reasoning",
                    summary = "Codex narrowed the patch to the auth middleware and paused before writing changes so the client can review the file-change item.",
                ),
                ThreadItem.FileChange(
                    id = "file-change-auth-item",
                    changes = listOf(
                        FileChangeEntry(
                            path = "src/middleware/auth.ts",
                            kind = "modified",
                            diff = "Inject tenant context before JWT verification.",
                        ),
                        FileChangeEntry(
                            path = "src/routes/user.ts",
                            kind = "modified",
                            diff = "Route middleware updated to use the consolidated auth pipeline.",
                        ),
                    ),
                    status = ThreadItemStatus.InProgress,
                ),
            ),
        ),
        "auth-handshake" to ThreadDetail(
            summary = requireNotNull(threadsById["auth-handshake"]),
            items = listOf(
                ThreadItem.UserMessage(
                    id = "auth-handshake-user",
                    text = "Validate the OAuth callback handshake and install any missing browser-side dependency only if it is required.",
                ),
                ThreadItem.CommandExecution(
                    id = "command-install-item",
                    command = "npm install @stripe/stripe-js",
                    cwd = "/projects/codex-mobile/api",
                    status = ThreadItemStatus.InProgress,
                    aggregatedOutput = "Preparing dependency graph before the install and waiting for approval.",
                ),
            ),
        ),
        "theme-sync" to ThreadDetail(
            summary = requireNotNull(threadsById["theme-sync"]),
            items = listOf(
                ThreadItem.UserMessage(
                    id = "theme-sync-user",
                    text = "Repair the theme provider sync and verify the palette contract in dark mode.",
                ),
                ThreadItem.CommandExecution(
                    id = "theme-sync-command",
                    command = "pnpm test --filter theme-provider",
                    cwd = "/projects/ui-kit",
                    status = ThreadItemStatus.Failed,
                    aggregatedOutput = "TypeError: Cannot read properties of undefined (reading 'color')",
                    exitCode = 1,
                ),
                ThreadItem.AgentMessage(
                    id = "theme-sync-agent",
                    text = "The current theme provider still dereferences a missing palette entry in dark mode.",
                    phase = "final_answer",
                ),
            ),
        ),
        "analytics-dashboard" to ThreadDetail(
            summary = requireNotNull(threadsById["analytics-dashboard"]),
            items = listOf(
                ThreadItem.UserMessage(
                    id = "analytics-user",
                    text = "Generate the analytics dashboard review and summarize the export health before I open the laptop again.",
                ),
                ThreadItem.Plan(
                    id = "analytics-plan",
                    text = "1. Validate the dashboard queries. 2. Check export freshness. 3. Summarize blockers only if any remain.",
                ),
                ThreadItem.AgentMessage(
                    id = "analytics-agent",
                    text = "The export completed successfully and the dashboard review did not surface new blockers.",
                    phase = "final_answer",
                ),
            ),
        ),
        "server-migration" to ThreadDetail(
            summary = requireNotNull(threadsById["server-migration"]),
            items = listOf(
                ThreadItem.UserMessage(
                    id = "migration-user",
                    text = "Prepare the next storage migration pass and stream only the essential execution details.",
                ),
                ThreadItem.CommandExecution(
                    id = "migration-command",
                    command = "rsync -av legacy/ edge-storage:/snapshots/phase-4",
                    cwd = "/projects/platform",
                    status = ThreadItemStatus.InProgress,
                    aggregatedOutput = "Transferred 182 assets so far and still streaming the current pass.",
                ),
            ),
        ),
    )
}

private fun demoApprovals(): List<ApprovalItem> = listOf(
    ApprovalItem(
        id = "command-install",
        threadId = "auth-handshake",
        turnId = "turn-auth-handshake",
        itemId = "command-install-item",
        kind = ApprovalKind.CommandExecution,
        command = "npm install @stripe/stripe-js",
        cwd = "/projects/codex-mobile/api",
        reason = "Codex needs approval before installing a new package.",
        availableDecisions = listOf(
            ApprovalDecision.Accept,
            ApprovalDecision.AcceptForSession,
            ApprovalDecision.Decline,
            ApprovalDecision.Cancel,
        ),
    ),
    ApprovalItem(
        id = "file-change-auth",
        threadId = "auth-refactor",
        turnId = "turn-auth-refactor",
        itemId = "file-change-auth-item",
        kind = ApprovalKind.FileChange,
        filePaths = listOf(
            "src/middleware/auth.ts",
            "src/routes/user.ts",
        ),
        reason = "Codex wants permission to write the proposed auth refactor to the workspace.",
        availableDecisions = listOf(
            ApprovalDecision.Accept,
            ApprovalDecision.AcceptForSession,
            ApprovalDecision.Decline,
            ApprovalDecision.Cancel,
        ),
    ),
)

private fun demoRateLimits(now: Long = nowEpochSeconds()): AccountRateLimits {
    val currentLimit = AccountRateLimit(
        limitId = "codex",
        primary = AccountRateLimitWindow(
            usedPercent = 22,
            windowDurationMins = 300,
            resetsAtEpochSeconds = now + 3 * 60 * 60 + 18 * 60,
        ),
        secondary = AccountRateLimitWindow(
            usedPercent = 41,
            windowDurationMins = 10_080,
            resetsAtEpochSeconds = now + 5 * 24 * 60 * 60 + 7 * 60 * 60,
        ),
    )
    return AccountRateLimits(
        current = currentLimit,
        byLimitId = mapOf(currentLimit.limitId to currentLimit),
    )
}

private fun demoUsageWrappedState(): UsageWrappedState {
    val today: LocalDate = LocalDate.now()
    val activity: List<UsageWrappedActivityDay> = (0 until 120).mapNotNull { index ->
        val date: LocalDate = today.minusDays((119 - index).toLong())
        val sessionCount: Int = when {
            date.dayOfWeek.value >= 6 && index % 3 != 0 -> 0
            date.dayOfWeek.value >= 6 -> 1
            index % 11 == 0 -> 3
            index % 4 == 0 -> 2
            else -> 1
        }
        if (sessionCount == 0) {
            null
        } else {
            UsageWrappedActivityDay(
                date = date.toString(),
                sessionCount = sessionCount,
                totalTokens = sessionCount * (18_000L + (index % 7) * 6_400L),
            )
        }
    }
    val totalTokens: Long = activity.sumOf(UsageWrappedActivityDay::totalTokens)
    val totalSessions: Int = activity.sumOf(UsageWrappedActivityDay::sessionCount)

    return UsageWrappedState(
        hostId = "work-laptop",
        summary = UsageWrappedSummary(
            generatedAt = "${today}T12:00:00Z",
            range = UsageWrappedRange(
                start = activity.firstOrNull()?.date,
                end = activity.lastOrNull()?.date,
            ),
            overview = UsageWrappedOverview(
                startedAt = "2025-10-02",
                activeDays = activity.size,
                sessionCount = totalSessions,
                projectCount = 17,
                currentStreakDays = 9,
                longestStreakDays = 18,
            ),
            tokenTotals = UsageWrappedTokenTotals(
                input = totalTokens * 62 / 100,
                cachedInput = totalTokens * 30 / 100,
                output = totalTokens * 5 / 100,
                reasoning = totalTokens * 3 / 100,
                total = totalTokens,
            ),
            costEstimate = UsageWrappedCostEstimate(
                approximateUsd = 1264.12,
                coveragePercent = 100,
                note = "Estimated using public GPT-5 and Codex API pricing. Reasoning tokens are treated at output-token rates.",
            ),
            highlights = UsageWrappedHighlights(
                mostActiveDay = activity.maxByOrNull(UsageWrappedActivityDay::totalTokens)?.let { day ->
                    UsageWrappedDaySummary(
                        date = day.date,
                        sessionCount = day.sessionCount,
                        totalTokens = day.totalTokens,
                    )
                },
                mostActiveProject = UsageWrappedProjectSummary(
                    cwd = "D:/projects/codexAndroidApp",
                    sessionCount = 41,
                    totalTokens = totalTokens / 3,
                ),
                mostUsedSource = UsageWrappedSourceSummary(
                    source = "vscode",
                    sessionCount = totalSessions - 14,
                ),
            ),
            activity = activity,
        ),
    )
}

private fun demoComposerCatalog(): ComposerCatalog = ComposerCatalog(
    models = listOf(
        ComposerModelOption(
            id = "gpt-5.4",
            displayName = "GPT-5.4",
            defaultReasoningEffort = ComposerReasoningEffort.Medium,
            supportedReasoningEfforts = listOf(
                ComposerReasoningEffortOption(ComposerReasoningEffort.Low, "Lower latency"),
                ComposerReasoningEffortOption(ComposerReasoningEffort.Medium, "Balanced"),
                ComposerReasoningEffortOption(ComposerReasoningEffort.High, "Deeper reasoning"),
            ),
            supportsPersonality = true,
            supportsImageInput = true,
            isDefault = true,
        ),
        ComposerModelOption(
            id = "gpt-5.1-codex",
            displayName = "GPT-5.1 Codex",
            defaultReasoningEffort = ComposerReasoningEffort.Medium,
            supportedReasoningEfforts = listOf(
                ComposerReasoningEffortOption(ComposerReasoningEffort.Minimal, "Fastest"),
                ComposerReasoningEffortOption(ComposerReasoningEffort.Medium, "Balanced"),
                ComposerReasoningEffortOption(ComposerReasoningEffort.XHigh, "Maximum depth"),
            ),
            supportsPersonality = true,
            supportsImageInput = false,
        ),
    ),
    skills = listOf(
        ComposerSkillOption(
            name = "brainstorming",
            path = "/demo/skills/brainstorming/SKILL.md",
            displayName = "Brainstorming",
            shortDescription = "Explore options before implementation.",
        ),
        ComposerSkillOption(
            name = "compose-ui",
            path = "/demo/skills/compose-ui/SKILL.md",
            displayName = "Compose UI",
            shortDescription = "Compose design and refactor guidance.",
        ),
    ),
)

private fun demoActiveItemIdsByThread(): Map<String, Set<String>> = mapOf(
    "auth-refactor" to setOf("auth-refactor-reasoning", "file-change-auth-item"),
    "auth-handshake" to setOf("command-install-item"),
    "server-migration" to setOf("migration-command"),
)

private fun syncThreadSummaries(
    threadDetails: Map<String, ThreadDetail>,
    threads: List<ThreadSummary>,
): Map<String, ThreadDetail> {
    val threadsById = threads.associateBy { it.id }
    return threadDetails.mapValues { (threadId, detail) ->
        val summary = threadsById[threadId] ?: detail.summary
        detail.copy(summary = summary)
    }
}

private fun nowEpochSeconds(): Long = System.currentTimeMillis() / 1_000

private fun nextItemId(prefix: String): String = "$prefix-${System.currentTimeMillis()}"

private fun activeStatus(): ThreadStatus = ThreadStatus(type = ThreadStatusType.Active)

private fun waitingOnApprovalStatus(): ThreadStatus =
    ThreadStatus(
        type = ThreadStatusType.Active,
        activeFlags = setOf("waitingOnApproval"),
    )

private fun statusForApprovalResolution(
    threadId: String,
    approvals: List<ApprovalItem>,
    decision: ApprovalDecision,
): ThreadStatus {
    if (approvals.any { it.threadId == threadId }) {
        return waitingOnApprovalStatus()
    }
    return when (decision) {
        ApprovalDecision.Accept,
        ApprovalDecision.AcceptForSession,
        is ApprovalDecision.AcceptWithExecpolicyAmendment,
        is ApprovalDecision.ApplyNetworkPolicyAmendment,
        -> activeStatus()

        ApprovalDecision.Decline,
        ApprovalDecision.Cancel,
        -> ThreadStatus(type = ThreadStatusType.Idle)
    }
}

private fun statusAfterAssistantReply(
    threadId: String,
    approvals: List<ApprovalItem>,
): ThreadStatus = if (approvals.any { it.threadId == threadId }) {
    waitingOnApprovalStatus()
} else {
    ThreadStatus(type = ThreadStatusType.Idle)
}

private fun statusAfterInterrupt(
    threadId: String,
    approvals: List<ApprovalItem>,
): ThreadStatus = if (approvals.any { it.threadId == threadId }) {
    waitingOnApprovalStatus()
} else {
    ThreadStatus(type = ThreadStatusType.Idle)
}

private fun ApprovalDecision.toThreadItemStatus(): ThreadItemStatus = when (this) {
    ApprovalDecision.Accept,
    ApprovalDecision.AcceptForSession,
    is ApprovalDecision.AcceptWithExecpolicyAmendment,
    is ApprovalDecision.ApplyNetworkPolicyAmendment,
    -> ThreadItemStatus.Completed

    ApprovalDecision.Decline,
    ApprovalDecision.Cancel,
    -> ThreadItemStatus.Declined
}

private fun previewForApprovalDecision(decision: ApprovalDecision): String = when (decision) {
    ApprovalDecision.Accept -> "Approval accepted from mobile. Codex can continue the turn."
    ApprovalDecision.AcceptForSession -> "Approval accepted for the current session from mobile."
    is ApprovalDecision.AcceptWithExecpolicyAmendment ->
        "Approval accepted and the suggested command rule was applied from mobile."
    is ApprovalDecision.ApplyNetworkPolicyAmendment ->
        "Approval accepted and the suggested network rule was applied from mobile."
    ApprovalDecision.Decline -> "Approval declined from mobile."
    ApprovalDecision.Cancel -> "Approval cancelled from mobile."
}

