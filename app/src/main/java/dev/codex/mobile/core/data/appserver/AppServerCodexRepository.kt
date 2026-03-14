package dev.codex.mobile.core.data.appserver

import android.content.Context
import android.net.Uri
import android.util.Base64
import dev.codex.mobile.BuildConfig
import dev.codex.mobile.core.data.CodexRepository
import dev.codex.mobile.core.data.local.AppLocalStateStore
import dev.codex.mobile.core.data.local.PersistedAppState
import dev.codex.mobile.core.data.local.PersistedThreadSettings
import dev.codex.mobile.core.model.AccountState
import dev.codex.mobile.core.model.AppPreferences
import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalItem
import dev.codex.mobile.core.model.ComposerCatalog
import dev.codex.mobile.core.model.ComposerPersonality
import dev.codex.mobile.core.model.ComposerReasoningEffort
import dev.codex.mobile.core.model.ComposerSandboxMode
import dev.codex.mobile.core.model.ConnectionPhase
import dev.codex.mobile.core.model.ConnectionState
import dev.codex.mobile.core.model.HostKind
import dev.codex.mobile.core.model.HostProfile
import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ThreadReplyRequest
import dev.codex.mobile.core.model.ThreadDetail
import dev.codex.mobile.core.model.ThreadActivity
import dev.codex.mobile.core.model.ThreadActivityEmphasis
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import dev.codex.mobile.core.model.ThreadUserInputRequest
import dev.codex.mobile.core.model.ThemePreference
import dev.codex.mobile.core.model.isWaitingOnApproval
import dev.codex.mobile.core.model.isWaitingOnUserInput
import dev.codex.mobile.core.model.previewText
import dev.codex.mobile.core.util.AppLog
import java.net.UnknownHostException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient

private data class RepositoryState(
    val preferences: AppPreferences = AppPreferences(),
    val hosts: List<HostProfile> = emptyList(),
    val connection: ConnectionState = ConnectionState(),
    val account: AccountState = AccountState(),
    val threads: List<ThreadSummary> = emptyList(),
    val threadDetails: Map<String, ThreadDetail> = emptyMap(),
    val activeItemIdsByThread: Map<String, Set<String>> = emptyMap(),
    val threadItemCache: Map<String, List<ThreadItem>> = emptyMap(),
    val threadSettingsCache: Map<String, PersistedThreadSettings> = emptyMap(),
    val approvals: List<ApprovalItem> = emptyList(),
    val userInputRequests: List<ThreadUserInputRequest> = emptyList(),
    val activeTurnIds: Map<String, String> = emptyMap(),
    val composerCatalog: ComposerCatalog = ComposerCatalog(),
    val unreadThreadResultDigests: Map<String, ThreadResultDigest> = emptyMap(),
    val inAppThreadNotifications: List<InAppThreadNotification> = emptyList(),
    val visibleThreadId: String? = null,
    val lastResultTurnIdByThread: Map<String, String> = emptyMap(),
)

private data class PendingApprovalRequest(
    val requestId: JsonPrimitive,
    val method: String,
)

private data class PendingUserInputRequest(
    val requestId: JsonPrimitive,
)

