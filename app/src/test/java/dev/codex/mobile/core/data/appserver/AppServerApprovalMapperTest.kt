package dev.codex.mobile.core.data.appserver

import dev.codex.mobile.core.model.ApprovalDecision
import dev.codex.mobile.core.model.ApprovalKind
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppServerApprovalMapperTest {
    @Test
    fun commandApprovalMapperParsesStructuredAvailableDecisions() {
        val approval = buildJsonObject {
            put("method", "item/commandExecution/requestApproval")
            putJsonObject("params") {
                put("threadId", "thread-1")
                put("turnId", "turn-1")
                put("itemId", "item-1")
                put("command", "Get-Item simple_page.html")
                put(
                    "availableDecisions",
                    buildJsonArray {
                        add(JsonPrimitive("accept"))
                        add(
                            buildJsonObject {
                                putJsonObject("acceptWithExecpolicyAmendment") {
                                    put(
                                        "execpolicy_amendment",
                                        buildJsonArray {
                                            add(JsonPrimitive("Get-Item simple_page.html"))
                                        },
                                    )
                                }
                            },
                        )
                        add(
                            buildJsonObject {
                                putJsonObject("applyNetworkPolicyAmendment") {
                                    putJsonObject("network_policy_amendment") {
                                        put("action", "allow")
                                        put("host", "developers.openai.com")
                                    }
                                }
                            },
                        )
                        add(JsonPrimitive("decline"))
                    },
                )
            }
        }.toApprovalItem(requestId = JsonPrimitive("request-1"))

        requireNotNull(approval)
        assertEquals(4, approval.availableDecisions.size)
        assertEquals(ApprovalDecision.Accept, approval.availableDecisions.first())
        assertTrue(approval.availableDecisions[1] is ApprovalDecision.AcceptWithExecpolicyAmendment)
        assertTrue(approval.availableDecisions[2] is ApprovalDecision.ApplyNetworkPolicyAmendment)
        assertEquals(ApprovalDecision.Decline, approval.availableDecisions.last())
    }

    @Test
    fun commandApprovalDecisionPayloadSerializesStructuredDecision() {
        val payload = commandApprovalDecisionPayload(
            ApprovalDecision.ApplyNetworkPolicyAmendment(
                action = "allow",
                host = "developers.openai.com",
            ),
        )

        val amendment = payload
            .objectAt("decision")
            ?.objectAt("applyNetworkPolicyAmendment")
            ?.objectAt("network_policy_amendment")

        requireNotNull(amendment)
        assertEquals("allow", amendment.string("action"))
        assertEquals("developers.openai.com", amendment.string("host"))
    }

    @Test
    fun commandApprovalDecisionPayloadWrapsPrimitiveDecision() {
        val payload = commandApprovalDecisionPayload(ApprovalDecision.Accept)

        assertEquals("accept", payload["decision"]?.jsonPrimitive?.content)
    }

    @Test
    fun fileChangeApprovalDecisionPayloadWrapsDecision() {
        val payload = fileChangeApprovalDecisionPayload(ApprovalDecision.AcceptForSession)

        assertEquals("acceptForSession", payload["decision"]?.jsonPrimitive?.content)
    }

    @Test
    fun commandApprovalMapperBuildsFallbackDecisionsFromProposedAmendment() {
        val approval = buildJsonObject {
            put("method", "item/commandExecution/requestApproval")
            putJsonObject("params") {
                put("threadId", "thread-1")
                put("turnId", "turn-1")
                put("itemId", "item-1")
                put("command", "Get-Item simple_page.html")
                put(
                    "proposedExecpolicyAmendment",
                    buildJsonArray {
                        add(JsonPrimitive("Get-Item simple_page.html"))
                    },
                )
            }
        }.toApprovalItem(requestId = JsonPrimitive("request-1"))

        requireNotNull(approval)
        assertEquals(4, approval.availableDecisions.size)
        assertEquals(ApprovalDecision.Accept, approval.availableDecisions[0])
        assertTrue(approval.availableDecisions[1] is ApprovalDecision.AcceptWithExecpolicyAmendment)
        assertEquals(ApprovalDecision.Decline, approval.availableDecisions[2])
        assertEquals(ApprovalDecision.Cancel, approval.availableDecisions[3])
    }

    @Test
    fun permissionsApprovalMapperParsesRequestedPermissions() {
        val approval = buildJsonObject {
            put("method", "item/permissions/requestApproval")
            putJsonObject("params") {
                put("threadId", "thread-1")
                put("turnId", "turn-1")
                put("itemId", "item-1")
                putJsonObject("permissions") {
                    putJsonObject("fileSystem") {
                        put(
                            "write",
                            buildJsonArray {
                                add(JsonPrimitive("D:/projects/codexAndroidApp/app/src/main"))
                            },
                        )
                    }
                    putJsonObject("network") {
                        put("enabled", true)
                    }
                }
            }
        }.toApprovalItem(requestId = JsonPrimitive("request-2"))

        requireNotNull(approval)
        assertEquals(ApprovalKind.Permissions, approval.kind)
        assertEquals(
            listOf("D:/projects/codexAndroidApp/app/src/main"),
            approval.requestedPermissions?.writePaths,
        )
        assertEquals(true, approval.requestedPermissions?.networkEnabled)
        assertEquals(
            listOf(
                ApprovalDecision.Accept,
                ApprovalDecision.AcceptForSession,
                ApprovalDecision.Decline,
            ),
            approval.availableDecisions,
        )
    }

    @Test
    fun permissionsApprovalPayloadGrantsRequestedPermissionsForSession() {
        val payload = permissionsApprovalPayload(
            decision = ApprovalDecision.AcceptForSession,
            requestedPermissions = buildJsonObject {
                putJsonObject("network") {
                    put("enabled", true)
                }
            },
        )

        assertEquals("session", payload.string("scope"))
        assertEquals(true, payload.objectAt("permissions")?.objectAt("network")?.boolean("enabled"))
    }

    @Test
    fun permissionsApprovalPayloadDeclineClearsGrantedPermissions() {
        val payload = permissionsApprovalPayload(
            decision = ApprovalDecision.Decline,
            requestedPermissions = buildJsonObject {
                putJsonObject("network") {
                    put("enabled", true)
                }
            },
        )

        assertTrue(payload.objectAt("permissions")?.isEmpty() == true)
        assertEquals(null, payload["scope"])
    }
}
