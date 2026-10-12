package org.fossify.messages.agent

sealed class Decision {
    data object Allow : Decision()
    data class Deny(val reason: String) : Decision()
}

object AgentPolicy {
    private const val MAX_SENDS_PER_EVENT = 1

    fun canSend(
        to: String,
        allowlist: Set<String>,
        sendsThisEvent: Int,
        sendsToday: Int,
        dailyCap: Int
    ): Decision = when {
        to !in allowlist -> Decision.Deny("$to is not on the allowlist")
        sendsThisEvent >= MAX_SENDS_PER_EVENT -> Decision.Deny("only one send_sms per event is allowed")
        sendsToday >= dailyCap -> Decision.Deny("daily cap of $dailyCap sends is reached")
        else -> Decision.Allow
    }
}