internal class AppServerCodexRepository(
    context: Context,
    private val applicationScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
) : CodexRepository {
    private val appContext: Context = context.applicationContext
    private val localStateStore: AppLocalStateStore = AppLocalStateStore(
        context = appContext,
        json = appServerJson,
        ioDispatcher = ioDispatcher,
    )
    private val repositoryState: MutableStateFlow<RepositoryState> = MutableStateFlow(RepositoryState())
    private val connectMutex: Mutex = Mutex()
    private val threadsRefreshMutex: Mutex = Mutex()
    private val openedThreadIds: MutableSet<String> = linkedSetOf()
    private val pendingApprovalRequests: MutableMap<String, PendingApprovalRequest> = linkedMapOf()
    private val pendingUserInputRequests: MutableMap<String, PendingUserInputRequest> = linkedMapOf()

    @Volatile
    private var session: CodexAppServerSession? = null

    @Volatile
    private var sessionEventsJob: Job? = null

    @Volatile
    private var persistStateJob: Job? = null

    init {
        applicationScope.launch {
            restoreLocalState()
        }
    }

    override fun observePreferences(): Flow<AppPreferences> = repositoryState.map { it.preferences }

    override fun observeHosts(): Flow<List<HostProfile>> = repositoryState.map { it.hosts }

    override fun observeConnection(): Flow<ConnectionState> = repositoryState.map { it.connection }

    override fun observeAccount(): Flow<AccountState> = repositoryState.map { it.account }

    override fun observeThreads(): Flow<List<ThreadSummary>> = repositoryState.map { it.threads }

    override fun observeThreadDetail(threadId: String): Flow<ThreadDetail?> =
        repositoryState.map { it.threadDetails[threadId] }

    override fun observeActiveItemIds(threadId: String): Flow<Set<String>> =
        repositoryState.map { it.activeItemIdsByThread[threadId].orEmpty() }

    override fun observeApprovals(): Flow<List<ApprovalItem>> = repositoryState.map { it.approvals }

    override fun observeUserInputRequests(): Flow<List<ThreadUserInputRequest>> =
        repositoryState.map { it.userInputRequests }

    override fun observeComposerCatalog(): Flow<ComposerCatalog> = repositoryState.map { it.composerCatalog }

    override fun observeUnreadThreadResultDigests(): Flow<Map<String, ThreadResultDigest>> =
        repositoryState.map { it.unreadThreadResultDigests }

    override fun observeInAppThreadNotifications(): Flow<List<InAppThreadNotification>> =
        repositoryState.map { it.inAppThreadNotifications }

    override suspend fun saveHost(
        name: String,
        address: String,
        port: Int,
    ) {
        val trimmedName = name.trim()
        val trimmedAddress = address.trim()
        if (trimmedName.isEmpty() || trimmedAddress.isEmpty()) return

        AppLog.action(
            name = "save_host",
            detail = "$trimmedName@$trimmedAddress:$port",
        )

        repositoryState.update { current ->
            current.copy(
                hosts = current.hosts + HostProfile(
                    id = hostId(name = trimmedName, address = trimmedAddress, port = port),
                    name = trimmedName,
                    address = trimmedAddress,
                    port = port,
                    kind = inferHostKind(trimmedName),
                ),
            )
        }
        persistLocalState()
    }

    override suspend fun setActiveHost(hostId: String) {
        AppLog.action(name = "activate_host", detail = hostId)

        repositoryState.update { current ->
            current.copy(
                hosts = current.hosts.map { host ->
                    host.copy(isActive = host.id == hostId)
                },
            )
        }
        persistLocalState()
        reconnectToActiveHost()
    }

    override suspend fun setThemePreference(preference: ThemePreference) {
        AppLog.action(name = "set_theme_preference", detail = preference.name)
        repositoryState.update { current ->
            current.copy(
                preferences = current.preferences.copy(themePreference = preference),
            )
        }
        persistLocalState()
    }

    override suspend fun setConnectionAlerts(enabled: Boolean) {
        AppLog.action(name = "set_connection_alerts", detail = enabled.toString())
        repositoryState.update { current ->
            current.copy(
                preferences = current.preferences.copy(connectionAlerts = enabled),
            )
        }
        persistLocalState()
    }

    override suspend fun resolveApproval(
        approvalId: String,
        decision: ApprovalDecision,
    ) {
        val pending = pendingApprovalRequests[approvalId] ?: return
        val currentSession = session ?: return

        AppLog.action(name = "resolve_approval", detail = "$approvalId->$decision")

        val payload = when (pending.method) {
            "item/commandExecution/requestApproval" -> commandApprovalDecisionPayload(decision)
            "item/fileChange/requestApproval" -> fileChangeApprovalDecisionPayload(decision)
            else -> return
        }

        currentSession.respondToRequest(
            requestId = pending.requestId,
            result = payload,
        )
    }

    override suspend fun createThread(): String? {
        val currentSession = session ?: return null
        val response = currentSession.threadStart()
        val thread = response.objectAt("thread") ?: return null
        val sessionSettings = response.toThreadSessionSettings()

        AppLog.action(name = "create_thread", detail = thread.string("id"))
        openedThreadIds += requireNotNull(thread.string("id"))
        applyThreadSnapshot(thread, sessionSettings)
        return thread.string("id")
    }

    override suspend fun openThread(threadId: String) {
        clearThreadResultSignals(threadId)
        openedThreadIds += threadId
        val currentSession = session ?: return
        val resumedResponse = runCatching {
            currentSession.threadResume(threadId)
        }.getOrNull()
        val resumedThread = resumedResponse?.objectAt("thread")
        val resumedSettings = resumedResponse?.toThreadSessionSettings()
        val thread = runCatching {
            currentSession.threadRead(threadId = threadId, includeTurns = true).objectAt("thread")
        }.getOrNull()
            ?: resumedThread
            ?: runCatching {
                AppLog.action(name = "open_thread_pending", detail = threadId)
                currentSession.threadRead(threadId = threadId, includeTurns = false).objectAt("thread")
            }.getOrNull()
            ?: return

        AppLog.action(name = "open_thread_live", detail = threadId)
        applyThreadSnapshot(thread, resumedSettings)
    }

    override fun setVisibleThread(threadId: String?) {
        repositoryState.update { current ->
            current.copy(
                visibleThreadId = threadId,
                unreadThreadResultDigests = if (threadId == null) {
                    current.unreadThreadResultDigests
                } else {
                    current.unreadThreadResultDigests - threadId
                },
                inAppThreadNotifications = if (threadId == null) {
                    current.inAppThreadNotifications
                } else {
                    current.inAppThreadNotifications.filterNot { notification ->
                        notification.threadId == threadId
                    }
                },
            )
        }
    }

    override suspend fun refreshThreads() {
        val currentSession = session ?: return
        threadsRefreshMutex.withLock {
            val current = repositoryState.value
            val threads = fetchThreadSummaries(
                currentSession = currentSession,
                composerCatalog = current.composerCatalog,
                threadSettingsCache = current.threadSettingsCache,
                approvals = current.approvals,
                userInputRequests = current.userInputRequests,
                activeTurnIds = current.activeTurnIds,
            )
            AppLog.action(name = "refresh_threads", detail = "count=${threads.size}")
            repositoryState.update { latest ->
                latest.copy(
                    threads = threads,
                    threadDetails = latest.threadDetails.syncWithThreads(threads),
                )
            }
            flushPersistLocalState()
        }
    }

    override suspend fun dismissInAppThreadNotification(notificationId: String) {
        repositoryState.update { current ->
            current.copy(
                inAppThreadNotifications = current.inAppThreadNotifications.filterNot { notification ->
                    notification.id == notificationId
                },
            )
        }
    }

    override suspend fun respondToUserInput(
        requestId: String,
        answers: Map<String, List<String>>,
    ) {
        val pending = pendingUserInputRequests[requestId] ?: return
        val currentSession = session ?: return
        val sanitizedAnswers: Map<String, List<String>> = answers.mapValues { (_, values) ->
            values.map(String::trim).filter(String::isNotBlank)
        }.filterValues { values -> values.isNotEmpty() }
        if (sanitizedAnswers.isEmpty()) return

        AppLog.action(
            name = "respond_user_input",
            detail = "request=$requestId answers=${sanitizedAnswers.size}",
        )

        currentSession.respondToRequest(
            requestId = pending.requestId,
            result = toolRequestUserInputResponsePayload(sanitizedAnswers),
        )
    }

    override suspend fun refreshComposerCatalog() {
        val currentSession = session ?: return
        val catalog = runCatching {
            loadComposerCatalog(currentSession = currentSession, forceReload = true)
        }.getOrElse {
            AppLog.action(name = "refresh_composer_catalog_failed", detail = it.message.orEmpty())
            return
        }
        repositoryState.update { current ->
            current.copy(composerCatalog = catalog)
        }
    }

    override suspend fun sendReply(
        threadId: String,
        request: ThreadReplyRequest,
    ) {
        if (!request.hasPayload) return

        val currentSession = session ?: return
        if (threadId !in openedThreadIds) {
            openThread(threadId)
        }

        val input = buildReplyInput(request)
        if (input.isEmpty()) return
        val activeTurnId = repositoryState.value.activeTurnIds[threadId]
        AppLog.action(
            name = "send_reply",
            detail = "thread=$threadId chars=${request.message.trim().length} input=${input.size}",
        )

        if (activeTurnId != null) {
            currentSession.turnSteer(
                threadId = threadId,
                expectedTurnId = activeTurnId,
                input = input,
            )
        } else {
            val response = currentSession.turnStart(
                threadId = threadId,
                input = input,
                model = request.modelId,
                effort = request.reasoningEffort.toWireValue(),
                personality = request.personality.toWireValue(),
                sandboxPolicy = request.sandboxMode.toSandboxPolicyPayload(),
            )
            if (request.modelId != null || request.reasoningEffort != null) {
                updateThreadSettings(
                    threadId = threadId,
                    settings = ThreadSessionSettings(
                        modelId = request.modelId ?: currentThreadModelId(threadId),
                        reasoningEffort = request.reasoningEffort ?: currentThreadReasoningEffort(threadId),
                    ),
                )
            }
            val turnId = response.objectAt("turn")?.string("id")
            if (turnId != null) {
                repositoryState.update { current ->
                    current.copy(
                        activeTurnIds = current.activeTurnIds + (threadId to turnId),
                        threads = current.threads.map { thread ->
                            if (thread.id == threadId) {
                                thread.copy(
                                    status = ThreadStatus(type = ThreadStatusType.Active),
                                )
                            } else {
                                thread
                            }
                        }.syncThreadOrdering(),
                    )
                }
            }
        }
    }

    override suspend fun interruptThread(threadId: String) {
        val currentSession = session ?: return
        var turnId = repositoryState.value.activeTurnIds[threadId]
        if (turnId == null) {
            openThread(threadId)
            turnId = repositoryState.value.activeTurnIds[threadId]
        }
        if (turnId == null) {
            AppLog.action(name = "interrupt_thread_unavailable", detail = threadId)
            error("No active turn available to interrupt for thread $threadId")
        }
        AppLog.action(name = "interrupt_thread", detail = "$threadId turn=$turnId")
        currentSession.turnInterrupt(
            threadId = threadId,
            turnId = turnId,
        )
    }

    private suspend fun restoreLocalState(): Unit {
        val localState = localStateStore.load()
        AppLog.action(name = "restore_local_state", detail = "cachedThreads=${localState.threadItemCache.size}")
        repositoryState.update { current ->
            val hydratedThreadDetails = current.threadDetails.mapValues { (threadId, detail) ->
                detail.copy(items = localState.threadItemCache[threadId].orEmpty().ifEmpty { detail.items })
            }
            current.copy(
                preferences = localState.preferences,
                hosts = localState.hosts,
                threadDetails = hydratedThreadDetails,
                threadItemCache = localState.threadItemCache,
                threadSettingsCache = localState.threadSettingsCache,
            )
        }

        if (localState.hosts.any { it.isActive }) {
            reconnectToActiveHost()
        }
    }

    private suspend fun persistLocalState(): Unit {
        val current = repositoryState.value
        localStateStore.save(
            PersistedAppState(
                preferences = current.preferences,
                hosts = current.hosts,
                threadItemCache = current.threadItemCache,
                threadSettingsCache = current.threadSettingsCache,
            ),
        )
        AppLog.action(name = "persist_local_state", detail = "cachedThreads=${current.threadItemCache.size}")
    }

    private fun schedulePersistLocalState(): Unit {
        persistStateJob?.cancel()
        persistStateJob = applicationScope.launch(ioDispatcher) {
            delay(350)
            persistLocalState()
        }
    }

    private fun flushPersistLocalState(): Unit {
        persistStateJob?.cancel()
        persistStateJob = applicationScope.launch(ioDispatcher) {
            persistLocalState()
        }
    }

    private suspend fun reconnectToActiveHost(): Unit = connectMutex.withLock {
        val previousActiveHostId = repositoryState.value.connection.activeHostId
        sessionEventsJob?.cancel()
        sessionEventsJob = null
        session?.close()
        session = null
        pendingApprovalRequests.clear()
        pendingUserInputRequests.clear()

        val activeHost = repositoryState.value.hosts.firstOrNull { it.isActive }
        val preserveThreadCache = shouldPreserveThreadCache(
            previousActiveHostId = previousActiveHostId,
            nextActiveHostId = activeHost?.id,
            currentThreadItemCache = repositoryState.value.threadItemCache,
        )
        if (activeHost == null) {
            repositoryState.update { current ->
                current.copy(
                    connection = ConnectionState(phase = ConnectionPhase.Idle),
                account = AccountState(),
                threads = emptyList(),
                threadDetails = emptyMap(),
                activeItemIdsByThread = emptyMap(),
                threadItemCache = emptyMap(),
                threadSettingsCache = emptyMap(),
                approvals = emptyList(),
                userInputRequests = emptyList(),
                activeTurnIds = emptyMap(),
                composerCatalog = ComposerCatalog(),
                unreadThreadResultDigests = emptyMap(),
                inAppThreadNotifications = emptyList(),
                visibleThreadId = null,
                lastResultTurnIdByThread = emptyMap(),
                )
            }
            schedulePersistLocalState()
            return
        }

        val socketUrl = "ws://${activeHost.address}:${activeHost.port}"
        repositoryState.update { current ->
            current.copy(
                connection = ConnectionState(
                    activeHostId = activeHost.id,
                    phase = ConnectionPhase.Connecting,
                    message = "Connecting to $socketUrl",
                ),
                account = AccountState(),
                threads = emptyList(),
                threadDetails = emptyMap(),
                activeItemIdsByThread = emptyMap(),
                threadItemCache = if (preserveThreadCache) current.threadItemCache else emptyMap(),
                threadSettingsCache = if (preserveThreadCache) current.threadSettingsCache else emptyMap(),
                approvals = emptyList(),
                userInputRequests = emptyList(),
                activeTurnIds = emptyMap(),
                composerCatalog = ComposerCatalog(),
                unreadThreadResultDigests = emptyMap(),
                inAppThreadNotifications = emptyList(),
                lastResultTurnIdByThread = emptyMap(),
            )
        }
        schedulePersistLocalState()

        val newSession = CodexAppServerSession(
            transport = CodexJsonRpcTransport(
                okHttpClient = okHttpClient,
                url = socketUrl,
            ),
            versionName = BuildConfig.VERSION_NAME,
        )

        runCatching {
            newSession.connect()
            val account = newSession.accountRead().toAccountState()
            val cachedThreadSettings = repositoryState.value.threadSettingsCache
            val composerCatalog = runCatching {
                loadComposerCatalog(currentSession = newSession)
            }.getOrElse {
                AppLog.action(name = "load_composer_catalog_failed", detail = it.message.orEmpty())
                ComposerCatalog()
            }
            val threads = fetchThreadSummaries(
                currentSession = newSession,
                composerCatalog = composerCatalog,
                threadSettingsCache = cachedThreadSettings,
                approvals = emptyList(),
                userInputRequests = emptyList(),
                activeTurnIds = emptyMap(),
            )

            session = newSession
            repositoryState.update { current ->
                current.copy(
                    connection = ConnectionState(
                        activeHostId = activeHost.id,
                        phase = ConnectionPhase.Connected,
                        message = socketUrl,
                    ),
                    account = account,
                    threads = threads,
                    threadDetails = current.threadDetails.syncWithThreads(threads),
                    composerCatalog = composerCatalog,
                )
            }

            sessionEventsJob = applicationScope.launch {
                newSession.events.collect { event ->
                    handleTransportEvent(activeHostId = activeHost.id, event = event)
                }
            }

            reopenObservedThreads()
        }.onFailure { error ->
            newSession.close()
            session = null
            repositoryState.update { current ->
                current.copy(
                    connection = ConnectionState(
                        activeHostId = activeHost.id,
                        phase = ConnectionPhase.Error,
                        message = error.toConnectionMessage(),
                    ),
                    account = AccountState(),
                    threads = emptyList(),
                    threadDetails = emptyMap(),
                    activeItemIdsByThread = emptyMap(),
                    approvals = emptyList(),
                    userInputRequests = emptyList(),
                    activeTurnIds = emptyMap(),
                    unreadThreadResultDigests = emptyMap(),
                    inAppThreadNotifications = emptyList(),
                    lastResultTurnIdByThread = emptyMap(),
                )
            }
            schedulePersistLocalState()
        }
    }

    private suspend fun reopenObservedThreads(): Unit {
        val ids = openedThreadIds.toList()
        ids.forEach { threadId ->
            runCatching {
                openThread(threadId)
            }
        }
    }

    private fun clearThreadResultSignals(threadId: String): Unit {
        repositoryState.update { current ->
            current.copy(
                unreadThreadResultDigests = current.unreadThreadResultDigests - threadId,
                inAppThreadNotifications = current.inAppThreadNotifications.filterNot { notification ->
                    notification.threadId == threadId
                },
            )
        }
    }

    private suspend fun synthesizeThreadResult(
        threadId: String,
        turnId: String,
        turnStatus: String,
        turnError: String?,
    ) {
        if (turnStatus == "interrupted") return

        val stateSnapshot: RepositoryState = repositoryState.value
        if (stateSnapshot.visibleThreadId == threadId) {
            clearThreadResultSignals(threadId)
            return
        }
        if (stateSnapshot.lastResultTurnIdByThread[threadId] == turnId) return

        val currentSession: CodexAppServerSession = session ?: return
        val threadObject = runCatching {
            currentSession.threadRead(
                threadId = threadId,
                includeTurns = true,
            ).objectAt("thread")
        }.getOrNull() ?: return
        val turnResult: ThreadTurnResult = threadObject.toThreadTurnResult(
            turnId = turnId,
            fallbackTurnStatus = turnStatus,
            fallbackTurnError = turnError,
        ) ?: return
        AppLog.action(
            name = "thread_result_ready",
            detail = "thread=$threadId turn=$turnId kind=${turnResult.digest.kind.name}",
        )
        repositoryState.update { current ->
            if (current.visibleThreadId == threadId || current.lastResultTurnIdByThread[threadId] == turnId) {
                current
            } else {
                current.copy(
                    unreadThreadResultDigests = current.unreadThreadResultDigests + (threadId to turnResult.digest),
                    inAppThreadNotifications = listOf(
                        turnResult.toInAppThreadNotification(
                            turnId = turnId,
                            createdAtEpochSeconds = currentEpochSeconds(),
                        ),
                    ) + current.inAppThreadNotifications.filterNot { notification ->
                        notification.threadId == threadId
                    }.take(MAX_IN_APP_THREAD_NOTIFICATIONS - 1),
                    lastResultTurnIdByThread = current.lastResultTurnIdByThread + (threadId to turnId),
                )
            }
        }
    }

    private fun handleTransportEvent(
        activeHostId: String,
        event: TransportEvent,
    ): Unit = when (event) {
        is TransportEvent.Notification -> handleNotification(event.method, event.params)
        is TransportEvent.ServerRequest -> handleServerRequest(event)
        is TransportEvent.Closed -> handleTransportClosed(
            activeHostId = activeHostId,
            event = event,
        )
    }

    private fun handleNotification(
        method: String,
        params: kotlinx.serialization.json.JsonObject,
    ): Unit = when (method) {
        "thread/status/changed" -> handleThreadStatusChanged(params)
        "turn/started" -> handleTurnStarted(params)
        "turn/completed" -> handleTurnCompleted(params)
        "turn/diff/updated" -> handleTurnDiffUpdated(params)
        "turn/plan/updated" -> handleTurnPlanUpdated(params)
        "thread/tokenUsage/updated" -> handleThreadTokenUsageUpdated(params)
        "item/started" -> handleItemStarted(params)
        "item/completed" -> handleItemCompleted(params)
        "item/agentMessage/delta" -> appendAgentDelta(params)
        "item/plan/delta" -> appendPlanDelta(params)
        "item/reasoning/summaryTextDelta" -> appendReasoningDelta(params)
        "item/reasoning/summaryPartAdded" -> appendReasoningSummaryBoundary(params)
        "item/reasoning/textDelta" -> appendReasoningTextDelta(params)
        "item/commandExecution/outputDelta" -> appendCommandOutputDelta(params)
        "item/fileChange/outputDelta" -> appendFileChangeOutputDelta(params)
        "mcpToolCall/progress" -> handleMcpToolCallProgress(params)
        "terminal/interaction" -> handleTerminalInteraction(params)
        "rawResponseItem/completed" -> handleRawResponseItemCompleted(params)
        "thread/realtime/started" -> handleRealtimeLifecycleActivity(params, title = "Realtime session started")
        "thread/realtime/itemAdded" -> handleRealtimeItemAdded(params)
        "thread/realtime/outputAudio/delta" -> handleRealtimeLifecycleActivity(params, title = "Realtime audio output updated")
        "thread/realtime/error" -> handleRealtimeLifecycleActivity(params, title = "Realtime session error", emphasis = ThreadActivityEmphasis.Error)
        "thread/realtime/closed" -> handleRealtimeLifecycleActivity(params, title = "Realtime session closed")
        "serverRequest/resolved" -> handleServerRequestResolved(params)
        else -> Unit
    }

    private fun handleServerRequest(request: TransportEvent.ServerRequest): Unit {
        val wrapper = buildJsonObject {
            put("method", request.method)
            put("params", request.params)
        }
        val threadId = request.params.string("threadId") ?: return
        val derivedItem = findThreadItem(
            threadId = threadId,
            itemId = request.params.string("itemId").orEmpty(),
        )

        when (request.method) {
            "item/commandExecution/requestApproval",
            "item/fileChange/requestApproval",
            -> {
                val approval = wrapper.toApprovalItem(request.requestId)?.let { base ->
                    when (derivedItem) {
                        is ThreadItem.CommandExecution -> base.copy(
                            command = base.command ?: derivedItem.command,
                            cwd = base.cwd ?: derivedItem.cwd,
                        )

                        is ThreadItem.FileChange -> base.copy(
                            filePaths = derivedItem.changes.map { it.path },
                        )

                        else -> base
                    }
                } ?: return

                pendingApprovalRequests[approval.id] = PendingApprovalRequest(
                    requestId = request.requestId,
                    method = request.method,
                )

                repositoryState.update { current ->
                    val updatedApprovals = (current.approvals.filterNot { it.id == approval.id } + approval)
                        .sortedBy { it.threadId }
                    current.copy(
                        approvals = updatedApprovals,
                        threads = current.threads.map { thread ->
                            if (thread.id == approval.threadId) {
                                thread.copy(
                                    status = statusWithPendingRequests(
                                        baseStatus = thread.status,
                                        threadId = thread.id,
                                        approvals = updatedApprovals,
                                        userInputRequests = current.userInputRequests,
                                        activeTurnId = current.activeTurnIds[thread.id],
                                    ),
                                )
                            } else {
                                thread
                            }
                        }.syncThreadOrdering(),
                        threadDetails = current.threadDetails.mapValues { (currentThreadId, detail) ->
                            if (currentThreadId == approval.threadId) {
                                detail.copy(
                                    summary = detail.summary.copy(
                                        status = statusWithPendingRequests(
                                            baseStatus = detail.summary.status,
                                            threadId = currentThreadId,
                                            approvals = updatedApprovals,
                                            userInputRequests = current.userInputRequests,
                                            activeTurnId = current.activeTurnIds[currentThreadId],
                                        ),
                                    ),
                                )
                            } else {
                                detail
                            }
                        },
                    )
                }
            }

            "item/tool/requestUserInput" -> {
                val userInputRequest = wrapper.toThreadUserInputRequest(request.requestId) ?: return
                pendingUserInputRequests[userInputRequest.requestId] = PendingUserInputRequest(
                    requestId = request.requestId,
                )
                AppLog.action(
                    name = "user_input_requested",
                    detail = "thread=${userInputRequest.threadId} request=${userInputRequest.requestId}",
                )
                appendActivity(
                    threadId = userInputRequest.threadId,
                    activity = ThreadActivity(
                        id = "user-input-${userInputRequest.requestId}",
                        title = "User input requested",
                        detail = userInputRequest.previewText,
                        emphasis = ThreadActivityEmphasis.Warning,
                    ),
                )
                repositoryState.update { current ->
                    val updatedUserInputRequests = current.userInputRequests
                        .filterNot { pending -> pending.requestId == userInputRequest.requestId } + userInputRequest
                    current.copy(
                        userInputRequests = updatedUserInputRequests,
                        threads = current.threads.map { thread ->
                            if (thread.id == userInputRequest.threadId) {
                                thread.copy(
                                    preview = userInputRequest.previewText,
                                    updatedAtEpochSeconds = currentEpochSeconds(),
                                    status = statusWithPendingRequests(
                                        baseStatus = thread.status,
                                        threadId = thread.id,
                                        approvals = current.approvals,
                                        userInputRequests = updatedUserInputRequests,
                                        activeTurnId = current.activeTurnIds[thread.id],
                                    ),
                                )
                            } else {
                                thread
                            }
                        }.syncThreadOrdering(),
                        threadDetails = current.threadDetails.mapValues { (currentThreadId, detail) ->
                            if (currentThreadId == userInputRequest.threadId) {
                                detail.copy(
                                    summary = detail.summary.copy(
                                        preview = userInputRequest.previewText,
                                        updatedAtEpochSeconds = currentEpochSeconds(),
                                        status = statusWithPendingRequests(
                                            baseStatus = detail.summary.status,
                                            threadId = currentThreadId,
                                            approvals = current.approvals,
                                            userInputRequests = updatedUserInputRequests,
                                            activeTurnId = current.activeTurnIds[currentThreadId],
                                        ),
                                    ),
                                )
                            } else {
                                detail
                            }
                        },
                    )
                }
            }

            else -> Unit
        }
    }

    private fun handleTransportClosed(
        activeHostId: String,
        event: TransportEvent.Closed,
    ): Unit {
        if (repositoryState.value.connection.activeHostId != activeHostId) return

        session = null
        pendingApprovalRequests.clear()
        pendingUserInputRequests.clear()
        repositoryState.update { current ->
            current.copy(
                connection = ConnectionState(
                    activeHostId = activeHostId,
                    phase = if (event.isError) ConnectionPhase.Error else ConnectionPhase.Disconnected,
                    message = event.message ?: if (event.isError) "Connection lost." else "Disconnected.",
                ),
                account = AccountState(),
                approvals = emptyList(),
                userInputRequests = emptyList(),
                activeTurnIds = emptyMap(),
                unreadThreadResultDigests = emptyMap(),
                inAppThreadNotifications = emptyList(),
                lastResultTurnIdByThread = emptyMap(),
            )
        }
    }

    private fun handleThreadStatusChanged(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        val incomingStatus = params.objectAt("status")?.toThreadStatus() ?: return
        val status = statusWithPendingRequests(
            baseStatus = incomingStatus,
            threadId = threadId,
            approvals = repositoryState.value.approvals,
            userInputRequests = repositoryState.value.userInputRequests,
            activeTurnId = repositoryState.value.activeTurnIds[threadId],
        )
        repositoryState.update { current ->
            current.copy(
                threads = current.threads.map { thread ->
                    if (thread.id == threadId) thread.copy(status = status) else thread
                }.syncThreadOrdering(),
                threadDetails = current.threadDetails.mapValues { (id, detail) ->
                    if (id == threadId) {
                        detail.copy(summary = detail.summary.copy(status = status))
                    } else {
                        detail
                    }
                },
            )
        }
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "thread-status-$threadId-${status.type.name}",
                title = "Thread status changed",
                detail = statusLabel(status),
                emphasis = when (status.type) {
                    ThreadStatusType.Active -> ThreadActivityEmphasis.Active
                    ThreadStatusType.SystemError -> ThreadActivityEmphasis.Error
                    else -> ThreadActivityEmphasis.Neutral
                },
            ),
        )
    }

    private fun handleTurnStarted(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        val turnId = params.objectAt("turn")?.string("id") ?: return
        clearThreadResultSignals(threadId)
        repositoryState.update { current ->
            current.copy(
                activeTurnIds = current.activeTurnIds + (threadId to turnId),
                activeItemIdsByThread = current.activeItemIdsByThread - threadId,
            )
        }
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "turn-started-$turnId",
                title = "Turn started",
                detail = turnId,
                emphasis = ThreadActivityEmphasis.Active,
            ),
        )
    }

    private fun handleTurnCompleted(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        val turn = params.objectAt("turn") ?: return
        val turnId = turn.string("id") ?: return
        repositoryState.update { current ->
            if (current.activeTurnIds[threadId] != turnId) {
                current.copy(
                    activeItemIdsByThread = current.activeItemIdsByThread - threadId,
                )
            } else {
                current.copy(
                    activeTurnIds = current.activeTurnIds - threadId,
                    activeItemIdsByThread = current.activeItemIdsByThread - threadId,
                )
            }
        }
        val turnStatus = turn.string("status").orEmpty()
        val turnError = turn.objectAt("error")?.string("message")
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "turn-completed-$turnId",
                title = "Turn $turnStatus",
                detail = turnError ?: turnId,
                emphasis = when (turnStatus) {
                    "completed" -> ThreadActivityEmphasis.Success
                    "failed" -> ThreadActivityEmphasis.Error
                    "interrupted" -> ThreadActivityEmphasis.Warning
                    else -> ThreadActivityEmphasis.Neutral
                },
            ),
        )
        applicationScope.launch {
            synthesizeThreadResult(
                threadId = threadId,
                turnId = turnId,
                turnStatus = turnStatus,
                turnError = turnError,
            )
        }
    }

    private fun handleTurnDiffUpdated(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "turn-diff-${params.string("turnId").orEmpty()}",
                title = "Unified diff updated",
                detail = params.string("diff"),
                emphasis = ThreadActivityEmphasis.Active,
            ),
        )
    }

    private fun handleTurnPlanUpdated(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        val explanation = params.string("explanation")
        val planLines = params.arrayAt("plan")
            ?.mapNotNull { entry ->
                val jsonEntry = entry.jsonObject
                val step = jsonEntry.string("step")
                val status = jsonEntry.string("status")
                if (step == null || status == null) null else "[$status] $step"
            }
            .orEmpty()
            .joinToString(separator = "\n")
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "turn-plan-${params.string("turnId").orEmpty()}",
                title = "Turn plan updated",
                detail = listOfNotNull(explanation, planLines.takeIf { it.isNotBlank() }).joinToString(separator = "\n\n").ifBlank { null },
                emphasis = ThreadActivityEmphasis.Active,
            ),
        )
    }

    private fun handleThreadTokenUsageUpdated(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        val tokenUsage = params.objectAt("tokenUsage") ?: return
        val total = tokenUsage.objectAt("total")
        val last = tokenUsage.objectAt("last")
        val contextRemainingPercent = tokenUsage.contextRemainingPercent()
        val detail = buildString {
            last?.let { lastUsage ->
                append("Last turn: ")
                append(lastUsage.long("totalTokens") ?: 0L)
                append(" total tokens")
            }
            total?.let { totalUsage ->
                if (isNotBlank()) append('\n')
                append("Thread total: ")
                append(totalUsage.long("totalTokens") ?: 0L)
                append(" total tokens")
            }
            contextRemainingPercent?.let { remainingPercent ->
                if (isNotBlank()) append('\n')
                append("Context remaining: ")
                append(remainingPercent)
                append('%')
            }
        }.ifBlank { null }
        repositoryState.update { current ->
            current.copy(
                threads = current.threads.map { thread ->
                    if (thread.id == threadId) {
                        thread.copy(contextRemainingPercent = contextRemainingPercent)
                    } else {
                        thread
                    }
                },
                threadDetails = current.threadDetails.mapValues { (id, threadDetail) ->
                    if (id == threadId) {
                        threadDetail.copy(
                            summary = threadDetail.summary.copy(
                                contextRemainingPercent = contextRemainingPercent,
                            ),
                        )
                    } else {
                        threadDetail
                    }
                },
            )
        }
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "token-usage-${params.string("turnId").orEmpty()}",
                title = "Token usage updated",
                detail = detail,
            ),
        )
    }

    private fun handleItemStarted(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        val item = params.objectAt("item")?.toThreadItem() ?: return
        markThreadItemActive(
            threadId = threadId,
            itemId = item.id,
            isActive = true,
        )
        upsertThreadItem(
            threadId = threadId,
            item = item,
            replaceExisting = false,
        )
    }

    private fun handleItemCompleted(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        val item = params.objectAt("item")?.toThreadItem() ?: return
        upsertThreadItem(
            threadId = threadId,
            item = item,
            replaceExisting = true,
        )
        markThreadItemActive(
            threadId = threadId,
            itemId = item.id,
            isActive = false,
        )
        flushPersistLocalState()
    }

    private fun appendAgentDelta(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.string("delta").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.AgentMessage -> item.copy(text = item.text + delta)
                null -> ThreadItem.AgentMessage(
                    id = params.string("itemId").orEmpty(),
                    text = delta,
                )

                else -> item
            }
        }
    }

    private fun appendPlanDelta(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.string("delta").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.Plan -> item.copy(text = item.text + delta)
                null -> ThreadItem.Plan(
                    id = params.string("itemId").orEmpty(),
                    text = delta,
                )

                else -> item
            }
        }
    }

    private fun appendReasoningDelta(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.string("delta").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.Reasoning -> {
                    val updatedSections = if (item.summarySections.isEmpty()) {
                        listOf(delta)
                    } else {
                        item.summarySections.dropLast(1) + (item.summarySections.last() + delta)
                    }
                    item.copy(
                        summary = updatedSections.joinToString(separator = "\n"),
                        summarySections = updatedSections,
                    )
                }

                null -> ThreadItem.Reasoning(
                    id = params.string("itemId").orEmpty(),
                    summary = delta,
                    summarySections = listOf(delta),
                )

                else -> item
            }
        }
    }

    private fun appendReasoningSummaryBoundary(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = "",
        ) { item, _ ->
            when (item) {
                is ThreadItem.Reasoning -> item.copy(
                    summarySections = item.summarySections + "",
                )

                else -> item
            }
        }
    }

    private fun appendReasoningTextDelta(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.string("delta").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.Reasoning -> item.copy(
                    contentText = item.contentText + delta,
                )

                null -> ThreadItem.Reasoning(
                    id = params.string("itemId").orEmpty(),
                    summary = "",
                    contentText = delta,
                )

                else -> item
            }
        }
    }

    private fun appendCommandOutputDelta(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.string("delta").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.CommandExecution -> item.copy(
                    aggregatedOutput = (item.aggregatedOutput ?: "") + delta,
                )

                else -> item
            }
        }
    }

    private fun appendFileChangeOutputDelta(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.elementAt("delta")?.toDisplayJson() ?: params.string("delta").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.FileChange -> item.copy(
                    toolOutput = (item.toolOutput ?: "") + delta,
                )

                else -> item
            }
        }
    }

    private fun handleMcpToolCallProgress(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.string("message").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.McpToolCall -> item.copy(
                    progressMessages = item.progressMessages + delta,
                )

                else -> item
            }
        }
    }

    private fun handleTerminalInteraction(params: kotlinx.serialization.json.JsonObject): Unit {
        appendToThreadItem(
            threadId = params.string("threadId") ?: return,
            itemId = params.string("itemId") ?: return,
            delta = params.string("stdin").orEmpty(),
        ) { item, delta ->
            when (item) {
                is ThreadItem.CommandExecution -> item.copy(
                    interactions = item.interactions + delta,
                )

                else -> item
            }
        }
    }

    private fun handleRawResponseItemCompleted(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "raw-response-${params.string("turnId").orEmpty()}",
                title = "Raw response item received",
                detail = params.objectAt("item")?.toDisplayJson(),
            ),
        )
    }

    private fun handleRealtimeItemAdded(params: kotlinx.serialization.json.JsonObject): Unit {
        val threadId = params.string("threadId") ?: return
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "realtime-item-$threadId",
                title = "Realtime item added",
                detail = params.elementAt("item")?.toDisplayJson(),
                emphasis = ThreadActivityEmphasis.Active,
            ),
        )
    }

    private fun handleRealtimeLifecycleActivity(
        params: kotlinx.serialization.json.JsonObject,
        title: String,
        emphasis: ThreadActivityEmphasis = ThreadActivityEmphasis.Neutral,
    ): Unit {
        val threadId = params.string("threadId") ?: return
        appendActivity(
            threadId = threadId,
            activity = ThreadActivity(
                id = "$title-$threadId",
                title = title,
                detail = params.toDisplayJson(),
                emphasis = emphasis,
            ),
        )
    }

    private fun handleServerRequestResolved(params: kotlinx.serialization.json.JsonObject): Unit {
        val requestId = params["requestId"]?.jsonPrimitive?.content ?: return
        val threadId = params.string("threadId") ?: return
        pendingApprovalRequests.remove(requestId)
        pendingUserInputRequests.remove(requestId)
        repositoryState.update { current ->
            val remainingApprovals = current.approvals.filterNot { it.id == requestId }
            val remainingUserInputRequests = current.userInputRequests.filterNot { it.requestId == requestId }
            current.copy(
                approvals = remainingApprovals,
                userInputRequests = remainingUserInputRequests,
                threads = current.threads.map { thread ->
                    if (
                        thread.id == threadId &&
                        (thread.status.isWaitingOnApproval || thread.status.isWaitingOnUserInput)
                    ) {
                        thread.copy(
                            status = statusWithPendingRequests(
                                baseStatus = thread.status,
                                threadId = thread.id,
                                approvals = remainingApprovals,
                                userInputRequests = remainingUserInputRequests,
                                activeTurnId = current.activeTurnIds[thread.id],
                            ),
                        )
                    } else {
                        thread
                    }
                }.syncThreadOrdering(),
                threadDetails = current.threadDetails.mapValues { (threadId, detail) ->
                    if (
                        threadId == detail.summary.id &&
                        (detail.summary.status.isWaitingOnApproval || detail.summary.status.isWaitingOnUserInput)
                    ) {
                        detail.copy(
                            summary = detail.summary.copy(
                                status = statusWithPendingRequests(
                                    baseStatus = detail.summary.status,
                                    threadId = threadId,
                                    approvals = remainingApprovals,
                                    userInputRequests = remainingUserInputRequests,
                                    activeTurnId = current.activeTurnIds[threadId],
                                ),
                            ),
                        )
                    } else {
                        detail
                    }
                },
            )
        }
    }

    private fun upsertThreadItem(
        threadId: String,
        item: ThreadItem,
        replaceExisting: Boolean,
    ): Unit {
        repositoryState.update { current ->
            val detail = current.threadDetails[threadId]
            val cachedItems = current.threadItemCache[threadId].orEmpty().ifEmpty { detail?.items.orEmpty() }
            val newItems = cachedItems.toMutableList()
            val existingIndex = newItems.indexOfFirst { existing -> existing.id == item.id }
            if (existingIndex >= 0) {
                newItems[existingIndex] = item
            } else {
                newItems += item
            }

            val updatedCache = current.threadItemCache + (threadId to newItems)
            if (detail == null) {
                return@update current.copy(threadItemCache = updatedCache)
            }

            val updatedDetail = detail.copy(
                summary = detail.summary.copy(preview = previewForItem(item, detail.summary.preview)),
                items = newItems,
            )
            current.copy(
                threadItemCache = updatedCache,
                threadDetails = current.threadDetails + (threadId to updatedDetail),
                threads = current.threads.map { thread ->
                    if (thread.id == threadId) {
                        updatedDetail.summary.copy(
                            updatedAtEpochSeconds = currentEpochSeconds(),
                        )
                    } else {
                        thread
                    }
                }.syncThreadOrdering(),
            )
        }
        schedulePersistLocalState()
    }

    private fun appendToThreadItem(
        threadId: String,
        itemId: String,
        delta: String,
        transform: (ThreadItem?, String) -> ThreadItem?,
    ): Unit {
        repositoryState.update { current ->
            val detail = current.threadDetails[threadId]
            val cachedItems = current.threadItemCache[threadId].orEmpty().ifEmpty { detail?.items.orEmpty() }
            val newItems = cachedItems.toMutableList()
            val existingIndex = newItems.indexOfFirst { item -> item.id == itemId }
            val existingItem = newItems.getOrNull(existingIndex)
            val transformedItem = transform(existingItem, delta) ?: return@update current
            if (existingIndex >= 0) {
                newItems[existingIndex] = transformedItem
            } else {
                newItems += transformedItem
            }

            val updatedCache = current.threadItemCache + (threadId to newItems)
            if (detail == null) {
                return@update current.copy(threadItemCache = updatedCache)
            }

            val updatedDetail = detail.copy(
                summary = detail.summary.copy(
                    preview = previewForItem(transformedItem, detail.summary.preview),
                ),
                items = newItems,
            )
            current.copy(
                threadItemCache = updatedCache,
                threadDetails = current.threadDetails + (threadId to updatedDetail),
                threads = current.threads.map { thread ->
                    if (thread.id == threadId) {
                        updatedDetail.summary.copy(
                            updatedAtEpochSeconds = currentEpochSeconds(),
                        )
                    } else {
                        thread
                    }
                }.syncThreadOrdering(),
            )
        }
        schedulePersistLocalState()
    }

    private fun appendActivity(
        threadId: String,
        activity: ThreadActivity,
    ) {
        repositoryState.update { current ->
            val detail = current.threadDetails[threadId] ?: return@update current
            val updatedActivities = (detail.activities.filterNot { it.id == activity.id } + activity)
                .takeLast(MAX_THREAD_ACTIVITIES)
            current.copy(
                threadDetails = current.threadDetails + (
                    threadId to detail.copy(activities = updatedActivities)
                    ),
            )
        }
    }

    private fun markThreadItemActive(
        threadId: String,
        itemId: String,
        isActive: Boolean,
    ): Unit {
        repositoryState.update { current ->
            val currentIds: Set<String> = current.activeItemIdsByThread[threadId].orEmpty()
            val updatedIds: Set<String> = if (isActive) {
                currentIds + itemId
            } else {
                currentIds - itemId
            }
            val updatedMap: Map<String, Set<String>> = if (updatedIds.isEmpty()) {
                current.activeItemIdsByThread - threadId
            } else {
                current.activeItemIdsByThread + (threadId to updatedIds)
            }
            current.copy(activeItemIdsByThread = updatedMap)
        }
    }

    private fun applyThreadSnapshot(
        thread: kotlinx.serialization.json.JsonObject,
        sessionSettings: ThreadSessionSettings? = null,
    ): Unit {
        val detail = thread.toThreadDetail()
        val activeTurnId = thread.extractActiveTurnId()
        repositoryState.update { current ->
            val existingDetail = current.threadDetails[detail.summary.id]
            val cachedItems = current.threadItemCache[detail.summary.id].orEmpty()
            val mergedSummary = detail.summary
                .withThreadSettings(
                    settings = sessionSettings?.toPersistedThreadSettings(
                        catalog = current.composerCatalog,
                    ) ?: current.threadSettingsCache[detail.summary.id],
                    catalog = current.composerCatalog,
                )
                .copy(
                    contextRemainingPercent = existingDetail?.summary?.contextRemainingPercent,
                )
                .let { summary ->
                    statusWithPendingRequests(
                        baseStatus = summary.status,
                        threadId = summary.id,
                        approvals = current.approvals,
                        userInputRequests = current.userInputRequests,
                        activeTurnId = activeTurnId ?: current.activeTurnIds[summary.id],
                    ).let { status ->
                        summary.copy(status = status)
                    }
                }
            val mergedDetail = detail.copy(
                items = mergeThreadItems(
                    existingItems = if (cachedItems.isNotEmpty()) cachedItems else existingDetail?.items.orEmpty(),
                    snapshotItems = detail.items,
                ),
                activities = existingDetail?.activities.orEmpty(),
                summary = mergedSummary,
            )
            val updatedThreadSettingsCache = sessionSettings?.let { settings ->
                current.threadSettingsCache + (
                    mergedDetail.summary.id to settings.toPersistedThreadSettings(
                        catalog = current.composerCatalog,
                    )
                )
            } ?: current.threadSettingsCache
            current.copy(
                threadItemCache = current.threadItemCache + (mergedDetail.summary.id to mergedDetail.items),
                threadSettingsCache = updatedThreadSettingsCache,
                threads = current.threads
                    .filterNot { it.id == mergedDetail.summary.id }
                    .plus(mergedDetail.summary)
                    .syncThreadOrdering(),
                threadDetails = (current.threadDetails + (mergedDetail.summary.id to mergedDetail))
                    .syncWithThreads(
                        current.threads
                            .filterNot { it.id == mergedDetail.summary.id }
                            .plus(mergedDetail.summary)
                            .syncThreadOrdering(),
                    ),
                activeTurnIds = if (activeTurnId == null) {
                    current.activeTurnIds - mergedDetail.summary.id
                } else {
                    current.activeTurnIds + (mergedDetail.summary.id to activeTurnId)
                },
            )
        }
        flushPersistLocalState()
    }

    private fun findThreadItem(
        threadId: String,
        itemId: String,
    ): ThreadItem? = repositoryState.value.threadDetails[threadId]
        ?.items
        ?.firstOrNull { item -> item.id == itemId }

    private suspend fun loadComposerCatalog(
        currentSession: CodexAppServerSession,
        forceReload: Boolean = false,
    ): ComposerCatalog = catalogFromResponses(
        modelResponse = currentSession.modelList(),
        skillsResponse = currentSession.skillsList(forceReload = forceReload),
    )

    private suspend fun buildReplyInput(request: ThreadReplyRequest): List<kotlinx.serialization.json.JsonObject> {
        val inputs = mutableListOf<kotlinx.serialization.json.JsonObject>()
        val text = request.toWireMessageText()
        if (text.isNotBlank()) {
            inputs += text.toTextInput()
        }

        val imageDataUrl = request.image?.contentUri?.let { contentUri ->
            contentUriToDataUrl(contentUri)
        }
        if (imageDataUrl != null) {
            inputs += buildJsonObject {
                put("type", "image")
                put("url", imageDataUrl)
            }
        }

        request.skill?.let { skill ->
            inputs += buildJsonObject {
                put("type", "skill")
                put("name", skill.name)
                put("path", skill.path)
            }
        }
        return inputs
    }

    private suspend fun contentUriToDataUrl(contentUri: String): String? = withContext(ioDispatcher) {
        val uri = Uri.parse(contentUri)
        val mimeType = appContext.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes()
        } ?: return@withContext null
        "data:$mimeType;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
    }

    private fun currentThreadModelId(threadId: String): String? = repositoryState.value.threadDetails[threadId]
        ?.summary
        ?.currentModelId
        ?: repositoryState.value.threads.firstOrNull { thread -> thread.id == threadId }?.currentModelId
        ?: repositoryState.value.threadSettingsCache[threadId]?.modelId

    private fun currentThreadReasoningEffort(threadId: String): ComposerReasoningEffort? = repositoryState.value.threadDetails[threadId]
        ?.summary
        ?.currentReasoningEffort
        ?: repositoryState.value.threads.firstOrNull { thread -> thread.id == threadId }?.currentReasoningEffort
        ?: repositoryState.value.threadSettingsCache[threadId]?.reasoningEffort?.toComposerReasoningEffort()

    private fun updateThreadSettings(
        threadId: String,
        settings: ThreadSessionSettings,
    ): Unit {
        repositoryState.update { current ->
            val persistedSettings = settings.toPersistedThreadSettings(catalog = current.composerCatalog)
            current.copy(
                threadSettingsCache = current.threadSettingsCache + (threadId to persistedSettings),
                threads = current.threads.map { thread ->
                    if (thread.id == threadId) {
                        thread.withThreadSettings(
                            settings = persistedSettings,
                            catalog = current.composerCatalog,
                        )
                    } else {
                        thread
                    }
                }.syncThreadOrdering(),
                threadDetails = current.threadDetails.mapValues { (id, detail) ->
                    if (id == threadId) {
                        detail.copy(
                            summary = detail.summary.withThreadSettings(
                                settings = persistedSettings,
                                catalog = current.composerCatalog,
                            ),
                        )
                    } else {
                        detail
                    }
                },
            )
        }
    }

    private suspend fun fetchThreadSummaries(
        currentSession: CodexAppServerSession,
        composerCatalog: ComposerCatalog,
        threadSettingsCache: Map<String, PersistedThreadSettings>,
        approvals: List<ApprovalItem>,
        userInputRequests: List<ThreadUserInputRequest>,
        activeTurnIds: Map<String, String>,
    ): List<ThreadSummary> = currentSession.threadList()
        .arrayAt("data")
        ?.map { it.jsonObject.toThreadSummary() }
        ?.map { thread ->
            thread.withThreadSettings(
                settings = threadSettingsCache[thread.id],
                catalog = composerCatalog,
            )
                .copy(
                    status = statusWithPendingRequests(
                        baseStatus = thread.status,
                        threadId = thread.id,
                        approvals = approvals,
                        userInputRequests = userInputRequests,
                        activeTurnId = activeTurnIds[thread.id],
                    ),
                )
        }
        .orEmpty()
        .syncThreadOrdering()
}

