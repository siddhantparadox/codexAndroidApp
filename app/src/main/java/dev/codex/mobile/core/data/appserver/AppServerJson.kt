package dev.codex.mobile.core.data.appserver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val appServerJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private val appServerPrettyJson: Json = Json(appServerJson) {
    prettyPrint = true
    prettyPrintIndent = "  "
}

internal val emptyJsonObject: JsonObject = buildJsonObject {}

internal fun JsonObject.string(name: String): String? = this[name]
    ?.takeUnless { it is JsonNull }
    ?.jsonPrimitive
    ?.content

internal fun JsonObject.boolean(name: String): Boolean? = this[name]
    ?.takeUnless { it is JsonNull }
    ?.jsonPrimitive
    ?.booleanOrNull

internal fun JsonObject.long(name: String): Long? = this[name]
    ?.takeUnless { it is JsonNull }
    ?.jsonPrimitive
    ?.longOrNull

internal fun JsonObject.int(name: String): Int? = this[name]
    ?.takeUnless { it is JsonNull }
    ?.jsonPrimitive
    ?.intOrNull

internal fun JsonObject.objectAt(name: String): JsonObject? = this[name]?.jsonObject

internal fun JsonObject.arrayAt(name: String): JsonArray? = this[name]?.jsonArray

internal fun JsonObject.elementAt(name: String): JsonElement? = this[name]

internal fun JsonElement.toDisplayJson(): String = appServerPrettyJson.encodeToString(
    serializer = JsonElement.serializer(),
    value = this,
)

internal fun String.toTextInput(): JsonObject = buildJsonObject {
    put("type", "text")
    put("text", this@toTextInput)
}

internal fun requestPayload(
    method: String,
    id: JsonPrimitive? = null,
    params: JsonElement = emptyJsonObject,
): JsonObject = buildJsonObject {
    put("method", method)
    id?.let { put("id", it) }
    put("params", params)
}

internal fun responsePayload(
    id: JsonPrimitive,
    result: JsonElement = JsonNull,
): JsonObject = buildJsonObject {
    put("id", id)
    put("result", result)
}

internal fun initializeParamsPayload(
    versionName: String,
): JsonObject = buildJsonObject {
    putJsonObject("clientInfo") {
        put("name", "codex_mobile_android")
        put("title", "Codex Mobile Android")
        put("version", versionName)
    }
    putJsonObject("capabilities") {
        put("experimentalApi", false)
    }
}

internal fun threadListParamsPayload(): JsonObject = buildJsonObject {
    put("limit", 100)
    put("sortKey", "updated_at")
}

internal fun threadStartParamsPayload(): JsonObject = emptyJsonObject

internal fun threadResumeParamsPayload(threadId: String): JsonObject = buildJsonObject {
    put("threadId", threadId)
}

internal fun threadReadParamsPayload(
    threadId: String,
    includeTurns: Boolean,
): JsonObject = buildJsonObject {
    put("threadId", threadId)
    put("includeTurns", includeTurns)
}

internal fun accountReadParamsPayload(): JsonObject = emptyJsonObject

internal fun turnStartParamsPayload(
    threadId: String,
    message: String,
): JsonObject = buildJsonObject {
    put("threadId", threadId)
    putJsonArray("input") {
        add(message.toTextInput())
    }
}

internal fun turnSteerParamsPayload(
    threadId: String,
    expectedTurnId: String,
    message: String,
): JsonObject = buildJsonObject {
    put("threadId", threadId)
    put("expectedTurnId", expectedTurnId)
    putJsonArray("input") {
        add(message.toTextInput())
    }
}

internal fun turnInterruptParamsPayload(
    threadId: String,
    turnId: String,
): JsonObject = buildJsonObject {
    put("threadId", threadId)
    put("turnId", turnId)
}
