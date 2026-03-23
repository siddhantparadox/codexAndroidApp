package dev.codex.mobile.feature.threads

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dev.codex.mobile.core.designsystem.theme.CodexMobileTheme
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ThreadsScreenContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showMoreRevealsHiddenThreadsInsideFolder() {
        val section = buildThreadFolderSections(
            threads = (1..5).map { index ->
                testUiThread(
                    id = "thread-$index",
                    name = "Thread $index",
                    cwd = "D:/projects/codexAndroidApp",
                    updatedAt = index.toLong(),
                )
            },
        ).single()

        composeRule.setContent {
            CodexMobileTheme(useDarkTheme = true) {
                ThreadsScreenContent(
                    uiState = ThreadsUiState(
                        canRefresh = true,
                        canCreateThread = true,
                        folderSections = listOf(section),
                        threads = section.threads,
                    ),
                    onRefresh = {},
                    onQueryChanged = {},
                    onFilterSelected = {},
                    onOpenCreateThreadPicker = {},
                    onOpenThread = {},
                )
            }
        }

        assertTrue(composeRule.onAllNodesWithText("Thread 1").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("show-more-D:/projects/codexAndroidApp").performClick()
        assertEquals(1, composeRule.onAllNodesWithText("Thread 1").fetchSemanticsNodes().size)
    }

    @Test
    fun tappingFolderHeaderCollapsesAndReExpandsSection() {
        val section = buildThreadFolderSections(
            threads = listOf(
                testUiThread(
                    id = "thread-1",
                    name = "Visible thread",
                    cwd = "D:/projects/codexAndroidApp",
                    updatedAt = 10L,
                ),
            ),
        ).single()

        composeRule.setContent {
            CodexMobileTheme(useDarkTheme = true) {
                ThreadsScreenContent(
                    uiState = ThreadsUiState(
                        canRefresh = true,
                        canCreateThread = true,
                        folderSections = listOf(section),
                        threads = section.threads,
                    ),
                    onRefresh = {},
                    onQueryChanged = {},
                    onFilterSelected = {},
                    onOpenCreateThreadPicker = {},
                    onOpenThread = {},
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("Visible thread").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("folder-header-D:/projects/codexAndroidApp").performClick()
        assertTrue(composeRule.onAllNodesWithText("Visible thread").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("folder-header-D:/projects/codexAndroidApp").performClick()
        assertEquals(1, composeRule.onAllNodesWithText("Visible thread").fetchSemanticsNodes().size)
    }

    @Test
    fun threadRowShowsPreviewFallbackTitleAndCompactMetadata() {
        val section = buildThreadFolderSections(
            threads = listOf(
                testUiThread(
                    id = "thread-1",
                    name = null,
                    preview = "Investigate websocket reconnect state",
                    cwd = "D:/projects/codexAndroidApp",
                    updatedAt = 10L,
                    status = ThreadStatus(
                        type = ThreadStatusType.Active,
                        activeFlags = setOf("waitingOnApproval"),
                    ),
                    currentModelName = "GPT-5.4",
                    gitBranch = "main",
                ),
            ),
        ).single()

        composeRule.setContent {
            CodexMobileTheme(useDarkTheme = true) {
                ThreadsScreenContent(
                    uiState = ThreadsUiState(
                        canRefresh = true,
                        canCreateThread = true,
                        folderSections = listOf(section),
                        threads = section.threads,
                    ),
                    onRefresh = {},
                    onQueryChanged = {},
                    onFilterSelected = {},
                    onOpenCreateThreadPicker = {},
                    onOpenThread = {},
                )
            }
        }

        assertEquals(1, composeRule.onAllNodesWithText("Investigate websocket reconnect state").fetchSemanticsNodes().size)
        assertEquals(1, composeRule.onAllNodesWithText("Needs Approval • GPT-5.4 • main").fetchSemanticsNodes().size)
    }
}

private fun testUiThread(
    id: String,
    name: String?,
    cwd: String,
    updatedAt: Long,
    preview: String = "Preview for $id",
    status: ThreadStatus = ThreadStatus(type = ThreadStatusType.Idle),
    currentModelName: String? = null,
    gitBranch: String? = null,
): ThreadSummary = ThreadSummary(
    id = id,
    name = name,
    preview = preview,
    createdAtEpochSeconds = updatedAt,
    updatedAtEpochSeconds = updatedAt,
    modelProvider = "openai",
    ephemeral = false,
    status = status,
    cwd = cwd,
    currentModelName = currentModelName,
    gitBranch = gitBranch,
)
