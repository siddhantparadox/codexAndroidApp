package dev.codex.mobile.core.data.demo

import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.AccountStatus
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.FileChangeEntry
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThemePreference
import dev.codex.mobile.core.util.AppLog
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
    val threads: List<ThreadSummary> = emptyList(),
    val threadDetails: Map<String, ThreadDetail> = emptyMap(),
    val approvals: List<ApprovalItem> = emptyList(),
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
                    name = "MacBook Pro 16\"",
                    address = "192.168.1.15",
                    port = 4500,
                    kind = HostKind.Laptop,
                    isActive = true,
                ),
                HostProfile(
                    id = "home-desktop",
                    name = "Home Desktop",
                    address = "192.168.1.42",
                    port = 4500,
                    kind = HostKind.Desktop,
                ),
            ),
            threads = initialThreads,
            threadDetails = demoThreadDetails(initialThreads),
            approvals = demoApprovals(),
        ),
    )

    override fun observePreferences(): Flow<AppPreferences> = store.map { it.preferences }

    override fun observeHosts(): Flow<List<HostProfile>> = store.map { it.hosts }

    override fun observeConnection(): Flow<ConnectionState> = store.map { it.connection }

    override fun observeAccount(): Flow<AccountState> = store.map { it.account }

    override fun observeThreads(): Flow<List<ThreadSummary>> = store.map { it.threads }

    override fun observeThreadDetail(threadId: String): Flow<ThreadDetail?> =
        store.map { it.threadDetails[threadId] }

    override fun observeApprovals(): Flow<List<ApprovalItem>> = store.map { it.approvals }

    override suspend fun saveHost(name: String, address: String, port: Int) {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        if (trimmedName.isEmpty() || trimmedAddress.isEmpty()) return

        AppLog.action(
            name = "save_host",
            detail = "$trimmedName@$trimmedAddress:$port",
        )
        store.update { current ->
            current.copy(
                hosts = current.hosts + HostProfile(
                    id = trimmedName.lowercase().replace(" ", "-"),
                    name = trimmedName,
                    address = trimmedAddress,
                    port = port,
                    kind = HostKind.Desktop,
                ),
            )
        }
    }

    override suspend fun setActiveHost(hostId: String) {
        AppLog.action(name = "activate_host", detail = hostId)
        store.update { current ->
            current.copy(
                hosts = current.hosts.map { host ->
                    host.copy(isActive = host.id == hostId)
                },
            )
        }
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

    override suspend fun createThread(): String? = store.value.threads.firstOrNull()?.id

    override suspend fun openThread(threadId: String) {
        AppLog.action(name = "open_thread", detail = threadId)
    }

    override suspend fun sendReply(threadId: String, message: String) {
        val cleanMessage = message.trim()
        if (cleanMessage.isEmpty()) return

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
                threadDetails = syncThreadSummaries(current.threadDetails, updatedThreads),
            )
        }
    }
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
    -> ThreadItemStatus.Completed

    ApprovalDecision.Decline,
    ApprovalDecision.Cancel,
    -> ThreadItemStatus.Declined
}

private fun previewForApprovalDecision(decision: ApprovalDecision): String = when (decision) {
    ApprovalDecision.Accept -> "Approval accepted from mobile. Codex can continue the turn."
    ApprovalDecision.AcceptForSession -> "Approval accepted for the current session from mobile."
    ApprovalDecision.Decline -> "Approval declined from mobile."
    ApprovalDecision.Cancel -> "Approval cancelled from mobile."
}
