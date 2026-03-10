package dev.codex.mobile.core.data.demo

import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ApprovalKind
import dev.codex.mobile.core.model.ApprovalRisk
import dev.codex.mobile.core.model.ApprovalState
import dev.codex.mobile.core.model.ExecutionKind
import dev.codex.mobile.core.model.ExecutionLine
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ReasoningStep
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThemePreference
import dev.codex.mobile.core.model.TimelineEntry
import dev.codex.mobile.core.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

private data class DemoStoreState(
    val preferences: AppPreferences = AppPreferences(),
    val hosts: List<HostProfile> = emptyList(),
    val threads: List<ThreadSummary> = emptyList(),
    val threadDetails: Map<String, ThreadDetail> = emptyMap(),
    val approvals: List<ApprovalItem> = emptyList(),
)

class DemoCodexRepository : CodexRepository {
    private val store = MutableStateFlow(
        DemoStoreState(
            preferences = AppPreferences(
                themePreference = ThemePreference.Dark,
                connectionAlerts = true,
                secureShellEnabled = true,
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
            threads = demoThreads(),
            threadDetails = demoThreadDetails(),
            approvals = demoApprovals(),
        ),
    )

    override fun observePreferences(): Flow<AppPreferences> = store.map { it.preferences }

    override fun observeHosts(): Flow<List<HostProfile>> = store.map { it.hosts }

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

    override suspend fun setSecureShell(enabled: Boolean) {
        AppLog.action(name = "set_secure_shell", detail = enabled.toString())
        store.update { current ->
            current.copy(
                preferences = current.preferences.copy(secureShellEnabled = enabled),
            )
        }
    }

    override suspend fun resolveApproval(approvalId: String, newState: ApprovalState) {
        AppLog.action(name = "resolve_approval", detail = "$approvalId->$newState")
        store.update { current ->
            val approvals = current.approvals.map { approval ->
                if (approval.id == approvalId) approval.copy(state = newState) else approval
            }
            current.copy(
                approvals = approvals,
                threads = current.threads.map { thread ->
                    val hasPendingApproval = approvals.any { it.threadId == thread.id && it.state == ApprovalState.Pending }
                    when {
                        hasPendingApproval && thread.status == ThreadStatus.Completed ->
                            thread.copy(status = ThreadStatus.NeedsReview)
                        !hasPendingApproval && thread.status == ThreadStatus.NeedsReview ->
                            thread.copy(status = ThreadStatus.Completed)
                        else -> thread
                    }
                },
            )
        }
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
            current.copy(
                threadDetails = current.threadDetails + (
                    threadId to detail.copy(
                        timeline = detail.timeline + TimelineEntry.Message(
                            id = "reply-${System.currentTimeMillis()}",
                            timestampLabel = "Just now",
                            author = "You",
                            text = cleanMessage,
                        ),
                    )
                ),
                threads = current.threads.map { summary ->
                    if (summary.id == threadId) {
                        summary.copy(
                            snippet = cleanMessage,
                            status = ThreadStatus.Running,
                            progressPercent = 82,
                            remainingLabel = "1.1s remaining",
                        )
                    } else {
                        summary
                    }
                },
            )
        }

        delay(650)

        store.update { current ->
            val detail = current.threadDetails[threadId] ?: return@update current
            current.copy(
                threadDetails = current.threadDetails + (
                    threadId to detail.copy(
                        timeline = detail.timeline + TimelineEntry.Message(
                            id = "codex-${System.currentTimeMillis()}",
                            timestampLabel = "Just now",
                            author = "Codex",
                            text = "Queued the follow-up and re-scoped the patch set to keep the approval surface small.",
                        ),
                    )
                ),
                threads = current.threads.map { summary ->
                    if (summary.id == threadId) {
                        summary.copy(
                            snippet = "Queued the follow-up and tightened the patch set...",
                            status = ThreadStatus.Running,
                            progressPercent = 91,
                            remainingLabel = "0.6s remaining",
                        )
                    } else {
                        summary
                    }
                },
            )
        }
    }

    override suspend fun interruptThread(threadId: String) {
        AppLog.action(name = "interrupt_thread", detail = threadId)
        store.update { current ->
            current.copy(
                threads = current.threads.map { summary ->
                    if (summary.id == threadId) {
                        summary.copy(
                            status = ThreadStatus.NeedsReview,
                            progressPercent = null,
                            remainingLabel = "Interrupted",
                            snippet = "Interrupted from mobile. Waiting for next instruction.",
                        )
                    } else {
                        summary
                    }
                },
            )
        }
    }
}