internal fun mergeThreadItems(
    existingItems: List<ThreadItem>,
    snapshotItems: List<ThreadItem>,
): List<ThreadItem> {
    if (existingItems.isEmpty()) return snapshotItems
    if (snapshotItems.isEmpty()) return existingItems
    if (existingItems.size > snapshotItems.size) return existingItems

    val snapshotById = snapshotItems.associateBy { it.id }
    val existingIds = existingItems.map { it.id }.toSet()

    return buildList {
        existingItems.forEach { item ->
            add(snapshotById[item.id] ?: item)
        }
        snapshotItems
            .filterNot { it.id in existingIds }
            .forEach(::add)
    }
}

internal fun shouldPreserveThreadCache(
    previousActiveHostId: String?,
    nextActiveHostId: String?,
    currentThreadItemCache: Map<String, List<ThreadItem>>,
): Boolean = when {
    nextActiveHostId == null -> false
    previousActiveHostId == nextActiveHostId -> true
    previousActiveHostId == null && currentThreadItemCache.isNotEmpty() -> true
    else -> false
}

private fun List<ThreadSummary>.syncThreadOrdering(): List<ThreadSummary> = distinctBy { it.id }
    .sortedByDescending { it.updatedAtEpochSeconds }

private fun statusWithPendingRequests(
    baseStatus: ThreadStatus,
    threadId: String,
    approvals: List<ApprovalItem>,
    userInputRequests: List<ThreadUserInputRequest>,
    activeTurnId: String?,
): ThreadStatus {
    val retainedFlags = baseStatus.activeFlags - setOf(
        "waitingOnApproval",
        "waitingOnUserInput",
    )
    val pendingFlags = buildSet {
        if (approvals.any { approval -> approval.threadId == threadId }) {
            add("waitingOnApproval")
        }
        if (userInputRequests.any { request -> request.threadId == threadId }) {
            add("waitingOnUserInput")
        }
    }

    return when {
        pendingFlags.isNotEmpty() -> ThreadStatus(
            type = ThreadStatusType.Active,
            activeFlags = retainedFlags + pendingFlags,
        )

        baseStatus.type == ThreadStatusType.Active &&
            retainedFlags.isEmpty() &&
            retainedFlags != baseStatus.activeFlags &&
            activeTurnId == null ->
            ThreadStatus(type = ThreadStatusType.Idle)

        baseStatus.type == ThreadStatusType.Active ->
            baseStatus.copy(activeFlags = retainedFlags)

        else -> baseStatus
    }
}

