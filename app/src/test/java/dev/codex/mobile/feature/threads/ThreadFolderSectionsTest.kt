package dev.codex.mobile.feature.threads

import dev.codex.mobile.core.model.ThreadResultDigest
import dev.codex.mobile.core.model.ThreadResultDigestKind
import dev.codex.mobile.core.model.ThreadStatus
import dev.codex.mobile.core.model.ThreadStatusType
import dev.codex.mobile.core.model.ThreadSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadFolderSectionsTest {
    @Test
    fun buildThreadFolderSections_ordersFoldersByNewestThread_andThreadsByRecency() {
        val sections = buildThreadFolderSections(
            threads = listOf(
                testThread(
                    id = "android-older",
                    name = "Older Android thread",
                    cwd = "D:/projects/codexAndroidApp",
                    updatedAt = 200L,
                ),
                testThread(
                    id = "android-newer",
                    name = "Newer Android thread",
                    cwd = "D:/projects/codexAndroidApp",
                    updatedAt = 250L,
                ),
                testThread(
                    id = "interview",
                    name = "Interview prep",
                    cwd = "D:/projects/interviews",
                    updatedAt = 300L,
                ),
            ),
        )

        assertEquals(listOf("interviews", "codexAndroidApp"), sections.map(ThreadFolderSection::folderName))
        assertEquals(
            listOf("android-newer", "android-older"),
            sections.last().threads.map(ThreadSummary::id),
        )
    }

    @Test
    fun buildThreadFolderSections_disambiguatesDuplicateFolderNamesWithPathSubtitle() {
        val sections = buildThreadFolderSections(
            threads = listOf(
                testThread(
                    id = "api-one",
                    cwd = "D:/projects/api",
                    updatedAt = 100L,
                ),
                testThread(
                    id = "api-two",
                    cwd = "C:\\work\\api",
                    updatedAt = 90L,
                ),
            ),
        )

        assertEquals(2, sections.size)
        assertTrue(sections.all { section -> section.folderName == "api" })
        assertEquals(
            setOf("D:/projects/api", "C:/work/api"),
            sections.mapNotNull(ThreadFolderSection::pathSubtitle).toSet(),
        )
    }

    @Test
    fun buildThreadFolderSections_groupsBlankPathsIntoUnknownFolder() {
        val sections = buildThreadFolderSections(
            threads = listOf(
                testThread(id = "unknown-one", cwd = "", updatedAt = 50L),
                testThread(id = "unknown-two", cwd = "   ", updatedAt = 60L),
            ),
        )

        assertEquals(1, sections.size)
        assertEquals("Unknown Folder", sections.single().folderName)
        assertNull(sections.single().pathSubtitle)
        assertEquals(listOf("unknown-two", "unknown-one"), sections.single().threads.map(ThreadSummary::id))
    }

    @Test
    fun buildThreadFolderListItems_respectsCollapseAndShowMoreState() {
        val section = buildThreadFolderSections(
            threads = (1..5).map { index ->
                testThread(
                    id = "thread-$index",
                    name = "Thread $index",
                    cwd = "D:/projects/codexAndroidApp",
                    updatedAt = index.toLong(),
                )
            },
        ).single()
        val unreadDigests = mapOf(
            "thread-5" to ThreadResultDigest(
                kind = ThreadResultDigestKind.PatchReady,
                title = "Patch ready",
                addedLineCount = 12,
                removedLineCount = 2,
            ),
        )

        val defaultItems = buildThreadFolderListItems(
            sections = listOf(section),
            unreadResultDigests = unreadDigests,
            collapsedSectionKeys = emptySet(),
            expandedSectionKeys = emptySet(),
        )
        val expandedItems = buildThreadFolderListItems(
            sections = listOf(section),
            unreadResultDigests = unreadDigests,
            collapsedSectionKeys = emptySet(),
            expandedSectionKeys = setOf(section.key),
        )
        val collapsedItems = buildThreadFolderListItems(
            sections = listOf(section),
            unreadResultDigests = unreadDigests,
            collapsedSectionKeys = setOf(section.key),
            expandedSectionKeys = emptySet(),
        )

        assertEquals(6, defaultItems.size)
        assertTrue(defaultItems.first() is ThreadFolderHeaderItem)
        assertTrue(defaultItems.last() is ThreadFolderShowMoreItem)
        assertEquals(5, expandedItems.filterIsInstance<ThreadFolderThreadItem>().size)
        assertTrue(expandedItems.none { item -> item is ThreadFolderShowMoreItem })
        assertEquals(1, collapsedItems.size)
        assertTrue(collapsedItems.single() is ThreadFolderHeaderItem)
    }
}

private fun testThread(
    id: String,
    name: String? = id,
    cwd: String,
    updatedAt: Long,
): ThreadSummary = ThreadSummary(
    id = id,
    name = name,
    preview = "Preview for $id",
    createdAtEpochSeconds = updatedAt,
    updatedAtEpochSeconds = updatedAt,
    modelProvider = "openai",
    ephemeral = false,
    status = ThreadStatus(type = ThreadStatusType.Idle),
    cwd = cwd,
)
