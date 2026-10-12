package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityGatewaySettingsBinding
import org.fossify.messages.gateway.GatewayConfig
import org.fossify.messages.gateway.GatewayEvent
import org.fossify.messages.gateway.GatewayWorker
import org.fossify.messages.gateway.isGatewayUrl

class GatewaySettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityGatewaySettingsBinding::inflate)
    private val gatewayConfig by lazy { GatewayConfig(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.gatewayScrollview))
        setupMaterialScrollListener(scrollingView = binding.gatewayScrollview, topAppBar = binding.gatewayAppbar)

        binding.apply {
            gatewayEnabled.isChecked = gatewayConfig.enabled
            gatewayEnabledHolder.setOnClickListener { gatewayEnabled.toggle() }
            gatewayUrl.setText(gatewayConfig.gatewayUrl)
            gatewayToken.setText(gatewayConfig.deviceToken)
            gatewayAllowlist.setText(gatewayConfig.allowlist.joinToString("\n"))
            gatewaySave.setOnClickListener {
                if (save()) toast(R.string.gateway_saved)
            }
            gatewayTest.setOnClickListener { sendTestEvent() }
        }
        updateTextColors(binding.gatewayHolder)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.gatewayAppbar, NavigationIcon.Arrow)
    }

    private fun save(): Boolean {
        val url = binding.gatewayUrl.text.toString().trim()
        if (!isGatewayUrl(url)) {
            toast(R.string.gateway_url_invalid)
            return false
        }
        gatewayConfig.enabled = binding.gatewayEnabled.isChecked
        gatewayConfig.gatewayUrl = url
        gatewayConfig.deviceToken = binding.gatewayToken.text.toString().trim()
        gatewayConfig.allowlist = binding.gatewayAllowlist.text.toString().lines()
            .map { it.trim().normalizePhoneNumber() }
            .filter { it.isNotBlank() }
            .toSet()
        binding.gatewayAllowlist.setText(gatewayConfig.allowlist.joinToString("\n"))
        return true
    }

    private fun sendTestEvent() {
        if (!save()) return
        val event = GatewayEvent(
            kind = "sms_received",
            occurredAtMs = System.currentTimeMillis(),
            number = "",
            displayName = "test",
            body = "test"
        )
        ensureBackgroundThread {
            GatewayWorker.enqueue(this, event)
            toast(R.string.gateway_test_queued)
        }
    }
}