private fun Map<String, ThreadDetail>.syncWithThreads(
    threads: List<ThreadSummary>,
): Map<String, ThreadDetail> {
    val threadsById = threads.associateBy { it.id }
    return mapValues { (threadId, detail) ->
        val summary = threadsById[threadId] ?: detail.summary
        detail.copy(summary = summary)
    }
}

private fun inferHostKind(name: String): HostKind = if (
    name.contains("book", ignoreCase = true) ||
    name.contains("laptop", ignoreCase = true)
) {
    HostKind.Laptop
} else {
    HostKind.Desktop
}

private fun hostId(
    name: String,
    address: String,
    port: Int,
): String = "${name.lowercase().replace(" ", "-")}-$address-$port"

private const val MAX_THREAD_ACTIVITIES: Int = 40
private const val MAX_IN_APP_THREAD_NOTIFICATIONS: Int = 6

private fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1_000

private fun Throwable.toConnectionMessage(): String = when (this) {
    is UnknownHostException -> "Host not found."
    else -> message ?: "Unable to connect to app-server."
}

private fun statusLabel(status: ThreadStatus): String = when {
    status.isWaitingOnApproval -> "Waiting on approval"
    status.isWaitingOnUserInput -> "Waiting on input"
    status.type == ThreadStatusType.Active -> "Active"
    status.type == ThreadStatusType.SystemError -> "System error"
    status.type == ThreadStatusType.Idle -> "Idle"
    else -> "Not loaded"
}

