package org.fossify.messages.gateway

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayClientTest {
    // 2026-10-12T07:00:00.750Z, the milliseconds must be dropped
    private val at = 1_791_788_400_750L

    @Test
    fun smsEventJsonHasExactlyTheContractFields() {
        val event = GatewayEvent(
            kind = "sms_received",
            occurredAtMs = at,
            number = "+4512345678",
            displayName = "Alice",
            body = "Your code is 123456",
            id = "1b4e28ba-2fa1-4d3e-9c6a-7f0a1c2d3e4f"
        )
        val json = JSONObject(eventJson(event))
        assertEquals(setOf("id", "kind", "occurred_at", "number", "display_name", "body"), keys(json))
        assertEquals("1b4e28ba-2fa1-4d3e-9c6a-7f0a1c2d3e4f", json.getString("id"))
        assertEquals("sms_received", json.getString("kind"))
        assertEquals("2026-10-12T07:00:00Z", json.getString("occurred_at"))
        assertEquals("+4512345678", json.getString("number"))
        assertEquals("Alice", json.getString("display_name"))
        assertEquals("Your code is 123456", json.getString("body"))
    }

    @Test
    fun callEventJsonHasExactlyTheContractFields() {
        val event = GatewayEvent(
            kind = "call_ended",
            occurredAtMs = at,
            number = "+4512345678",
            displayName = "",
            direction = "incoming",
            durationS = 42,
            missed = false,
            id = "2c5f39cb-3ab2-4e4f-8d7b-8a1b2c3d4e5f"
        )
        val json = JSONObject(eventJson(event))
        assertEquals(
            setOf("id", "kind", "occurred_at", "number", "display_name", "direction", "duration_s", "missed"),
            keys(json)
        )
        assertEquals("2c5f39cb-3ab2-4e4f-8d7b-8a1b2c3d4e5f", json.getString("id"))
        assertEquals("call_ended", json.getString("kind"))
        assertEquals("2026-10-12T07:00:00Z", json.getString("occurred_at"))
        assertEquals("+4512345678", json.getString("number"))
        assertEquals("", json.getString("display_name"))
        assertEquals("incoming", json.getString("direction"))
        assertEquals(42, json.getInt("duration_s"))
        assertFalse(json.getBoolean("missed"))
    }

    @Test
    fun defaultIdIsUuidV4() {
        val id = GatewayEvent("sms_received", at, "", "", body = "").id
        assertTrue(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")))
    }

    @Test
    fun workDataKeepsIdAndCapsFieldsUnderTheDataLimit() {
        // '€' is 3 bytes in modified UTF-8, the worst case for the 10 KB WorkManager Data limit
        val event = GatewayEvent("sms_received", at, "+4512345678", "€".repeat(500), body = "€".repeat(8000))
        val restored = GatewayEvent.from(event.toData())
        assertEquals(event.id, restored.id)
        assertEquals(3000, restored.body?.length)
        assertEquals(120, restored.displayName.length)
        assertEquals(null, restored.durationS)
    }

    @Test
    fun gatewayUrlMustBeHttps() {
        assertFalse(isGatewayUrl("http://gateway.example.ts.net"))
        assertFalse(isGatewayUrl("https://"))
        assertFalse(isGatewayUrl(""))
        assertFalse(isGatewayUrl("https://gateway.example.ts.net/?key=x"))
        assertFalse(isGatewayUrl("https://gateway.example.ts.net/#x"))
        assertTrue(isGatewayUrl("https://gateway.example.ts.net"))
        assertTrue(isGatewayUrl("https://gateway.example.ts.net/"))
    }

    @Test
    fun allowlistFilter() {
        assertTrue(isAllowed("+4512345678", emptySet()))
        assertTrue(isAllowed("+4512345678", setOf("+4512345678")))
        assertFalse(isAllowed("+4599999999", setOf("+4512345678")))
    }

    private fun keys(json: JSONObject) = json.keys().asSequence().toSet()
}
