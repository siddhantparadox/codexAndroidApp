const TOKENS_PER_MILLION = 1_000_000;

const PRICING_CATALOG = [
  {
    modelId: "gpt-5.4",
    inputUsdPerMillion: 2.5,
    cachedInputUsdPerMillion: 0.25,
    outputUsdPerMillion: 15,
    aliases: new Set(),
  },
  {
    modelId: "gpt-5.4-long-context",
    inputUsdPerMillion: 5,
    cachedInputUsdPerMillion: 0.5,
    outputUsdPerMillion: 22.5,
    aliases: new Set(),
  },
  {
    modelId: "gpt-5.3-codex",
    inputUsdPerMillion: 1.75,
    cachedInputUsdPerMillion: 0.175,
    outputUsdPerMillion: 14,
    aliases: new Set(["gpt-5.3-codex-spark"]),
  },
  {
    modelId: "gpt-5.2-codex",
    inputUsdPerMillion: 1.75,
    cachedInputUsdPerMillion: 0.175,
    outputUsdPerMillion: 14,
    aliases: new Set(["gpt-5.2"]),
  },
  {
    modelId: "gpt-5.1-codex",
    inputUsdPerMillion: 1.25,
    cachedInputUsdPerMillion: 0.125,
    outputUsdPerMillion: 10,
    aliases: new Set(["gpt-5.1", "gpt-5.1-codex-max"]),
  },
  {
    modelId: "gpt-5.1-codex-mini",
    inputUsdPerMillion: 0.25,
    cachedInputUsdPerMillion: 0.025,
    outputUsdPerMillion: 2,
    aliases: new Set(),
  },
  {
    modelId: "gpt-5-codex",
    inputUsdPerMillion: 1.25,
    cachedInputUsdPerMillion: 0.125,
    outputUsdPerMillion: 10,
    aliases: new Set(["gpt-5", "gpt-5-codex-mini"]),
  },
];

export function estimateUsageWrappedCost(tokenTotalsByModel) {
  const entries = Object.entries(tokenTotalsByModel);
  if (entries.length === 0) {
    return null;
  }

  const recordedTokens = entries.reduce((sum, [, totals]) => sum + (totals.total || 0), 0);
  if (recordedTokens <= 0) {
    return null;
  }

  let coveredTokens = 0;
  let approximateUsd = 0;
  const notes = [];

  for (const [modelId, totals] of entries) {
    const resolvedPricing = resolvePricing(modelId);
    if (!resolvedPricing) continue;
    coveredTokens += totals.total || 0;
    approximateUsd += priceUsd(resolvedPricing.pricing, totals);
    if (resolvedPricing.usedAlias) {
      addUnique(notes, `${modelId} mapped to ${resolvedPricing.pricing.modelId} public API pricing.`);
    }
  }

  if (approximateUsd <= 0) {
    return null;
  }

  const coveragePercent = clamp(Math.round((coveredTokens / recordedTokens) * 100), 0, 100);
  if (coveragePercent < 100) {
    addUnique(notes, `Public API pricing covered ${coveragePercent}% of recorded tokens.`);
  }
  addUnique(notes, "Estimated using public standard API token pricing from recorded session token totals.");
  addUnique(notes, "GPT-5.4 long-context rates are applied when turn-level input usage exceeds 272K tokens.");
  addUnique(notes, "Cached input tokens are billed at cached-input rates when recognized.");
  addUnique(notes, "Service-tier modifiers and built-in tool charges are not included.");

  return {
    approximateUsd: roundCurrency(approximateUsd),
    currencyCode: "USD",
    basis: "api_equivalent",
    coveragePercent,
    note: notes.join(" "),
  };
}

function resolvePricing(modelId) {
  const normalizedModelId = String(modelId || "").toLowerCase();
  const pricing = PRICING_CATALOG.find((entry) => (
    entry.modelId === normalizedModelId || entry.aliases.has(normalizedModelId)
  ));
  if (!pricing) {
    return null;
  }
  return {
    pricing,
    usedAlias: normalizedModelId !== pricing.modelId,
  };
}

function priceUsd(pricing, totals) {
  const freshInputTokens = Math.max((totals.input || 0) - (totals.cachedInput || 0), 0);
  const billedOutputTokens = totals.output || 0;
  return (
    (freshInputTokens / TOKENS_PER_MILLION) * pricing.inputUsdPerMillion +
    ((totals.cachedInput || 0) / TOKENS_PER_MILLION) * pricing.cachedInputUsdPerMillion +
    (billedOutputTokens / TOKENS_PER_MILLION) * pricing.outputUsdPerMillion
  );
}

function roundCurrency(value) {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function addUnique(values, value) {
  if (!values.includes(value)) {
    values.push(value);
  }
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value));
}
