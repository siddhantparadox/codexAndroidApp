package dev.codex.mobile.core.data.appserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

    @Test
    fun turnStartParamsPayloadIncludesApprovalPolicyWhenProvided() {
        val payload = turnStartParamsPayload(
            threadId = "thread-1",
            input = emptyList(),
            approvalPolicy = JsonPrimitive("on-request"),
            sandboxPolicy = buildJsonObject {
                put("type", "readOnly")
            },
        )

        assertEquals("thread-1", payload.string("threadId"))
        assertEquals("on-request", payload["approvalPolicy"]?.jsonPrimitive?.content)
        assertEquals("readOnly", payload.objectAt("sandboxPolicy")?.string("type"))
    }

    @Test
    fun turnStartParamsPayloadOmitsApprovalPolicyWhenNotProvided() {
        val payload = turnStartParamsPayload(
            threadId = "thread-1",
            input = emptyList(),
        )

        assertNull(payload["approvalPolicy"])
    }
}
