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
                        val info: JsonObject = event.objectAt("info") ?: return@forEach
                        val totals: JsonObject = info.objectAt("total_token_usage") ?: return@forEach
                        val updatedTotals: UsageTokenTotals = totals.toUsageTokenTotals()
                        val lastTotals: UsageTokenTotals =
                            info.objectAt("last_token_usage")?.toUsageTokenTotals() ?: UsageTokenTotals()
                        val modelId: String = pricingBucket(currentModel = currentModel, lastTotals = lastTotals)
                        val correction: UsageTokenTotals = tokenTotals.regressionFrom(updatedTotals)
                        if (modelId != UnknownModelId && correction.isNotEmpty()) {
                            tokenTotalsByModel[modelId] = (
                                tokenTotalsByModel[modelId] ?: UsageTokenTotals()
                                ).minus(correction)
                        }
                        val delta: UsageTokenTotals = updatedTotals.deltaFrom(tokenTotals)
                        tokenTotals = updatedTotals
                        if (modelId != UnknownModelId && delta.isNotEmpty()) {
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
private const val Gpt54ModelId: String = "gpt-5.4"
private const val Gpt54LongContextModelId: String = "gpt-5.4-long-context"
private const val LongContextInputThreshold: Long = 272_000L

private fun JsonObject.objectAt(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private fun JsonObject.toUsageTokenTotals(): UsageTokenTotals = UsageTokenTotals(
    input = long("input_tokens") ?: 0L,
    cachedInput = long("cached_input_tokens") ?: 0L,
    output = long("output_tokens") ?: 0L,
    reasoning = long("reasoning_output_tokens") ?: 0L,
    total = long("total_tokens") ?: 0L,
)

private fun UsageTokenTotals.deltaFrom(previous: UsageTokenTotals): UsageTokenTotals = UsageTokenTotals(
    input = (input - previous.input).coerceAtLeast(0L),
    cachedInput = (cachedInput - previous.cachedInput).coerceAtLeast(0L),
    output = (output - previous.output).coerceAtLeast(0L),
    reasoning = (reasoning - previous.reasoning).coerceAtLeast(0L),
    total = (total - previous.total).coerceAtLeast(0L),
)

private fun UsageTokenTotals.regressionFrom(current: UsageTokenTotals): UsageTokenTotals = UsageTokenTotals(
    input = (input - current.input).coerceAtLeast(0L),
    cachedInput = (cachedInput - current.cachedInput).coerceAtLeast(0L),
    output = (output - current.output).coerceAtLeast(0L),
    reasoning = (reasoning - current.reasoning).coerceAtLeast(0L),
    total = (total - current.total).coerceAtLeast(0L),
)

private fun UsageTokenTotals.plus(other: UsageTokenTotals): UsageTokenTotals = UsageTokenTotals(
    input = input + other.input,
    cachedInput = cachedInput + other.cachedInput,
    output = output + other.output,
    reasoning = reasoning + other.reasoning,
    total = total + other.total,
)

private fun UsageTokenTotals.minus(other: UsageTokenTotals): UsageTokenTotals = UsageTokenTotals(
    input = (input - other.input).coerceAtLeast(0L),
    cachedInput = (cachedInput - other.cachedInput).coerceAtLeast(0L),
    output = (output - other.output).coerceAtLeast(0L),
    reasoning = (reasoning - other.reasoning).coerceAtLeast(0L),
    total = (total - other.total).coerceAtLeast(0L),
)

private fun UsageTokenTotals.isNotEmpty(): Boolean = total > 0L ||
    input > 0L ||
    cachedInput > 0L ||
    output > 0L ||
    reasoning > 0L

private fun pricingBucket(
    currentModel: String?,
    lastTotals: UsageTokenTotals,
): String {
    val normalizedModelId: String = currentModel?.trim()?.lowercase().orEmpty()
    if (normalizedModelId.isBlank()) return UnknownModelId
    if (normalizedModelId == Gpt54ModelId && lastTotals.input > LongContextInputThreshold) {
        return Gpt54LongContextModelId
    }
    return normalizedModelId
}
