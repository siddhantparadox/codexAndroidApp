package dev.codex.mobile.feature.threads

import dev.codex.mobile.core.model.ThreadSummary

public data class ThreadCwdOption(
    public val path: String,
    public val lastUsedAtEpochSeconds: Long,
)

internal fun buildThreadCwdOptions(
    threads: List<ThreadSummary>,
): List<ThreadCwdOption> = threads
    .asSequence()
    .mapNotNull { thread ->
        val normalizedPath = thread.cwd.trim()
        normalizedPath.takeIf(String::isNotEmpty)?.let { path ->
            ThreadCwdOption(
                path = path,
                lastUsedAtEpochSeconds = thread.updatedAtEpochSeconds,
            )
        }
    }
    .groupBy(ThreadCwdOption::path)
    .map { (path, options) ->
        ThreadCwdOption(
            path = path,
            lastUsedAtEpochSeconds = options.maxOf(ThreadCwdOption::lastUsedAtEpochSeconds),
        )
    }
    .sortedWith(
        compareByDescending<ThreadCwdOption> { option -> option.lastUsedAtEpochSeconds }
            .thenBy { option -> option.path.lowercase() },
    )

internal fun filterThreadCwdOptions(
    options: List<ThreadCwdOption>,
    query: String,
): List<ThreadCwdOption> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) return options
    return options.filter { option ->
        option.path.contains(normalizedQuery, ignoreCase = true)
    }
}
