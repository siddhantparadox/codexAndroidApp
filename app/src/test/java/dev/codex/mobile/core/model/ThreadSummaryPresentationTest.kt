package dev.codex.mobile.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadSummaryPresentationTest {
    @Test
    fun runtimeSettingsLabelUsesModelAndEffort() {
        val summary = testThreadSummary(
            name = "Title",
            preview = "Preview",
            currentModelName = "GPT-5.4",
            currentReasoningEffort = ComposerReasoningEffort.High,
        )

        assertEquals("GPT-5.4 • High", summary.runtimeSettingsLabel())
    }

    @Test
    fun runtimeSettingsLabelIsNullWithoutModelAndEffort() {
        val summary = testThreadSummary(name = null, preview = "")

        assertNull(summary.runtimeSettingsLabel())
    }

    @Test
    fun displayTitleFallsBackToPreviewThenFolderThenGeneric() {
        val previewFallback = testThreadSummary(
            name = null,
            preview = "  Investigate reconnect state machine  ",
            cwd = "D:/projects/codexAndroidApp",
        )
        val folderFallback = testThreadSummary(
            name = null,
            preview = "",
            cwd = "D:/projects/codexAndroidApp",
        )
        val genericFallback = testThreadSummary(
            name = null,
            preview = "",
            cwd = "",
        )

        assertEquals("Investigate reconnect state machine", previewFallback.displayTitle())
        assertEquals("codexAndroidApp thread", folderFallback.displayTitle())
        assertEquals("Thread", genericFallback.displayTitle())
    }

    @Test
    fun compactListMetadataLabelShowsStatusModelAndBranch() {
        val summary = testThreadSummary(
            currentModelName = "GPT-5.4",
            gitBranch = "main",
            status = ThreadStatus(
                type = ThreadStatusType.Active,
                activeFlags = setOf("waitingOnApproval"),
            ),
        )

        assertEquals("Needs Approval • GPT-5.4 • main", summary.compactListMetadataLabel())
    }

    @Test
    fun compactListMetadataLabelOmitsIdleStatus() {
        val summary = testThreadSummary(
            currentModelName = "GPT-5.4 Mini",
            gitBranch = "feature/threads",
            status = ThreadStatus(type = ThreadStatusType.Idle),
        )

        assertEquals("GPT-5.4 Mini • feature/threads", summary.compactListMetadataLabel())
    }
}

private fun testThreadSummary(
    name: String? = "Title",
    preview: String = "Preview",
    cwd: String = "D:/projects/sample",
    status: ThreadStatus = ThreadStatus(type = ThreadStatusType.Idle),
    currentModelName: String? = null,
    currentReasoningEffort: ComposerReasoningEffort? = null,
    gitBranch: String? = null,
): ThreadSummary = ThreadSummary(
    id = "thread-1",
    name = name,
    preview = preview,
    createdAtEpochSeconds = 0L,
    updatedAtEpochSeconds = 0L,
    modelProvider = "openai",
    ephemeral = false,
    status = status,
    cwd = cwd,
    currentModelName = currentModelName,
    currentReasoningEffort = currentReasoningEffort,
    gitBranch = gitBranch,
)
