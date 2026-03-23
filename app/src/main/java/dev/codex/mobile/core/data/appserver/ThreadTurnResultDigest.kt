package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.InAppThreadNotification
import dev.codex.mobile.core.model.ThreadItem
import dev.codex.mobile.core.model.ThreadItemStatus
import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadResultDigestKind
import dev.codex.mobile.core.model.displayText
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal data class ThreadTurnResult(
    val threadId: String,
    val threadTitle: String,
    val digest: ThreadResultDigest,
)

internal fun JsonObject.toThreadTurnResult(
    turnId: String,
    fallbackTurnStatus: String,
    fallbackTurnError: String?,
): ThreadTurnResult? {
    val turns: List<JsonObject> = arrayAt("turns")
        ?.map { turn -> turn.jsonObject }
        .orEmpty()
    val turn: JsonObject = turns.lastOrNull { turn -> turn.string("id") == turnId } ?: return null
    val turnStatus: String = turn.string("status") ?: fallbackTurnStatus
    if (turnStatus == "interrupted") return null

    val items: List<ThreadItem> = turn.arrayAt("items")
        ?.map { item -> item.jsonObject.toThreadItem() }
        .orEmpty()
    val digest: ThreadResultDigest = buildThreadResultDigest(
        items = items,
        turnStatus = turnStatus,
        turnError = turn.objectAt("error")?.string("message") ?: fallbackTurnError,
    ) ?: return null

    return ThreadTurnResult(
        threadId = string("id").orEmpty(),
        threadTitle = displayThreadTitle(),
        digest = digest,
    )
}

internal fun ThreadTurnResult.toInAppThreadNotification(
    turnId: String,
    createdAtEpochSeconds: Long,
): InAppThreadNotification = InAppThreadNotification(
    id = "$threadId-$turnId",
    threadId = threadId,
    title = when (digest.kind) {
        ThreadResultDigestKind.PatchReady -> "$threadTitle complete"
        ThreadResultDigestKind.ReplyReady -> "$threadTitle complete"
        ThreadResultDigestKind.Failed -> "$threadTitle failed"
        ThreadResultDigestKind.Completed -> "$threadTitle complete"
    },
    message = digest.displayText,
    createdAtEpochSeconds = createdAtEpochSeconds,
)

internal fun buildThreadResultDigest(
    items: List<ThreadItem>,
    turnStatus: String,
    turnError: String?,
): ThreadResultDigest? {
    if (turnStatus == "failed") {
        val failedCommand: ThreadItem.CommandExecution? = items
            .filterIsInstance<ThreadItem.CommandExecution>()
            .lastOrNull { item -> item.status == ThreadItemStatus.Failed }
        val failureSummary: String? = firstMeaningfulLine(
            turnError,
            failedCommand?.aggregatedOutput,
            failedCommand?.command,
        )
        return ThreadResultDigest(
            kind = ThreadResultDigestKind.Failed,
            title = "Command failed",
            supportingText = failureSummary,
        )
    }

    val fileChanges: List<ThreadItem.FileChange> = items.filterIsInstance<ThreadItem.FileChange>()
    if (fileChanges.isNotEmpty()) {
        val fileEntries = fileChanges.flatMap(ThreadItem.FileChange::changes)
        val fileCount: Int = fileEntries.size
        val lineStats: DiffLineStats = fileEntries.fold(DiffLineStats()) { running, change ->
            running + DiffLineStats.fromUnifiedDiff(change.diff)
        }
        val supportingText: String = buildString {
            append(fileCount)
            append(if (fileCount == 1) " file" else " files")
            if (lineStats.addedLineCount > 0 || lineStats.removedLineCount > 0) {
                append(" · +")
                append(lineStats.addedLineCount)
                append(" -")
                append(lineStats.removedLineCount)
            }
        }
        return ThreadResultDigest(
            kind = ThreadResultDigestKind.PatchReady,
            title = "Patch ready",
            supportingText = supportingText,
            fileCount = fileCount,
            addedLineCount = lineStats.addedLineCount,
            removedLineCount = lineStats.removedLineCount,
        )
    }

    val finalReply: String? = items
        .filterIsInstance<ThreadItem.AgentMessage>()
        .lastOrNull { item -> item.text.isNotBlank() && item.phase != "commentary" }
        ?.text
        ?.singleLinePreview()
    if (finalReply != null) {
        return ThreadResultDigest(
            kind = ThreadResultDigestKind.ReplyReady,
            title = "Reply ready",
            supportingText = finalReply,
        )
    }

    return if (turnStatus == "completed") {
        ThreadResultDigest(
            kind = ThreadResultDigestKind.Completed,
            title = "Thread complete",
        )
    } else {
        null
    }
}

private data class DiffLineStats(
    val addedLineCount: Int = 0,
    val removedLineCount: Int = 0,
) {
    operator fun plus(other: DiffLineStats): DiffLineStats = DiffLineStats(
        addedLineCount = addedLineCount + other.addedLineCount,
        removedLineCount = removedLineCount + other.removedLineCount,
    )

    companion object {
        fun fromUnifiedDiff(diff: String): DiffLineStats {
            var inHunk: Boolean = false
            var addedLineCount: Int = 0
            var removedLineCount: Int = 0

            diff.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split('\n')
                .forEach { line ->
                    when {
                        line.startsWith("@@") -> inHunk = true
                        !inHunk -> Unit
                        line.startsWith("+++") -> Unit
                        line.startsWith("---") -> Unit
                        line.startsWith("+") -> addedLineCount += 1
                        line.startsWith("-") -> removedLineCount += 1
                    }
                }

            return DiffLineStats(
                addedLineCount = addedLineCount,
                removedLineCount = removedLineCount,
            )
        }
    }
}

private fun firstMeaningfulLine(vararg values: String?): String? = values
    .firstNotNullOfOrNull { value ->
        value?.lineSequence()
            ?.map(String::trim)
            ?.firstOrNull { line -> line.isNotBlank() }
            ?.singleLinePreview()
    }

private fun String.singleLinePreview(maxLength: Int = 120): String = lineSequence()
    .map(String::trim)
    .filter(String::isNotBlank)
    .joinToString(separator = " ")
    .let { collapsed ->
        if (collapsed.length <= maxLength) {
            collapsed
        } else {
            collapsed.take(maxLength - 1).trimEnd() + "…"
        }
    }

private fun JsonObject.displayThreadTitle(): String = string("name")
    ?.singleLinePreview()
    ?.takeIf(String::isNotBlank)
    ?: string("preview")
        ?.singleLinePreview()
        ?.takeIf(String::isNotBlank)
    ?: string("cwd")
        ?.trim()
        ?.trimEnd('/', '\\')
        ?.substringAfterLast('/')
        ?.substringAfterLast('\\')
        ?.takeIf(String::isNotBlank)
        ?.let { folderName -> "$folderName thread" }
    ?: "Thread"
