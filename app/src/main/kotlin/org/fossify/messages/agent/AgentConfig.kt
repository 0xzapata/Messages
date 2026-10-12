package org.fossify.messages.agent

import android.content.Context
import androidx.core.content.edit

class AgentConfig(context: Context) {
    private val prefs = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var allowlist: Set<String>
        get() = prefs.getString("allowlist", null).orEmpty().lines().filter { it.isNotBlank() }.toSet()
        set(value) = prefs.edit { putString("allowlist", value.joinToString("\n")) }
}
