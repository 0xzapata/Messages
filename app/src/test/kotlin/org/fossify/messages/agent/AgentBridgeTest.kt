package org.fossify.messages.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentBridgeTest {
    private val allowlist = setOf("+4512345678")

    @Test
    fun policyDeniesNumberOffAllowlist() {
        assertTrue(AgentPolicy.canSend("+4599999999", allowlist, 0, 0, 20) is Decision.Deny)
    }

    @Test
    fun policyAllowsOneSendPerEvent() {
        assertEquals(Decision.Allow, AgentPolicy.canSend("+4512345678", allowlist, 0, 0, 20))
        assertTrue(AgentPolicy.canSend("+4512345678", allowlist, 1, 1, 20) is Decision.Deny)
    }

    @Test
    fun policyEnforcesDailyCap() {
        assertEquals(Decision.Allow, AgentPolicy.canSend("+4512345678", allowlist, 0, 19, 20))
        assertTrue(AgentPolicy.canSend("+4512345678", allowlist, 0, 20, 20) is Decision.Deny)
    }

    @Test
    fun parsesBodyWithDoneTrailer() {
        val body = """{"stop_reason":"tool_use",""" +
            """"content":[{"type":"tool_use","id":"t1","name":"send_sms_ide","input":{}}]}""" +
            "\n\ndata: [DONE]\n"
        val json = parseJsonBody(body)
        assertEquals("tool_use", json.getString("stop_reason"))
        val name = json.getJSONArray("content").getJSONObject(0).getString("name")
        assertEquals("send_sms", toolName(name))
        assertEquals("read_thread", toolName("read_thread"))
    }

    @Test
    fun jevBlocksAtThresholdAndOnUnsafeChoice() {
        assertNull(jevBlockReason(jev("safe", 0.49), 0.5))
        assertEquals("safe 0.50", jevBlockReason(jev("safe", 0.5), 0.5))
        assertEquals("injection 0.10", jevBlockReason(jev("injection", 0.1), 0.5))
    }

    private fun jev(choice: String, noul: Double) = JSONObject(
        """{"answers":{"verdict":{"type":"choice","choice":"$choice","confidence":0.9},""" +
            """"block":{"type":"noul","noul":$noul}}}"""
    )
}
