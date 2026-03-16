package dev.codex.mobile.core.data.appserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AppServerJsonPayloadTest {
    @Test
    fun threadStartParamsPayloadIncludesCwdWhenProvided() {
        val payload = threadStartParamsPayload(cwd = "/projects/api")

        assertEquals("/projects/api", payload.string("cwd"))
        val dynamicTools = requireNotNull(payload.arrayAt("dynamicTools"))
        assertEquals(1, dynamicTools.size)
        val tool = dynamicTools.single().jsonObject
        assertEquals("pick_photo", tool.string("name"))
        assertEquals("object", tool.objectAt("inputSchema")?.string("type"))
        assertTrue(
            tool.objectAt("inputSchema")
                ?.objectAt("properties")
                ?.objectAt("reason")
                ?.string("type") == "string",
        )
    }

    @Test
    fun threadStartParamsPayloadOmitsBlankCwd() {
        val payload = threadStartParamsPayload(cwd = "   ")

        assertNull(payload.string("cwd"))
        assertFalse(payload.arrayAt("dynamicTools").isNullOrEmpty())
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