private fun previewForItem(
    item: ThreadItem,
    fallback: String,
): String = when (item) {
    is ThreadItem.UserMessage -> item.text
    is ThreadItem.AgentMessage -> item.text
    is ThreadItem.Plan -> item.text
    is ThreadItem.Reasoning -> item.summary.ifBlank { fallback }
    is ThreadItem.CommandExecution -> item.command.ifBlank { fallback }
    is ThreadItem.FileChange -> item.changes.firstOrNull()?.path ?: fallback
    is ThreadItem.McpToolCall -> "${item.server}/${item.tool}".ifBlank { fallback }
    is ThreadItem.DynamicToolCall -> item.tool.ifBlank { fallback }
    is ThreadItem.CollabToolCall -> item.tool.ifBlank { fallback }
    is ThreadItem.WebSearch -> item.query.ifBlank { fallback }
    is ThreadItem.ImageView -> item.path.ifBlank { fallback }
    is ThreadItem.ImageGeneration -> item.result.ifBlank { fallback }
    is ThreadItem.ReviewMode -> item.review.ifBlank { fallback }
    is ThreadItem.ContextCompaction -> "Conversation compacted"
    is ThreadItem.Unknown -> item.typeName.ifBlank { fallback }
}

private fun ThreadSummary.withThreadSettings(
    settings: PersistedThreadSettings?,
    catalog: ComposerCatalog,
): ThreadSummary = if (settings == null) {
    this
} else {
    copy(
        currentModelId = settings.modelId ?: currentModelId,
        currentModelName = settings.modelName
            ?: settings.modelId?.let { modelId ->
                catalog.models.firstOrNull { model -> model.id == modelId }?.displayName
            }
            ?: currentModelName,
        currentReasoningEffort = settings.reasoningEffort?.toComposerReasoningEffort()
            ?: currentReasoningEffort,
    )
}

