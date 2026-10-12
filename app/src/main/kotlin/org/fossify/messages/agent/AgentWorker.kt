package org.fossify.messages.agent

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

data class AgentEvent(
    val address: String,
    val body: String,
    val timestampMs: Long
) {
    fun toData() = workDataOf("address" to address, "body" to body.take(MAX_BODY_CHARS), "timestampMs" to timestampMs)

    companion object {
        fun from(data: Data) = AgentEvent(
            address = data.getString("address").orEmpty(),
            body = data.getString("body").orEmpty(),
            timestampMs = data.getLong("timestampMs", 0L)
        )
    }
}

private const val MAX_BODY_CHARS = 3000

class AgentWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = Result.success()

    companion object {
        fun enqueue(context: Context, event: AgentEvent) {
            val request = OneTimeWorkRequestBuilder<AgentWorker>().setInputData(event.toData()).build()
            WorkManager.getInstance(context).enqueue(request).result.get()
        }
    }
}
