package dev.codex.mobile.core.data

import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.AccountRateLimits
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ComposerCatalog
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ThreadReplyRequest
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadUserInputResponse
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThemePreference
import dev.codex.mobile.core.model.UsageWrappedState
import kotlinx.coroutines.flow.Flow

interface CodexRepository {
    fun observePreferences(): Flow<AppPreferences>

    fun observeHosts(): Flow<List<HostProfile>>

    fun observeConnection(): Flow<ConnectionState>

    fun observeAccount(): Flow<AccountState>

    fun observeRateLimits(): Flow<AccountRateLimits?>

    fun observeUsageWrapped(): Flow<UsageWrappedState>

    fun observeThreads(): Flow<List<ThreadSummary>>

    fun observeThreadDetail(threadId: String): Flow<ThreadDetail?>

    fun observeActiveItemIds(threadId: String): Flow<Set<String>>

    fun observeApprovals(): Flow<List<ApprovalItem>>

    fun observeUserInputRequests(): Flow<List<ThreadUserInputRequest>>

    fun observeComposerCatalog(): Flow<ComposerCatalog>

    fun observeUnreadThreadResultDigests(): Flow<Map<String, ThreadResultDigest>>

    fun observeInAppThreadNotifications(): Flow<List<InAppThreadNotification>>

    suspend fun saveHost(
        name: String,
        address: String,
        port: Int,
        desktopId: String? = null,
        activate: Boolean = false,
    ): String?

    suspend fun setActiveHost(hostId: String)

    suspend fun setThemePreference(preference: ThemePreference)

    suspend fun setConnectionAlerts(enabled: Boolean)

    suspend fun resolveApproval(
        approvalId: String,
        decision: ApprovalDecision,
    )

    suspend fun createThread(cwd: String? = null): String?

    suspend fun openThread(threadId: String)

    fun setVisibleThread(threadId: String?)

    suspend fun refreshThreads()

    suspend fun refreshUsageWrapped()

    suspend fun dismissInAppThreadNotification(notificationId: String)

    suspend fun respondToUserInput(
        requestId: String,
        response: ThreadUserInputResponse,
    )

    suspend fun refreshComposerCatalog()

    suspend fun sendReply(
        threadId: String,
        request: ThreadReplyRequest,
    )

    suspend fun interruptThread(threadId: String)
}
