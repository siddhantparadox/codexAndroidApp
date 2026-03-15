package dev.codex.mobile.core.data.appserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppServerJsonPayloadTest {
    @Test
    fun threadStartParamsPayloadIncludesCwdWhenProvided() {
        val payload = threadStartParamsPayload(cwd = "/projects/api")

        assertEquals("/projects/api", payload.string("cwd"))
    }

    @Test
    fun threadStartParamsPayloadOmitsBlankCwd() {
        val payload = threadStartParamsPayload(cwd = "   ")

        assertNull(payload.string("cwd"))
    }
}
