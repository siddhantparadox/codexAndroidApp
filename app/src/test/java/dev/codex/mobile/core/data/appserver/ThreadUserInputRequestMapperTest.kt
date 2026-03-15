package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.ThreadUserInputAnswer
import dev.codex.mobile.core.model.ThreadUserInputPayload
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
        val payload = request.payload as ThreadUserInputPayload.ToolQuestions
        assertEquals(1, payload.questions.size)
        assertTrue(payload.questions.single().isOtherAllowed)
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

    @Test
    fun mapsMcpFormElicitationRequest() {
        val json = Json.parseToJsonElement(
            """
            {
              "method": "mcpServer/elicitation/request",
              "params": {
                "serverName": "github",
                "threadId": "thread-1",
                "turnId": null,
                "mode": "form",
                "message": "Complete the GitHub form",
                "requestedSchema": {
                  "type": "object",
                  "required": ["repo", "notify"],
                  "properties": {
                    "repo": {
                      "type": "string",
                      "title": "Repository",
                      "enum": ["openai/codex", "openai/api"],
                      "enumNames": ["Codex", "API"]
                    },
                    "notify": {
                      "type": "boolean",
                      "title": "Notify team",
                      "default": true
                    },
                    "labels": {
                      "type": "array",
                      "title": "Labels",
                      "items": {
                        "type": "string",
                        "anyOf": [
                          { "const": "bug", "title": "Bug" },
                          { "const": "docs", "title": "Docs" }
                        ]
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        ).jsonObject

        val request = json.toThreadUserInputRequest(requestId = JsonPrimitive("request-2"))

        requireNotNull(request)
        val payload = request.payload as ThreadUserInputPayload.McpForm
        assertEquals("github", payload.serverName)
        assertEquals("Complete the GitHub form", payload.message)
        assertEquals(3, payload.fields.size)
        assertEquals("Repository", payload.fields[0].label)
        assertTrue(payload.fields[0].required)
        assertTrue(payload.fields[1].required)
    }

    @Test
    fun mapsMcpUrlElicitationRequest() {
        val json = Json.parseToJsonElement(
            """
            {
              "method": "mcpServer/elicitation/request",
              "params": {
                "serverName": "github",
                "threadId": "thread-1",
                "turnId": "turn-1",
                "mode": "url",
                "elicitationId": "elicitation-1",
                "message": "Complete sign-in before continuing",
                "url": "https://github.com/login/device"
              }
            }
            """.trimIndent(),
        ).jsonObject

        val request = json.toThreadUserInputRequest(requestId = JsonPrimitive("request-3"))

        requireNotNull(request)
        val payload = request.payload as ThreadUserInputPayload.McpUrl
        assertEquals("elicitation-1", payload.elicitationId)
        assertEquals("https://github.com/login/device", payload.url)
    }

    @Test
    fun buildsMcpFormAcceptResponsePayload() {
        val request = Json.parseToJsonElement(
            """
            {
              "method": "mcpServer/elicitation/request",
              "params": {
                "serverName": "github",
                "threadId": "thread-1",
                "mode": "form",
                "message": "Complete the GitHub form",
                "requestedSchema": {
                  "type": "object",
                  "required": ["repo"],
                  "properties": {
                    "repo": {
                      "type": "string",
                      "enum": ["openai/codex"]
                    },
                    "notify": {
                      "type": "boolean",
                      "default": false
                    },
                    "labels": {
                      "type": "array",
                      "items": {
                        "type": "string",
                        "enum": ["bug", "docs"]
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        ).jsonObject.toThreadUserInputRequest(requestId = JsonPrimitive("request-4"))

        requireNotNull(request)
        val payload = requireNotNull(
            userInputResponsePayload(
                request = request,
                response = dev.codex.mobile.core.model.ThreadUserInputResponse.Accept(
                    answers = mapOf(
                        "repo" to ThreadUserInputAnswer.TextList(listOf("openai/codex")),
                        "notify" to ThreadUserInputAnswer.BooleanValue(true),
                        "labels" to ThreadUserInputAnswer.TextList(listOf("bug", "docs")),
                    ),
                ),
            ),
        )

        assertEquals("accept", payload.string("action"))
        val content = requireNotNull(payload["content"]?.jsonObject)
        assertEquals("openai/codex", content.getValue("repo").jsonPrimitive.content)
        assertEquals("true", content.getValue("notify").jsonPrimitive.content)
        assertEquals(2, content.getValue("labels").jsonArray.size)
    }

    @Test
    fun buildsMcpUrlDeclineResponsePayload() {
        val request = Json.parseToJsonElement(
            """
            {
              "method": "mcpServer/elicitation/request",
              "params": {
                "serverName": "github",
                "threadId": "thread-1",
                "mode": "url",
                "elicitationId": "elicitation-1",
                "message": "Complete sign-in before continuing",
                "url": "https://github.com/login/device"
              }
            }
            """.trimIndent(),
        ).jsonObject.toThreadUserInputRequest(requestId = JsonPrimitive("request-5"))

        requireNotNull(request)
        val payload = requireNotNull(
            userInputResponsePayload(
                request = request,
                response = dev.codex.mobile.core.model.ThreadUserInputResponse.Decline,
            ),
        )

        assertEquals("decline", payload.string("action"))
    }
}
