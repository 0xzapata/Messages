package org.fossify.messages.activities

import android.os.Bundle
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.R
import org.fossify.messages.agent.AgentConfig
import org.fossify.messages.databinding.ActivityAgentSettingsBinding

class AgentSettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityAgentSettingsBinding::inflate)
    private val agentConfig by lazy { AgentConfig(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.agentScrollview))
        setupMaterialScrollListener(scrollingView = binding.agentScrollview, topAppBar = binding.agentAppbar)

        binding.apply {
            agentEnabled.isChecked = agentConfig.enabled
            agentEnabledHolder.setOnClickListener { agentEnabled.toggle() }
            agentAllowlist.setText(agentConfig.allowlist.joinToString("\n"))
            agentSave.setOnClickListener {
                save()
                toast(R.string.agent_saved)
            }
        }
        updateTextColors(binding.agentHolder)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.agentAppbar, NavigationIcon.Arrow)
    }

    private fun save() = binding.apply {
        agentConfig.enabled = agentEnabled.isChecked
        agentConfig.allowlist = agentAllowlist.text.toString().lines()
            .map { it.trim().normalizePhoneNumber() }
            .filter { it.isNotBlank() }
            .toSet()
        agentAllowlist.setText(agentConfig.allowlist.joinToString("\n"))
    }
}