private fun demoThreads(): List<ThreadSummary> = listOf(
    ThreadSummary(
        id = "auth-refactor",
        projectLabel = "Nexus Pipeline",
        title = "Refactoring Auth Service",
        snippet = "Processing middleware hooks and validation branches...",
        timeLabel = "2m ago",
        status = ThreadStatus.Running,
        participantInitials = listOf("AC", "RM"),
        progressPercent = 68,
        remainingLabel = "2.4s remaining",
    ),
    ThreadSummary(
        id = "auth-handshake",
        projectLabel = "Core API",
        title = "Authentication Handshake",
        snippet = "Security validation required for new OAuth endpoint...",
        timeLabel = "45m ago",
        status = ThreadStatus.NeedsReview,
        participantInitials = listOf("AC"),
    ),
    ThreadSummary(
        id = "theme-sync",
        projectLabel = "UI Kit",
        title = "Theme Provider Sync",
        snippet = "Runtime error: cannot read property 'color' of undefined...",
        timeLabel = "1h ago",
        status = ThreadStatus.Failed,
        participantInitials = listOf("SK", "RM"),
    ),
    ThreadSummary(
        id = "analytics-dashboard",
        projectLabel = "Data Visualization",
        title = "Analytics Dashboard",
        snippet = "Quarterly reports generated and exported successfully...",
        timeLabel = "3h ago",
        status = ThreadStatus.Completed,
        participantInitials = listOf("LP"),
    ),
    ThreadSummary(
        id = "server-migration",
        projectLabel = "DevOps",
        title = "Server Migration Phase 4",
        snippet = "Transferring legacy assets to edge storage...",
        timeLabel = "5h ago",
        status = ThreadStatus.Running,
        participantInitials = listOf("AC"),
    ),
)

private fun demoThreadDetails(): Map<String, ThreadDetail> {
    val runningSummary = demoThreads().first()
    return mapOf(
        "auth-refactor" to ThreadDetail(
            summary = runningSummary,
            hostName = "MacBook Pro 16\"",
            modelLabel = "GPT-5.4",
            timeline = listOf(
                TimelineEntry.UserIntent(
                    id = "intent",
                    timestampLabel = "10:24 AM",
                    text = "Refactor the authentication module to support multi-tenant JWT validation and consolidate common middleware.",
                ),
                TimelineEntry.Reasoning(
                    id = "reasoning",
                    timestampLabel = "10:25 AM",
                    summary = "Analyzing the existing auth surface and narrowing the patch to keep the approval small.",
                    steps = listOf(
                        ReasoningStep(
                            text = "Audit circular dependencies in src/lib/auth.ts",
                            completed = true,
                        ),
                        ReasoningStep(
                            text = "Define TenantConfig interface for dynamic provider resolution",
                            completed = true,
                        ),
                        ReasoningStep(
                            text = "Update middleware registry to inject tenant context",
                            completed = false,
                        ),
                    ),
                ),
                TimelineEntry.ExecutionLog(
                    id = "log",
                    timestampLabel = "10:27 AM",
                    lines = listOf(
                        ExecutionLine(1, ExecutionKind.Run, "npm list --depth=0"),
                        ExecutionLine(2, ExecutionKind.Info, "Mapping dependencies for @codex/auth..."),
                        ExecutionLine(3, ExecutionKind.Patch, "Applying hunk #1 to core/security.ts"),
                        ExecutionLine(4, ExecutionKind.Write, "Created src/services/tenant-manager.ts"),
                        ExecutionLine(5, ExecutionKind.Warn, "2 linting errors detected in auth-utils.ts"),
                    ),
                ),
                TimelineEntry.ProposedChange(
                    id = "proposal",
                    timestampLabel = "Just now",
                    title = "Multi-tenant Architecture Update",
                    createdCount = 3,
                    modifiedCount = 124,
                ),
            ),
        ),
    )
}

private fun demoApprovals(): List<ApprovalItem> = listOf(
    ApprovalItem(
        id = "command-install",
        threadId = "auth-handshake",
        kind = ApprovalKind.Command,
        title = "Command Approval",
        subtitle = "npm install @stripe/stripe-js",
        detail = "CWD: /projects/codex-mobile/api • Scope: System modification (packages)",
        risk = ApprovalRisk.HighImpact,
        state = ApprovalState.Pending,
        primaryActionLabel = "Approve",
        secondaryActionLabel = "Decline",
        requestTimeLabel = "Just now",
    ),
    ApprovalItem(
        id = "file-change-auth",
        threadId = "auth-refactor",
        kind = ApprovalKind.FileChange,
        title = "File-Change Approval",
        subtitle = "Update Auth Middleware",
        detail = "Modified 2 files • src/middleware/auth.ts, src/routes/user.ts",
        risk = ApprovalRisk.MediumRisk,
        state = ApprovalState.Pending,
        primaryActionLabel = "Accept Diff",
        secondaryActionLabel = "Review",
        tertiaryActionLabel = "Archive",
        requestTimeLabel = "2m ago",
    ),
    ApprovalItem(
        id = "deploy-stage",
        threadId = "analytics-dashboard",
        kind = ApprovalKind.Deployment,
        title = "Deployment Request",
        subtitle = "Deploy 'feature/redesign' to staging",
        detail = "Requested by Alex Chen • Safe preview environment",
        risk = ApprovalRisk.LowRisk,
        state = ApprovalState.Archived,
        primaryActionLabel = "Restore",
        secondaryActionLabel = "Dismiss",
        requestTimeLabel = "14m ago",
        authorLabel = "Alex Chen",
    ),
)
