package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.ThreadDynamicToolKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicToolRequestMapperTest {
    @Test
    fun mapsPickPhotoDynamicToolRequest() {
        val json = Json.parseToJsonElement(
            """
            {
              "method": "item/tool/call",
              "params": {
                "threadId": "thread-1",
                "turnId": "turn-1",
                "itemId": "item-dynamic-1",
                "tool": "pick_photo",
                "arguments": {
                  "reason": "Attach the screenshot you want me to inspect."
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val request = json.toThreadDynamicToolRequest(requestId = JsonPrimitive("request-1"))

        requireNotNull(request)
        assertEquals("request-1", request.requestId)
        assertEquals(ThreadDynamicToolKind.PickPhoto, request.kind)
        assertEquals("item-dynamic-1", request.itemId)
        assertEquals("Attach the screenshot you want me to inspect.", request.prompt)
        assertTrue(request.arguments.contains("reason"))
    }

    @Test
    fun ignoresUnsupportedDynamicToolRequest() {
        val json = Json.parseToJsonElement(
            """
            {
              "method": "item/tool/call",
              "params": {
                "threadId": "thread-1",
                "tool": "record_audio",
                "arguments": {}
              }
            }
            """.trimIndent(),
        ).jsonObject

        val request = json.toThreadDynamicToolRequest(requestId = JsonPrimitive("request-1"))

        assertNull(request)
    }

    @Test
    fun buildsSuccessfulDynamicToolResponsePayload() {
        val payload = dynamicToolCallResponsePayload(
            contentItems = listOf(
                dynamicToolImageContentItemPayload("data:image/jpeg;base64,abc123"),
            ),
            success = true,
        )

        assertEquals("true", payload["success"]?.jsonPrimitive?.content)
        val contentItems = requireNotNull(payload["contentItems"]?.jsonArray)
        assertEquals(1, contentItems.size)
        val item = contentItems.single().jsonObject
        assertEquals("inputImage", item["type"]?.jsonPrimitive?.content)
        assertEquals("data:image/jpeg;base64,abc123", item["imageUrl"]?.jsonPrimitive?.content)
    }

    @Test
    fun buildsUnsuccessfulDynamicToolResponsePayload() {
        val payload = dynamicToolCallResponsePayload(success = false)

        assertEquals("false", payload["success"]?.jsonPrimitive?.content)
        assertEquals(null, payload["contentItems"])
    }
}
