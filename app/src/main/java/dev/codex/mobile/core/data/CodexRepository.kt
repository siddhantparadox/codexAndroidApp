package dev.codex.mobile.core.data

import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow

interface CodexRepository {
    fun observePreferences(): Flow<AppPreferences>

    fun observeHosts(): Flow<List<HostProfile>>

    fun observeThreads(): Flow<List<ThreadSummary>>

    fun observeThreadDetail(threadId: String): Flow<ThreadDetail?>

    fun observeApprovals(): Flow<List<ApprovalItem>>

    suspend fun saveHost(
        name: String,
        address: String,
        port: Int,
    )

    suspend fun setActiveHost(hostId: String)

    suspend fun setThemePreference(preference: ThemePreference)

    suspend fun setConnectionAlerts(enabled: Boolean)

    suspend fun resolveApproval(
        approvalId: String,
        decision: ApprovalDecision,
    )

    suspend fun sendReply(
        threadId: String,
        message: String,
    )

    suspend fun interruptThread(threadId: String)
}
