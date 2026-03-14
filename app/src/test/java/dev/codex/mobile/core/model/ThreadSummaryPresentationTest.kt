package dev.codex.mobile.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThreadSummaryPresentationTest {
    @Test
    fun runtimeSettingsLabelUsesModelAndEffort() {
        val summary = ThreadSummary(
            id = "thread-1",
            name = "Title",
            preview = "Preview",
            createdAtEpochSeconds = 0L,
            updatedAtEpochSeconds = 0L,
            modelProvider = "openai",
            ephemeral = false,
            status = ThreadStatus(type = ThreadStatusType.Idle),
            currentModelName = "GPT-5.4",
            currentReasoningEffort = ComposerReasoningEffort.High,
        )

        assertEquals("GPT-5.4 • High", summary.runtimeSettingsLabel())
    }

    @Test
    fun runtimeSettingsLabelIsNullWithoutModelAndEffort() {
        val summary = ThreadSummary(
            id = "thread-1",
            name = null,
            preview = "",
            createdAtEpochSeconds = 0L,
            updatedAtEpochSeconds = 0L,
            modelProvider = "openai",
            ephemeral = false,
            status = ThreadStatus(type = ThreadStatusType.Idle),
        )

        assertNull(summary.runtimeSettingsLabel())
    }
}
