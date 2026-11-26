package com.example.data.network

import com.example.data.model.BillingLineItem
import com.example.data.model.ConnectedBillingReport
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class WorkerHealthResponse(
    val reachable: Boolean = false,
    val status: String = "NOT_CONFIGURED",
    val cpuPercent: Double = 0.0,
    val memoryMb: Int = 0,
    val networkMbps: Double = 0.0,
    val droppedFrames: Int = 0,
    val activeStreams: Int = 0,
    val ffmpegAvailable: Boolean = false,
    val latencyMs: Long = 0L,
    val uptimeSec: Long = 0L,
    val activeStreamIds: List<String> = emptyList(),
    val recentErrors: List<String> = emptyList(),
    val lastCheckedEpochMs: Long = 0L,
    val message: String = "Configure Cloud Worker URL in Settings to connect your 24/7 FFmpeg RTMPS server."
)

data class WorkerStreamStatus(
    val scheduleId: String = "",
    val active: Boolean = false,
    val paused: Boolean = false,
    val profileId: String = "720p30_4M",
    val maxDurationMinutes: Int = 60,
    val repeatPlayback: Boolean = false,
    val elapsedSec: Long = 0L,
    val remainingSec: Long = 0L,
    val lastSafePositionSec: Long = 0L,
    val currentBitrateKbps: Int = 0,
    val droppedFrames: Int = 0,
    val retryAttempt: Int = 0,
    val recoveryState: String = "IDLE"
)

class CloudWorkerClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun Request.Builder.withOptionalBearer(token: String?): Request.Builder {
        val clean = token?.trim()
        if (!clean.isNullOrBlank()) {
            addHeader("Authorization", "Bearer $clean")
        }
        return this
    }

    suspend fun fetchHealth(baseUrl: String?): WorkerHealthResponse = withContext(Dispatchers.IO) {
        val clean = baseUrl?.trim()?.trimEnd('/')
        if (clean.isNullOrBlank()) {
            return@withContext WorkerHealthResponse(lastCheckedEpochMs = System.currentTimeMillis())
        }
        val startedMs = System.currentTimeMillis()
        try {
            val req = Request.Builder().url("$clean/api/health").get().build()
            client.newCall(req).execute().use { resp ->
                val latency = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext WorkerHealthResponse(
                        reachable = false,
                        status = "HTTP_${resp.code}",
                        latencyMs = latency,
                        lastCheckedEpochMs = System.currentTimeMillis(),
                        message = "Worker returned HTTP ${resp.code}"
                    )
                }
                val json = JSONObject(body)
                val idsArr = json.optJSONArray("activeStreamIds") ?: JSONArray()
                val activeIds = (0 until idsArr.length()).mapNotNull { i ->
                    idsArr.optString(i).takeIf { it.isNotBlank() }
                }
                val errArr = json.optJSONArray("recentErrors") ?: JSONArray()
                val errors = (0 until errArr.length()).mapNotNull { i ->
                    val item = errArr.opt(i)
                    when (item) {
                        is JSONObject -> {
                            val ts = item.optString("timestamp")
                            val msg = item.optString("message", item.toString())
                            if (ts.isNotBlank()) "[$ts] $msg" else msg
                        }
                        else -> item?.toString()?.takeIf { it.isNotBlank() }
                    }
                }
                WorkerHealthResponse(
                    reachable = true,
                    status = json.optString("status", "ONLINE"),
                    cpuPercent = json.optDouble("cpuPercent", 0.0),
                    memoryMb = json.optInt("memoryMb", 0),
                    networkMbps = json.optDouble("networkMbps", 0.0),
                    droppedFrames = json.optInt("droppedFrames", 0),
                    activeStreams = json.optInt("activeStreams", activeIds.size),
                    ffmpegAvailable = json.optBoolean("ffmpegAvailable", true),
                    latencyMs = latency,
                    uptimeSec = json.optLong("uptimeSec", 0L),
                    activeStreamIds = activeIds,
                    recentErrors = errors,
                    lastCheckedEpochMs = System.currentTimeMillis(),
                    message = "Connected to DREAMIIT28 Cloud Worker (${latency}ms)"
                )
            }
        } catch (e: Exception) {
            WorkerHealthResponse(
                reachable = false,
                status = "UNREACHABLE",
                latencyMs = (System.currentTimeMillis() - startedMs).coerceAtLeast(0L),
                lastCheckedEpochMs = System.currentTimeMillis(),
                message = e.localizedMessage ?: "Worker unreachable"
            )
        }
    }

    suspend fun fetchStreamStatus(
        baseUrl: String?,
        scheduleId: String,
        bearerToken: String? = null
    ): Result<WorkerStreamStatus> = withContext(Dispatchers.IO) {
        val clean = baseUrl?.trim()?.trimEnd('/')
        if (clean.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Cloud Worker URL is not configured."))
        }
        try {
            val req = Request.Builder()
                .url("$clean/api/streams/$scheduleId/status")
                .withOptionalBearer(bearerToken)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Worker status HTTP ${resp.code}: $body"))
                }
                Result.success(parseStreamStatusString(body))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pauseStream(
        baseUrl: String?,
        scheduleId: String,
        bearerToken: String? = null
    ): Result<String> = dispatchCommand(baseUrl, "/api/streams/$scheduleId/pause", JSONObject(), bearerToken)

    suspend fun resumeStream(
        baseUrl: String?,
        scheduleId: String,
        bearerToken: String? = null
    ): Result<String> = dispatchCommand(baseUrl, "/api/streams/$scheduleId/resume", JSONObject(), bearerToken)

    suspend fun fetchBilling(
        workerBaseUrl: String?,
        customBillingUrl: String?
    ): ConnectedBillingReport = withContext(Dispatchers.IO) {
        val targetUrl = customBillingUrl?.trim()?.takeIf { it.isNotBlank() }
            ?: workerBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }?.let { "$it/api/billing" }

        if (targetUrl.isNullOrBlank()) {
            return@withContext ConnectedBillingReport(
                connected = false,
                source = "Unconfigured",
                statusMessage = "Billing API Not Connected. Configure a Connected Billing JSON URL or Cloud Worker /api/billing endpoint in Settings."
            )
        }

        try {
            val req = Request.Builder().url(targetUrl).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ConnectedBillingReport(
                        connected = false,
                        source = targetUrl,
                        statusMessage = "Billing endpoint returned HTTP ${resp.code}. Verify connected billing export."
                    )
                }
                parseBillingJsonString(body, targetUrl)
            }
        } catch (e: Exception) {
            ConnectedBillingReport(
                connected = false,
                source = targetUrl,
                statusMessage = "Billing feed unreachable: ${e.localizedMessage ?: "Connection failed"}"
            )
        }
    }

    suspend fun dispatchCommand(
        baseUrl: String?,
        endpoint: String,
        payload: JSONObject,
        bearerToken: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val clean = baseUrl?.trim()?.trimEnd('/')
        if (clean.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Cloud Streaming Worker URL is not configured in Settings.")
            )
        }
        try {
            val req = Request.Builder()
                .url("$clean$endpoint")
                .withOptionalBearer(bearerToken)
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) Result.success(body)
                else Result.failure(IllegalStateException("Worker HTTP ${resp.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val genericMapAdapter by lazy {
            val moshi = Moshi.Builder().build()
            val mapType = Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Any::class.java
            )
            moshi.adapter<Map<String, Any?>>(mapType)
        }

        fun parseStreamStatusString(rawJson: String): WorkerStreamStatus {
            val root = runCatching { genericMapAdapter.fromJson(rawJson) }.getOrNull()
                ?: return WorkerStreamStatus()
            return WorkerStreamStatus(
                scheduleId = (root["scheduleId"] as? String).orEmpty(),
                active = root["active"] as? Boolean ?: false,
                paused = root["paused"] as? Boolean ?: false,
                profileId = (root["profileId"] as? String).orEmpty().ifBlank { "720p30_4M" },
                maxDurationMinutes = (root["maxDurationMinutes"] as? Number)?.toInt() ?: 60,
                repeatPlayback = root["repeatPlayback"] as? Boolean ?: false,
                elapsedSec = (root["elapsedSec"] as? Number)?.toLong() ?: 0L,
                remainingSec = (root["remainingSec"] as? Number)?.toLong() ?: 0L,
                lastSafePositionSec = (root["lastSafePositionSec"] as? Number)?.toLong() ?: 0L,
                currentBitrateKbps = (root["currentBitrateKbps"] as? Number)?.toInt() ?: 0,
                droppedFrames = (root["droppedFrames"] as? Number)?.toInt() ?: 0,
                retryAttempt = (root["retryAttempt"] as? Number)?.toInt() ?: 0,
                recoveryState = (root["recoveryState"] as? String).orEmpty().ifBlank { "IDLE" }
            )
        }

        fun parseBillingJson(json: JSONObject, source: String): ConnectedBillingReport =
            parseBillingJsonString(json.toString(), source)

        fun parseBillingJsonString(rawJson: String, source: String): ConnectedBillingReport {
            val root = runCatching { genericMapAdapter.fromJson(rawJson) }.getOrNull()
                ?: return ConnectedBillingReport(
                    connected = false,
                    source = source,
                    statusMessage = "Invalid billing JSON payload."
                )
            val connected = root["connected"] as? Boolean ?: false
            val resolvedSource = (root["source"] as? String)?.takeIf { it.isNotBlank() } ?: source
            if (!connected) {
                val msg = (root["message"] as? String)?.takeIf { it.isNotBlank() }
                    ?: "Billing API Not Connected on server. Configure GCP_BILLING_JSON_URL or CLOUD_MONTHLY_EXPENSE_USD."
                return ConnectedBillingReport(
                    connected = false,
                    source = resolvedSource,
                    statusMessage = msg
                )
            }
            val rawItems = root["lineItems"] as? List<*> ?: emptyList<Any>()
            val items = rawItems.mapNotNull { entry ->
                val map = entry as? Map<*, *> ?: return@mapNotNull null
                val label = (map["label"] as? String)?.trim().orEmpty()
                if (label.isBlank()) null
                else {
                    val amount = (map["amountUsd"] as? Number)?.toDouble() ?: 0.0
                    BillingLineItem(label = label, amountUsd = amount)
                }
            }
            val totalUsd = (root["totalMonthlyUsd"] as? Number)?.toDouble()
            return ConnectedBillingReport(
                connected = true,
                currency = (root["currency"] as? String)?.takeIf { it.isNotBlank() } ?: "USD",
                totalMonthlyUsd = totalUsd,
                billingPeriod = (root["billingPeriod"] as? String)?.takeIf { it.isNotBlank() },
                source = resolvedSource,
                lineItems = items,
                statusMessage = (root["message"] as? String)?.takeIf { it.isNotBlank() }
                    ?: "Live connected billing feed verified."
            )
        }
    }
}
