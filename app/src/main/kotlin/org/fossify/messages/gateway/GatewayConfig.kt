package org.fossify.messages.gateway

import android.content.Context
import androidx.core.content.edit
import java.net.URI

class GatewayConfig(context: Context) {
    private val prefs = context.getSharedPreferences("gateway_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    var gatewayUrl: String
        get() = prefs.getString("gateway_url", null).orEmpty()
        set(value) = prefs.edit { putString("gateway_url", value) }

    var deviceToken: String
        get() = prefs.getString("device_token", null).orEmpty()
        set(value) = prefs.edit { putString("device_token", value) }

    var allowlist: Set<String>
        get() = prefs.getString("allowlist", null).orEmpty().lines().filter { it.isNotBlank() }.toSet()
        set(value) = prefs.edit { putString("allowlist", value.joinToString("\n")) }
}

/** https only, with a host, and no query or fragment, because the worker appends the path. */
fun isGatewayUrl(url: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return url.startsWith("https://") && !uri.host.isNullOrEmpty() &&
        uri.rawQuery == null && uri.rawFragment == null
}

/** An empty allowlist sends every event. */
fun isAllowed(number: String, allowlist: Set<String>) = allowlist.isEmpty() || number in allowlist
