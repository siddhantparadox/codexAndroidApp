package dev.codex.mobile.core.util

fun relativeTimeLabel(
    epochSeconds: Long,
    nowEpochSeconds: Long = System.currentTimeMillis() / 1_000,
): String {
    val delta = (nowEpochSeconds - epochSeconds).coerceAtLeast(0L)
    return when {
        delta < 60L -> "Just now"
        delta < 60L * 60L -> "${delta / 60L}m ago"
        delta < 60L * 60L * 24L -> "${delta / (60L * 60L)}h ago"
        else -> "${delta / (60L * 60L * 24L)}d ago"
    }
}