private fun ThreadSessionSettings.toPersistedThreadSettings(
    catalog: ComposerCatalog,
): PersistedThreadSettings = PersistedThreadSettings(
    modelId = modelId,
    modelName = modelId?.let { resolvedModelId ->
        catalog.models.firstOrNull { model -> model.id == resolvedModelId }?.displayName
    } ?: modelId,
    reasoningEffort = reasoningEffort?.toWireValue(),
)

private fun ThreadReplyRequest.toWireMessageText(): String {
    val trimmedMessage = message.trim()
    val skillPrefix = skill?.let { "$${it.name}" }
    return listOfNotNull(skillPrefix, trimmedMessage.takeIf { it.isNotBlank() })
        .joinToString(separator = " ")
        .trim()
}

private fun ComposerReasoningEffort?.toWireValue(): String? = when (this) {
    ComposerReasoningEffort.None -> "none"
    ComposerReasoningEffort.Minimal -> "minimal"
    ComposerReasoningEffort.Low -> "low"
    ComposerReasoningEffort.Medium -> "medium"
    ComposerReasoningEffort.High -> "high"
    ComposerReasoningEffort.XHigh -> "xhigh"
    null -> null
}

private fun String?.toComposerReasoningEffort(): ComposerReasoningEffort? = when (this) {
    "none" -> ComposerReasoningEffort.None
    "minimal" -> ComposerReasoningEffort.Minimal
    "low" -> ComposerReasoningEffort.Low
    "medium" -> ComposerReasoningEffort.Medium
    "high" -> ComposerReasoningEffort.High
    "xhigh" -> ComposerReasoningEffort.XHigh
    else -> null
}

private fun ComposerPersonality.toWireValue(): String? = when (this) {
    ComposerPersonality.Default -> null
    ComposerPersonality.Friendly -> "friendly"
    ComposerPersonality.Pragmatic -> "pragmatic"
}

private fun ComposerSandboxMode.toSandboxPolicyPayload(): kotlinx.serialization.json.JsonObject? = when (this) {
    ComposerSandboxMode.Default -> null
    ComposerSandboxMode.ReadOnly -> buildJsonObject {
        put("type", "readOnly")
    }

    ComposerSandboxMode.WorkspaceWrite -> buildJsonObject {
        put("type", "workspaceWrite")
    }

    ComposerSandboxMode.FullAccess -> buildJsonObject {
        put("type", "dangerFullAccess")
    }
}

private fun kotlinx.serialization.json.JsonObject.contextRemainingPercent(): Int? {
    val modelContextWindow = long("modelContextWindow") ?: return null
    if (modelContextWindow <= 0L) return null
    val totalTokens = objectAt("total")?.long("totalTokens") ?: return null
    val usedRatio = totalTokens.toDouble() / modelContextWindow.toDouble()
    val remainingPercent = ((1.0 - usedRatio) * 100.0).toInt()
    return remainingPercent.coerceIn(0, 100)
}
