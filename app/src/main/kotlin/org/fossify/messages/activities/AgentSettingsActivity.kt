package org.fossify.messages.activities

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.agent.AgentConfig
import org.fossify.messages.agent.DEFAULT_DAILY_CAP
import org.fossify.messages.agent.AgentEvent
import org.fossify.messages.agent.AgentWorker
import org.fossify.messages.databinding.ActivityAgentSettingsBinding
import org.fossify.messages.extensions.getThreadId

class AgentSettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivityAgentSettingsBinding::inflate)
    private val agentConfig by lazy { AgentConfig(this) }

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            agentConfig.skillsTreeUri = uri.toString()
            showFolder(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.agentScrollview))
        setupMaterialScrollListener(scrollingView = binding.agentScrollview, topAppBar = binding.agentAppbar)

        binding.apply {
            agentEnabled.isChecked = agentConfig.enabled
            agentEnabledHolder.setOnClickListener { agentEnabled.toggle() }
            agentBaseUrl.setText(agentConfig.baseUrl)
            agentClaudeKey.setText(agentConfig.claudeKey)
            agentModel.setText(agentConfig.model)
            agentEffort.setText(agentConfig.effort)
            agentJevKey.setText(agentConfig.jevKey)
            agentDailyCap.setText(agentConfig.dailyCap.toString())
            agentAllowlist.setText(agentConfig.allowlist.joinToString("\n"))
            agentConfig.skillsTreeUri.takeIf { it.isNotBlank() }?.let { showFolder(it.toUri()) }

            agentChooseSkills.setOnClickListener {
                try {
                    pickFolder.launch(null)
                } catch (_: ActivityNotFoundException) {
                    toast(org.fossify.commons.R.string.system_service_disabled)
                }
            }
            agentSave.setOnClickListener {
                save()
                toast(R.string.agent_saved)
            }
            agentDryTest.setOnClickListener { runDryTest() }
        }
        updateTextColors(binding.agentHolder)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.agentAppbar, NavigationIcon.Arrow)
    }

    private fun save() = binding.apply {
        agentConfig.enabled = agentEnabled.isChecked
        agentConfig.baseUrl = agentBaseUrl.text.toString()
        agentConfig.claudeKey = agentClaudeKey.text.toString()
        agentConfig.model = agentModel.text.toString()
        agentConfig.effort = agentEffort.text.toString()
        agentConfig.jevKey = agentJevKey.text.toString()
        agentConfig.dailyCap = agentDailyCap.text.toString().toIntOrNull() ?: DEFAULT_DAILY_CAP
        agentConfig.allowlist = agentAllowlist.text.toString().lines()
            .map { it.trim().normalizePhoneNumber() }
            .filter { it.isNotBlank() }
            .toSet()
        agentAllowlist.setText(agentConfig.allowlist.joinToString("\n"))
    }

    private fun runDryTest() {
        save()
        val address = agentConfig.allowlist.firstOrNull()
        if (address == null) {
            toast(R.string.agent_allowlist_empty)
            return
        }

        ensureBackgroundThread {
            val now = System.currentTimeMillis()
            val event = AgentEvent(
                address = address,
                body = "test: say hello in one word",
                threadId = getThreadId(address),
                subId = -1,
                timestampMs = now,
                dryRun = true
            )
            AgentWorker.enqueue(this, "agent-dry-$now", event)
            toast(R.string.agent_dry_test_started)
        }
    }

    private fun showFolder(uri: Uri) {
        val name = DocumentFile.fromTreeUri(this, uri)?.name ?: uri.toString()
        binding.agentChooseSkills.text = getString(R.string.agent_skills_folder, name)
    }
}
