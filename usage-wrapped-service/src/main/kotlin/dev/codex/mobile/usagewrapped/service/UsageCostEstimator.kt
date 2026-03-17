package dev.codex.mobile.usagewrapped.service

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

private const val TokensPerMillion: Double = 1_000_000.0

internal class UsageCostEstimator {
    private val pricingCatalog: List<ModelPricing> = defaultPricingCatalog()

    fun estimate(tokenTotalsByModel: Map<String, UsageTokenTotals>): UsageWrappedCostEstimate? {
        if (tokenTotalsByModel.isEmpty()) return null

        val recordedTokens: Long = tokenTotalsByModel.values.sumOf(UsageTokenTotals::total)
        if (recordedTokens <= 0L) return null

        var coveredTokens = 0L
        var approximateUsd = 0.0
        val notes: MutableList<String> = mutableListOf()

        tokenTotalsByModel.forEach { (modelId, totals) ->
            val resolvedPricing: ResolvedPricing = resolvePricing(modelId) ?: return@forEach
            coveredTokens += totals.total
            approximateUsd += resolvedPricing.pricing.priceUsd(totals)
            if (resolvedPricing.usedAlias) {
                notes.addUnique("$modelId mapped to ${resolvedPricing.pricing.modelId} public API pricing.")
            }
        }

        if (approximateUsd <= 0.0) return null

        val coveragePercent: Int = ((coveredTokens.toDouble() / recordedTokens.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
        if (coveragePercent < 100) {
            notes.addUnique("Public API pricing covered $coveragePercent% of recorded tokens.")
        }
        notes.addUnique("Estimated using public standard API token pricing from recorded session token totals.")
        notes.addUnique("GPT-5.4 long-context rates are applied when turn-level input usage exceeds 272K tokens.")
        notes.addUnique("Cached input tokens are billed at cached-input rates when recognized.")
        notes.addUnique("Service-tier modifiers and built-in tool charges are not included.")

        return UsageWrappedCostEstimate(
            approximateUsd = approximateUsd.roundCurrency(),
            coveragePercent = coveragePercent,
            note = notes.joinToString(separator = " "),
        )
    }

    private fun resolvePricing(modelId: String): ResolvedPricing? {
        val normalizedModelId: String = modelId.lowercase()
        val pricing: ModelPricing = pricingCatalog.firstOrNull { entry ->
            entry.matches(modelId = normalizedModelId)
        } ?: return null
        return ResolvedPricing(
            pricing = pricing,
            usedAlias = normalizedModelId != pricing.modelId,
        )
    }
}

private data class ResolvedPricing(
    val pricing: ModelPricing,
    val usedAlias: Boolean,
)

private data class ModelPricing(
    val modelId: String,
    val inputUsdPerMillion: Double,
    val cachedInputUsdPerMillion: Double,
    val outputUsdPerMillion: Double,
    val aliases: Set<String> = emptySet(),
) {
    fun matches(modelId: String): Boolean = modelId == this.modelId || modelId in aliases

    fun priceUsd(totals: UsageTokenTotals): Double {
        val freshInputTokens: Long = (totals.input - totals.cachedInput).coerceAtLeast(0L)
        val billedOutputTokens: Long = totals.output
        return (freshInputTokens.toDouble() / TokensPerMillion) * inputUsdPerMillion +
            (totals.cachedInput.toDouble() / TokensPerMillion) * cachedInputUsdPerMillion +
            (billedOutputTokens.toDouble() / TokensPerMillion) * outputUsdPerMillion
    }
}

private fun defaultPricingCatalog(): List<ModelPricing> = listOf(
    ModelPricing(
        modelId = "gpt-5.4",
        inputUsdPerMillion = 2.50,
        cachedInputUsdPerMillion = 0.25,
        outputUsdPerMillion = 15.00,
    ),
    ModelPricing(
        modelId = "gpt-5.4-long-context",
        inputUsdPerMillion = 5.00,
        cachedInputUsdPerMillion = 0.50,
        outputUsdPerMillion = 22.50,
    ),
    ModelPricing(
        modelId = "gpt-5.3-codex",
        inputUsdPerMillion = 1.75,
        cachedInputUsdPerMillion = 0.175,
        outputUsdPerMillion = 14.00,
        aliases = setOf("gpt-5.3-codex-spark"),
    ),
    ModelPricing(
        modelId = "gpt-5.2-codex",
        inputUsdPerMillion = 1.75,
        cachedInputUsdPerMillion = 0.175,
        outputUsdPerMillion = 14.00,
        aliases = setOf("gpt-5.2"),
    ),
    ModelPricing(
        modelId = "gpt-5.1-codex",
        inputUsdPerMillion = 1.25,
        cachedInputUsdPerMillion = 0.125,
        outputUsdPerMillion = 10.00,
        aliases = setOf("gpt-5.1", "gpt-5.1-codex-max"),
    ),
    ModelPricing(
        modelId = "gpt-5.1-codex-mini",
        inputUsdPerMillion = 0.25,
        cachedInputUsdPerMillion = 0.025,
        outputUsdPerMillion = 2.00,
    ),
    ModelPricing(
        modelId = "gpt-5-codex",
        inputUsdPerMillion = 1.25,
        cachedInputUsdPerMillion = 0.125,
        outputUsdPerMillion = 10.00,
        aliases = setOf("gpt-5", "gpt-5-codex-mini"),
    ),
)

private fun MutableList<String>.addUnique(value: String): Unit {
    if (value !in this) {
        add(value)
    }
}

private fun Double.roundCurrency(): Double = BigDecimal.valueOf(this)
    .setScale(2, RoundingMode.HALF_UP)
    .toDouble()
