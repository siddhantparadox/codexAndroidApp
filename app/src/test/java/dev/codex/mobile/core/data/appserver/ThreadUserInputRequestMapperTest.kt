package dev.codex.mobile.core.data.appserver

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadUserInputRequestMapperTest {
    @Test
    fun mapsToolRequestUserInputServerRequest() {
        val json = Json.parseToJsonElement(
            """
            {
              "method": "item/tool/requestUserInput",
              "params": {
                "threadId": "thread-1",
                "turnId": "turn-1",
                "itemId": "item-1",
                "questions": [
                  {
                    "id": "q-1",
                    "header": "Styles",
                    "question": "Use the minimal stylesheet only?",
                    "options": [
                      {
                        "label": "Minimal stylesheet only",
                        "description": "Keep the patch scoped to a stylesheet link."
                      },
                      {
                        "label": "Add visible styles too",
                        "description": "Include a few starter styles in the same pass."
                      }
                    ],
                    "isOther": true,
                    "isSecret": false
                  }
                ]
              }
            }
            """.trimIndent(),
        ).jsonObject

        val request = json.toThreadUserInputRequest(requestId = JsonPrimitive("request-1"))

        requireNotNull(request)
        assertEquals("request-1", request.requestId)
        assertEquals("thread-1", request.threadId)
        assertEquals("item-1", request.itemId)
        assertEquals(1, request.questions.size)
        assertTrue(request.questions.single().isOtherAllowed)
    }

    @Test
    fun buildsToolRequestUserInputResponsePayload() {
        val payload = toolRequestUserInputResponsePayload(
            answers = mapOf(
                "q-1" to listOf("Minimal stylesheet only"),
                "q-2" to listOf(""),
            ),
        )

        val answers = requireNotNull(payload["answers"]?.jsonObject)
        assertEquals(
            "Minimal stylesheet only",
            answers.getValue("q-1").jsonObject
                .getValue("answers")
                .jsonArray[0]
                .jsonPrimitive
                .content,
        )
        assertTrue(
            answers.getValue("q-2").jsonObject
                .getValue("answers")
                .jsonArray
                .isEmpty(),
        )
        assertFalse(answers.isEmpty())
    }
}
