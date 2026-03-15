package dev.codex.mobile.feature.threads

import dev.codex.mobile.core.model.ThreadSourceKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class ThreadCwdPickerTest {
    @Test
    fun buildThreadCwdOptionsIgnoresBlankValuesAndKeepsMostRecentPerPath() {
        val options = buildThreadCwdOptions(
            threads = listOf(
                threadSummary(id = "thread-1", cwd = "/projects/api", updatedAt = 100L),
                threadSummary(id = "thread-2", cwd = "  ", updatedAt = 200L),
                threadSummary(id = "thread-3", cwd = "/projects/mobile", updatedAt = 150L),
                threadSummary(id = "thread-4", cwd = "/projects/api", updatedAt = 250L),
            ),
        )

        assertEquals(
            listOf(
                ThreadCwdOption(path = "/projects/api", lastUsedAtEpochSeconds = 250L),
                ThreadCwdOption(path = "/projects/mobile", lastUsedAtEpochSeconds = 150L),
            ),
            options,
        )
    }

    @Test
    fun filterThreadCwdOptionsMatchesPathsCaseInsensitively() {
        val filtered = filterThreadCwdOptions(
            options = listOf(
                ThreadCwdOption(path = "/projects/api", lastUsedAtEpochSeconds = 100L),
                ThreadCwdOption(path = "/projects/MobileApp", lastUsedAtEpochSeconds = 80L),
            ),
            query = "mobile",
        )

        assertEquals(
            listOf(
                ThreadCwdOption(path = "/projects/MobileApp", lastUsedAtEpochSeconds = 80L),
            ),
            filtered,
        )
    }
}

private fun threadSummary(
    id: String,
    cwd: String,
    updatedAt: Long,
): ThreadSummary = ThreadSummary(
    id = id,
    name = id,
    preview = "preview",
    createdAtEpochSeconds = 1L,
    updatedAtEpochSeconds = updatedAt,
    modelProvider = "openai",
    ephemeral = false,
    status = ThreadStatus(type = ThreadStatusType.Idle),
    source = ThreadSourceKind.Cli,
    cwd = cwd,
)
