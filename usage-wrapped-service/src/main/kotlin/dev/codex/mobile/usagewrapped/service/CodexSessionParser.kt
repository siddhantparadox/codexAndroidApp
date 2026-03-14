package dev.codex.mobile.usagewrapped.service

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal data class CodexSessionSnapshot(
    val startedAt: Instant,
    val cwd: String? = null,
    val source: String? = null,
    val tokenTotals: UsageTokenTotals = UsageTokenTotals(),
    val tokenTotalsByModel: Map<String, UsageTokenTotals> = emptyMap(),
)

internal class CodexSessionParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(path: Path): CodexSessionSnapshot? {
        var startedAt: Instant? = null
        var cwd: String? = null
        var source: String? = null
        var tokenTotals: UsageTokenTotals = UsageTokenTotals()
        var currentModel: String? = null
        val tokenTotalsByModel: MutableMap<String, UsageTokenTotals> = linkedMapOf()

        Files.newBufferedReader(path).useLines { lines ->
            lines.forEach { line ->
                val payload: JsonObject = runCatching {
                    json.parseToJsonElement(line).jsonObject
                }.getOrNull() ?: return@forEach

                when (payload.string("type")) {
                    "session_meta" -> {
                        val meta: JsonObject = payload.objectAt("payload") ?: return@forEach
                        startedAt = meta.string("timestamp")?.let(Instant::parse) ?: startedAt
                        cwd = meta.string("cwd") ?: cwd
                        source = meta.string("source") ?: source
                    }

                    "turn_context" -> {
                        val context: JsonObject = payload.objectAt("payload") ?: return@forEach
                        currentModel = context.string("model") ?: currentModel
                    }

                    "event_msg" -> {
                        val event: JsonObject = payload.objectAt("payload") ?: return@forEach
                        if (event.string("type") != "token_count") return@forEach
                        val totals: JsonObject = event
                            .objectAt("info")
                            ?.objectAt("total_token_usage")
                            ?: return@forEach
                        val updatedTotals = UsageTokenTotals(
                            input = totals.long("input_tokens") ?: 0L,
                            cachedInput = totals.long("cached_input_tokens") ?: 0L,
                            output = totals.long("output_tokens") ?: 0L,
                            reasoning = totals.long("reasoning_output_tokens") ?: 0L,
                            total = totals.long("total_tokens") ?: 0L,
                        )
                        val delta: UsageTokenTotals = updatedTotals.deltaFrom(tokenTotals)
                        tokenTotals = updatedTotals
                        if (delta.isNotEmpty()) {
                            val modelId: String = currentModel?.takeIf(String::isNotBlank) ?: UnknownModelId
                            tokenTotalsByModel[modelId] = (
                                tokenTotalsByModel[modelId] ?: UsageTokenTotals()
                                ).plus(delta)
                        }
                    }
                }
            }
        }

        return startedAt?.let { sessionStartedAt ->
            CodexSessionSnapshot(
                startedAt = sessionStartedAt,
                cwd = cwd,
                source = source,
                tokenTotals = tokenTotals,
                tokenTotalsByModel = tokenTotalsByModel.toMap(),
            )
        }
    }
}

private const val UnknownModelId: String = "_unknown"

private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun UsageTokenTotals.deltaFrom(previous: UsageTokenTotals): UsageTokenTotals = UsageTokenTotals(
    input = (input - previous.input).coerceAtLeast(0L),
    cachedInput = (cachedInput - previous.cachedInput).coerceAtLeast(0L),
    output = (output - previous.output).coerceAtLeast(0L),
    reasoning = (reasoning - previous.reasoning).coerceAtLeast(0L),
    total = (total - previous.total).coerceAtLeast(0L),
)

private fun UsageTokenTotals.plus(other: UsageTokenTotals): UsageTokenTotals = UsageTokenTotals(
    input = input + other.input,
    cachedInput = cachedInput + other.cachedInput,
    output = output + other.output,
    reasoning = reasoning + other.reasoning,
    total = total + other.total,
)

private fun UsageTokenTotals.isNotEmpty(): Boolean = total > 0L ||
    input > 0L ||
    cachedInput > 0L ||
    output > 0L ||
    reasoning > 0L
