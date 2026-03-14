package dev.codex.mobile.core.model

data class AccountRateLimitWindow(
    val usedPercent: Int? = null,
    val windowDurationMins: Int? = null,
    val resetsAtEpochSeconds: Long? = null,
)

data class AccountRateLimit(
    val limitId: String,
    val limitName: String? = null,
    val primary: AccountRateLimitWindow? = null,
    val secondary: AccountRateLimitWindow? = null,
)

data class AccountRateLimits(
    val current: AccountRateLimit? = null,
    val byLimitId: Map<String, AccountRateLimit> = emptyMap(),
)

fun AccountRateLimits.preferredBucket(): AccountRateLimit? = current
    ?: byLimitId["codex"]
    ?: byLimitId.values.firstOrNull()

fun AccountRateLimits.mergeUpdatedBucket(
    update: AccountRateLimit,
): AccountRateLimits {
    val existing: AccountRateLimit? = byLimitId[update.limitId]
    val merged: AccountRateLimit = AccountRateLimit(
        limitId = update.limitId,
        limitName = update.limitName ?: existing?.limitName,
        primary = update.primary ?: existing?.primary,
        secondary = update.secondary ?: existing?.secondary,
    )
    val updatedBuckets: Map<String, AccountRateLimit> = byLimitId + (merged.limitId to merged)
    val currentBucket: AccountRateLimit? = when {
        current?.limitId == merged.limitId -> merged
        current == null -> merged
        else -> current
    }
    return copy(
        current = currentBucket,
        byLimitId = updatedBuckets,
    )
}
