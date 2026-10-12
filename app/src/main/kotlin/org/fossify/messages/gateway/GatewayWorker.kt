package org.fossify.messages.gateway

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.fossify.commons.extensions.notificationManager
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.R
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_BAD_REQUEST
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.net.URL
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** One event of the gateway contract, section "POST /v1/events (device)". Call fields stay null for SMS. */
data class GatewayEvent(
    val kind: String,
    val occurredAtMs: Long,
    val number: String,
    val displayName: String,
    val body: String? = null,
    val direction: String? = null,
    val durationS: Long? = null,
    val missed: Boolean? = null,
    /** Row id of the SMS in the app database, 0 when unknown. Lets the worker read a long body in full. */
    val messageId: Long = 0,
    val id: String = UUID.randomUUID().toString()
) {
    // Fields, not the JSON, go into Data: JSON escapes can grow a body 6x, past the 10 KB Data limit.
    fun toData() = workDataOf(
        "id" to id, "kind" to kind, "occurred_at" to occurredAtMs, "number" to number.take(MAX_NUMBER_CHARS),
        "display_name" to displayName.take(MAX_NAME_CHARS), "body" to body?.take(MAX_BODY_CHARS),
        "direction" to direction, "duration_s" to durationS, "missed" to missed, "message_id" to messageId
    )

    companion object {
        fun from(data: Data) = data.keyValueMap.let {
            GatewayEvent(
                kind = it["kind"] as String,
                occurredAtMs = it["occurred_at"] as Long,
                number = it["number"] as String,
                displayName = it["display_name"] as String,
                body = it["body"] as String?,
                direction = it["direction"] as String?,
                durationS = it["duration_s"] as Long?,
                missed = it["missed"] as Boolean?,
                messageId = it["message_id"] as Long,
                id = it["id"] as String
            )
        }
    }
}

private const val CHANNEL_ID = "gateway"
private const val MAX_NUMBER_CHARS = 32
private const val MAX_NAME_CHARS = 120

// The Data copy of the body stops at 3000 chars (10 KB WorkManager Data limit). A longer body is read back
// from the app database by message id in the worker, up to the contract maximum.
private const val MAX_BODY_CHARS = 3000
private const val CONTRACT_BODY_CHARS = 8000
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000
private const val BACKOFF_SECONDS = 30L
private const val MAX_RETRIES = 10
private const val ENQUEUE_WAIT_SECONDS = 5L

fun eventJson(event: GatewayEvent): String = JSONObject()
    .put("id", event.id)
    .put("kind", event.kind)
    .put("occurred_at", Instant.ofEpochMilli(event.occurredAtMs).truncatedTo(ChronoUnit.SECONDS).toString())
    .put("number", event.number)
    .put("display_name", event.displayName)
    .putOpt("body", event.body)
    .putOpt("direction", event.direction)
    .putOpt("duration_s", event.durationS)
    .putOpt("missed", event.missed)
    .toString()

/** Posts one event. The id is fixed at enqueue time, so a retry resends the same id. */
class GatewayWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val config = GatewayConfig(applicationContext)
        val event = GatewayEvent.from(inputData).let { e ->
            if (e.body?.length == MAX_BODY_CHARS) e.copy(body = fullBody(e.messageId) ?: e.body) else e
        }
        // Settings can change while an event waits for the network: never post under old consent.
        if (!config.enabled || !isAllowed(event.number, config.allowlist)) return Result.success()
        val code = try {
            post(config.gatewayUrl, config.deviceToken, eventJson(event))
        } catch (_: IOException) {
            null
        }
        return when (code) {
            HTTP_CREATED, HTTP_OK -> Result.success()
            HTTP_UNAUTHORIZED, HTTP_BAD_REQUEST -> fail(code)
            else -> if (runAttemptCount >= MAX_RETRIES) fail(code) else Result.retry()
        }
    }

    /** The stored SMS, when it still exists. Null when the user deleted it before the worker ran. */
    private fun fullBody(messageId: Long): String? {
        if (messageId == 0L) return null
        return applicationContext.messagesDB.getMessageById(messageId)?.body?.take(CONTRACT_BODY_CHARS)
    }

    private fun post(gatewayUrl: String, token: String, json: String): Int {
        val connection = URL(gatewayUrl.trimEnd('/') + "/v1/events").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(json.toByteArray()) }
            return connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    /** The notification holds the status code only: never the body, the number, or the token. */
    private fun fail(code: Int?): Result {
        val context = applicationContext
        val title = context.getString(R.string.gateway)
        val text = code?.let { context.getString(R.string.gateway_failed_http, it) }
            ?: context.getString(R.string.gateway_failed_network)
        context.notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW)
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_messenger)
            .setContentTitle(title)
            .setContentText(text)
            .build()
        context.notificationManager.notify(id.hashCode(), notification)
        return Result.failure()
    }

    companion object {
        fun enqueue(context: Context, event: GatewayEvent) {
            val request = OneTimeWorkRequestBuilder<GatewayWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .setInputData(event.toData())
                .build()
            // Wait until the request is persisted: the SMS receiver process can die right after onReceive returns.
            // The wait is bounded because the receiver has a 10 s budget. The enqueue itself continues.
            val operation = WorkManager.getInstance(context).enqueue(request)
            try {
                operation.result.get(ENQUEUE_WAIT_SECONDS, TimeUnit.SECONDS)
            } catch (_: TimeoutException) {
                // ponytail: the event is lost if the process dies in this window; use a foreground service if seen
            }
        }
    }
}
