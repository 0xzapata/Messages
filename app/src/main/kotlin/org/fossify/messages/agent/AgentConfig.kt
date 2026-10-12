package org.fossify.messages.agent

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate

const val DEFAULT_DAILY_CAP = 20
private const val DEFAULT_JEV_BLOCK_THRESHOLD = 0.5f

class AgentConfig(context: Context) {
    private val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var baseUrl: String
        get() = getString("base_url").ifBlank { "https://api.anthropic.com" }
        set(value) = putString("base_url", value)

    var claudeKey: String
        get() = getString("claude_key")
        set(value) = putString("claude_key", value)

    var model: String
        get() = getString("model").ifBlank { "claude-opus-5-5" }
        set(value) = putString("model", value)

    var effort: String
        get() = getString("effort").ifBlank { "low" }
        set(value) = putString("effort", value)

    var jevKey: String
        get() = getString("jev_key")
        set(value) = putString("jev_key", value)

    val jevBlockThreshold: Double
        get() = prefs.getFloat("jev_block_threshold", DEFAULT_JEV_BLOCK_THRESHOLD).toDouble()

    var skillsTreeUri: String
        get() = getString("skills_tree_uri")
        set(value) = putString("skills_tree_uri", value)

    var allowlist: Set<String>
        get() = getString("allowlist").lines().filter { it.isNotBlank() }.toSet()
        set(value) = putString("allowlist", value.joinToString("\n"))

    var dailyCap: Int
        get() = prefs.getInt("daily_cap", DEFAULT_DAILY_CAP)
        set(value) = prefs.edit { putInt("daily_cap", value) }

    fun reserveSend(to: String, sendsThisEvent: Int): Decision = synchronized(AgentConfig::class.java) {
        val today = LocalDate.now().toString()
        val sendsToday = if (getString("sends_date") == today) prefs.getInt("sends_today", 0) else 0
        AgentPolicy.canSend(to, allowlist, sendsThisEvent, sendsToday, dailyCap).also {
            if (it == Decision.Allow) {
                prefs.edit(commit = true) {
                    putString("sends_date", today)
                    putInt("sends_today", sendsToday + 1)
                }
            }
        }
    }

    private fun getString(key: String) = prefs.getString(key, null).orEmpty()

    private fun putString(key: String, value: String) = prefs.edit { putString(key, value.trim()) }
}
