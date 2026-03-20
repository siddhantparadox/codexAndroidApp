package dev.codex.mobile.feature.threaddetail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadRichTextTest {
    @Test
    fun localTranscriptLinksAreRecognized() {
        assertTrue(isTranscriptLocalLink("/Users/sgupt/project/app/src/MainActivity.kt"))
        assertTrue(isTranscriptLocalLink("../README.md"))
        assertTrue(isTranscriptLocalLink("./docs/plans/thread.md"))
        assertTrue(isTranscriptLocalLink("D:/projects/codexAndroidApp/app/src/main/java/dev/codex/mobile/MainActivity.kt"))
        assertTrue(isTranscriptLocalLink("C:\\Users\\sgupt\\notes\\todo.md"))
    }

    @Test
    fun externalUrisAreNotTreatedAsLocalTranscriptLinks() {
        assertFalse(isTranscriptLocalLink(""))
        assertFalse(isTranscriptLocalLink("https://developer.android.com"))
        assertFalse(isTranscriptLocalLink("http://example.com/file.kt"))
        assertFalse(isTranscriptLocalLink("mailto:test@example.com"))
        assertFalse(isTranscriptLocalLink("codex://thread/thread-123"))
    }

    @Test
    fun plainParagraphsUseFastTextPath() {
        assertFalse(shouldUseMarkdownRenderer("This is a normal Codex reply with no markdown syntax."))
        assertFalse(shouldUseMarkdownRenderer("Two plain paragraphs.\n\nStill just text."))
    }

    @Test
    fun markdownContentUsesMarkdownRenderer() {
        assertTrue(shouldUseMarkdownRenderer("```kotlin\nprintln(\"hi\")\n```"))
        assertTrue(shouldUseMarkdownRenderer("- first\n- second"))
        assertTrue(shouldUseMarkdownRenderer("[Android docs](https://developer.android.com)"))
        assertTrue(shouldUseMarkdownRenderer("Use `adb logcat` to inspect logs."))
    }
}
