package dev.codex.mobile.core.data.appserver

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal class CodexAppServerSession(
    private val transport: CodexJsonRpcTransport,
    private val versionName: String,
) {
    val events: Flow<TransportEvent> = transport.events

    suspend fun connect(): Unit {
        transport.connect()
        transport.request(
            method = "initialize",
            params = initializeParamsPayload(versionName = versionName),
        )
        transport.notify(
            method = "initialized",
            params = emptyJsonObject,
        )
    }

    suspend fun accountRead(): JsonObject = transport.request(
        method = "account/read",
        params = accountReadParamsPayload(),
    ).jsonObject

    suspend fun accountRateLimitsRead(): JsonObject = transport.request(
        method = "account/rateLimits/read",
        params = accountRateLimitsReadParamsPayload(),
    ).jsonObject

    suspend fun modelList(): JsonObject = transport.request(
        method = "model/list",
        params = modelListParamsPayload(),
    ).jsonObject

    suspend fun skillsList(forceReload: Boolean = false): JsonObject = transport.request(
        method = "skills/list",
        params = skillsListParamsPayload(forceReload = forceReload),
    ).jsonObject

    suspend fun threadList(): JsonObject = transport.request(
        method = "thread/list",
        params = threadListParamsPayload(),
    ).jsonObject

    suspend fun threadRead(
        threadId: String,
        includeTurns: Boolean = true,
    ): JsonObject = transport.request(
        method = "thread/read",
        params = threadReadParamsPayload(
            threadId = threadId,
            includeTurns = includeTurns,
        ),
    ).jsonObject

    suspend fun threadStart(cwd: String? = null): JsonObject = transport.request(
        method = "thread/start",
        params = threadStartParamsPayload(cwd = cwd),
    ).jsonObject

    suspend fun threadResume(threadId: String): JsonObject = transport.request(
        method = "thread/resume",
        params = threadResumeParamsPayload(threadId = threadId),
    ).jsonObject

    suspend fun turnStart(
        threadId: String,
        input: List<JsonObject>,
        approvalPolicy: JsonElement? = null,
        model: String? = null,
        effort: String? = null,
        personality: String? = null,
        sandboxPolicy: JsonObject? = null,
    ): JsonObject = transport.request(
        method = "turn/start",
        params = turnStartParamsPayload(
            threadId = threadId,
            input = input,
            approvalPolicy = approvalPolicy,
            model = model,
            effort = effort,
            personality = personality,
            sandboxPolicy = sandboxPolicy,
        ),
    ).jsonObject

    suspend fun turnSteer(
        threadId: String,
        expectedTurnId: String,
        input: List<JsonObject>,
    ): JsonObject = transport.request(
        method = "turn/steer",
        params = turnSteerParamsPayload(
            threadId = threadId,
            expectedTurnId = expectedTurnId,
            input = input,
        ),
    ).jsonObject

    suspend fun turnInterrupt(
        threadId: String,
        turnId: String,
    ): Unit {
        transport.request(
            method = "turn/interrupt",
            params = turnInterruptParamsPayload(
                threadId = threadId,
                turnId = turnId,
            ),
        )
    }

    fun respondToRequest(
        requestId: JsonPrimitive,
        result: JsonElement,
    ): Unit {
        transport.respond(
            requestId = requestId,
            result = result,
        )
    }

    fun close(): Unit {
        transport.close()
    }
}
