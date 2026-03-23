package dev.codex.mobile.feature.threads

import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadSummary

internal const val InitialVisibleThreadsPerFolder: Int = 4

private const val UnknownFolderKey: String = "__unknown_folder__"
private const val UnknownFolderLabel: String = "Unknown Folder"

/** A visible folder section on the Threads screen. */
data class ThreadFolderSection(
    val key: String,
    val folderName: String,
    val pathSubtitle: String?,
    val threads: List<ThreadSummary>,
    val newestUpdatedAtEpochSeconds: Long,
)

internal sealed interface ThreadsListItem {
    val key: String
    val contentType: String
}

internal data class ThreadFolderHeaderItem(
    val section: ThreadFolderSection,
    val isExpanded: Boolean,
) : ThreadsListItem {
    override val key: String = "folder:${section.key}"
    override val contentType: String = "folder_header"
}

internal data class ThreadFolderThreadItem(
    val sectionKey: String,
    val thread: ThreadSummary,
    val resultDigest: ThreadResultDigest?,
) : ThreadsListItem {
    override val key: String = "thread:${thread.id}"
    override val contentType: String = "thread_row"
}

internal data class ThreadFolderShowMoreItem(
    val sectionKey: String,
    val hiddenThreadCount: Int,
) : ThreadsListItem {
    override val key: String = "show-more:$sectionKey"
    override val contentType: String = "show_more"
}

internal fun buildThreadFolderSections(threads: List<ThreadSummary>): List<ThreadFolderSection> {
    if (threads.isEmpty()) return emptyList()

    val groupedThreads: Map<String, List<ThreadSummary>> = threads.groupBy { thread ->
        normalizeFolderPath(thread.cwd) ?: UnknownFolderKey
    }

    val sortedGroups: List<GroupedThreadsDraft> = groupedThreads.map { (groupKey, groupThreads) ->
        val sortedThreads: List<ThreadSummary> = groupThreads.sortedByDescending { thread ->
            thread.updatedAtEpochSeconds
        }
        val normalizedPath: String? = groupKey.takeUnless { it == UnknownFolderKey }
        GroupedThreadsDraft(
            key = groupKey,
            normalizedPath = normalizedPath,
            folderName = normalizedPath?.let(::folderNameFromPath) ?: UnknownFolderLabel,
            threads = sortedThreads,
        )
    }

    val duplicateFolderNames: Set<String> = sortedGroups
        .groupingBy { draft -> draft.folderName.lowercase() }
        .eachCount()
        .filterValues { count -> count > 1 }
        .keys

    return sortedGroups
        .map { draft ->
            ThreadFolderSection(
                key = draft.key,
                folderName = draft.folderName,
                pathSubtitle = if (draft.folderName.lowercase() in duplicateFolderNames) {
                    draft.normalizedPath
                } else {
                    null
                },
                threads = draft.threads,
                newestUpdatedAtEpochSeconds = draft.threads.firstOrNull()?.updatedAtEpochSeconds ?: 0L,
            )
        }
        .sortedByDescending { section -> section.newestUpdatedAtEpochSeconds }
}

internal fun buildThreadFolderListItems(
    sections: List<ThreadFolderSection>,
    unreadResultDigests: Map<String, ThreadResultDigest>,
    collapsedSectionKeys: Set<String>,
    expandedSectionKeys: Set<String>,
    initialVisibleThreadsPerFolder: Int = InitialVisibleThreadsPerFolder,
): List<ThreadsListItem> = buildList {
    sections.forEach { section ->
        val isExpanded: Boolean = section.key !in collapsedSectionKeys
        add(
            ThreadFolderHeaderItem(
                section = section,
                isExpanded = isExpanded,
            ),
        )
        if (!isExpanded) return@forEach

        val visibleThreads: List<ThreadSummary> = if (section.key in expandedSectionKeys) {
            section.threads
        } else {
            section.threads.take(initialVisibleThreadsPerFolder)
        }

        visibleThreads.forEach { thread ->
            add(
                ThreadFolderThreadItem(
                    sectionKey = section.key,
                    thread = thread,
                    resultDigest = unreadResultDigests[thread.id],
                ),
            )
        }

        val hiddenThreadCount: Int = section.threads.size - visibleThreads.size
        if (hiddenThreadCount > 0) {
            add(
                ThreadFolderShowMoreItem(
                    sectionKey = section.key,
                    hiddenThreadCount = hiddenThreadCount,
                ),
            )
        }
    }
}

private data class GroupedThreadsDraft(
    val key: String,
    val normalizedPath: String?,
    val folderName: String,
    val threads: List<ThreadSummary>,
)

private fun normalizeFolderPath(cwd: String): String? {
    val normalizedPath: String = cwd
        .trim()
        .replace('\\', '/')
        .trimEnd('/')
    return normalizedPath.takeIf { path -> path.isNotBlank() }
}

private fun folderNameFromPath(path: String): String = path.substringAfterLast('/').ifBlank { path }
