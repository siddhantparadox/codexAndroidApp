package dev.codex.mobile.core.data.appserver

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import dev.codex.mobile.core.util.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal sealed interface TransportEvent {
    data class Notification(
        val method: String,
        val params: JsonObject,
    ) : TransportEvent

    data class ServerRequest(
        val requestId: JsonPrimitive,
        val method: String,
        val params: JsonObject,
    ) : TransportEvent

    data class Closed(
        val message: String?,
        val isError: Boolean,
    ) : TransportEvent
}

internal class CodexJsonRpcTransport(
    private val okHttpClient: OkHttpClient,
    private val url: String,
) {
    private val eventChannel: Channel<TransportEvent> = Channel(capacity = Channel.BUFFERED)
    private val pendingResponses: ConcurrentHashMap<String, CompletableDeferred<JsonElement>> = ConcurrentHashMap()
    private val nextRequestId: AtomicLong = AtomicLong(1L)

    private var webSocket: WebSocket? = null
    private var closedByClient: Boolean = false

    val events: Flow<TransportEvent> = eventChannel.receiveAsFlow()

    suspend fun connect(): Unit = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder()
            .url(url)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response): Unit {
                this@CodexJsonRpcTransport.webSocket = webSocket
                AppLog.action(
                    name = "transport_open",
                    detail = url,
                )
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String): Unit {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String): Unit {
                AppLog.action(
                    name = "transport_closing",
                    detail = "url=$url code=$code reason=${reason.ifBlank { "n/a" }}",
                )
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String): Unit {
                AppLog.action(
                    name = "transport_closed_socket",
                    detail = "url=$url code=$code reason=${reason.ifBlank { "n/a" }} clientClosed=$closedByClient",
                )
                closePendingResponses(IOException(reason.ifBlank { "WebSocket closed" }))
                eventChannel.trySend(
                    TransportEvent.Closed(
                        message = reason.takeIf { it.isNotBlank() },
                        isError = !closedByClient,
                    ),
                )
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?): Unit {
                AppLog.action(
                    name = "transport_failure",
                    detail = "url=$url message=${t.message.orEmpty()} code=${response?.code ?: -1}",
                )
                closePendingResponses(t)
                if (continuation.isActive) {
                    continuation.resumeWithException(t)
                } else {
                    eventChannel.trySend(
                        TransportEvent.Closed(
                            message = t.message,
                            isError = !closedByClient,
                        ),
                    )
                }
            }
        }

        val newSocket = okHttpClient.newWebSocket(request, listener)
        continuation.invokeOnCancellation {
            closedByClient = true
            newSocket.cancel()
        }
    }

    suspend fun request(
        method: String,
        params: JsonElement = emptyJsonObject,
    ): JsonElement {
        val requestId = JsonPrimitive(nextRequestId.getAndIncrement())
        val key = requestId.content
        val deferred = CompletableDeferred<JsonElement>()
        pendingResponses[key] = deferred

        try {
            sendPayload(requestPayload(method = method, id = requestId, params = params))
        } catch (error: Throwable) {
            pendingResponses.remove(key)
            throw error
        }

        return deferred.await()
    }

    fun notify(
        method: String,
        params: JsonElement = emptyJsonObject,
    ): Unit {
        sendPayload(requestPayload(method = method, params = params))
    }

    fun respond(
        requestId: JsonPrimitive,
        result: JsonElement,
    ): Unit {
        sendPayload(responsePayload(id = requestId, result = result))
    }

    fun close(): Unit {
        closedByClient = true
        webSocket?.close(1_000, "Client closing")
        webSocket = null
    }

    private fun handleIncomingMessage(text: String): Unit {
        val message = appServerJson.parseToJsonElement(text).jsonObject
        val method = message.string("method")
        val responseId = message["id"]?.jsonPrimitive

        when {
            method != null && responseId != null -> {
                eventChannel.trySend(
                    TransportEvent.ServerRequest(
                        requestId = responseId,
                        method = method,
                        params = message["params"]?.jsonObject ?: emptyJsonObject,
                    ),
                )
            }

            method != null -> {
                eventChannel.trySend(
                    TransportEvent.Notification(
                        method = method,
                        params = message["params"]?.jsonObject ?: emptyJsonObject,
                    ),
                )
            }

            responseId != null -> {
                val key = responseId.content
                val deferred = pendingResponses.remove(key) ?: return
                val errorMessage = message["error"]?.jsonObject?.string("message")
                if (errorMessage != null) {
                    deferred.completeExceptionally(IOException(errorMessage))
                } else {
                    deferred.complete(message["result"] ?: emptyJsonObject)
                }
            }
        }
    }

    private fun sendPayload(payload: JsonObject): Unit {
        val serialized = appServerJson.encodeToString(JsonObject.serializer(), payload)
        val didSend = webSocket?.send(serialized) ?: false
        check(didSend) { "Failed to send WebSocket payload." }
    }

    private fun closePendingResponses(cause: Throwable): Unit {
        val iterator = pendingResponses.values.iterator()
        while (iterator.hasNext()) {
            iterator.next().completeExceptionally(cause)
        }
        pendingResponses.clear()
    }
}
