package com.example.data.network

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import com.example.data.model.ChannelBrandingInfo
import com.example.data.model.ChannelViewerStats
import com.example.data.model.CommentThreadItem
import com.example.data.model.ComparedVideoMetric
import com.example.data.model.CompetitorChannel
import com.example.data.model.LiveChatMessage
import com.example.data.model.RevenueDayPoint
import com.example.data.model.RevenueSnapshot
import com.example.data.model.StreamHealthReport
import com.example.data.model.VideoPerformanceDetail
import com.example.data.model.YouTubeAnalyticsSnapshot
import com.example.data.model.YouTubeChannel
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

// --- OAuth 2.0 & YouTube Data / Live Streaming / Analytics Models ---

@JsonClass(generateAdapter = true)
data class OAuthTokenResponse(
    val access_token: String? = null,
    val expires_in: Long? = null,
    val refresh_token: String? = null,
    val scope: String? = null,
    val token_type: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelsResponse(
    val items: List<YouTubeChannelItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelItem(
    val id: String,
    val snippet: ChannelSnippet? = null,
    val statistics: ChannelStatistics? = null
)

@JsonClass(generateAdapter = true)
data class ChannelSnippet(
    val title: String = "",
    val customUrl: String? = null,
    val thumbnails: ThumbnailMap? = null
)

@JsonClass(generateAdapter = true)
data class ThumbnailMap(
    val high: ThumbnailEntry? = null,
    val medium: ThumbnailEntry? = null,
    val default: ThumbnailEntry? = null
)

@JsonClass(generateAdapter = true)
data class ThumbnailEntry(val url: String? = null)

@JsonClass(generateAdapter = true)
data class ChannelStatistics(
    val subscriberCount: String? = null,
    val viewCount: String? = null,
    val videoCount: String? = null
)

@JsonClass(generateAdapter = true)
data class LiveBroadcastRequest(
    val snippet: BroadcastSnippet,
    val status: BroadcastStatus,
    val contentDetails: BroadcastContentDetails = BroadcastContentDetails()
)

@JsonClass(generateAdapter = true)
data class BroadcastSnippet(
    val title: String,
    val description: String,
    val scheduledStartTime: String
)

@JsonClass(generateAdapter = true)
data class BroadcastStatus(
    val privacyStatus: String,
    val selfDeclaredMadeForKids: Boolean = false
)

@JsonClass(generateAdapter = true)
data class BroadcastContentDetails(
    val enableAutoStart: Boolean = true,
    val enableAutoStop: Boolean = true,
    val latencyPreference: String = "normal"
)

@JsonClass(generateAdapter = true)
data class LiveBroadcastResponse(
    val id: String,
    val snippet: BroadcastSnippet? = null
)

@JsonClass(generateAdapter = true)
data class LiveStreamRequest(
    val snippet: StreamSnippet,
    val cdn: StreamCdn
)

@JsonClass(generateAdapter = true)
data class StreamSnippet(val title: String)

@JsonClass(generateAdapter = true)
data class StreamCdn(
    val frameRate: String = "30fps",
    val ingestionType: String = "rtmp",
    val resolution: String = "720p"
)

@JsonClass(generateAdapter = true)
data class LiveStreamResponse(
    val id: String,
    val cdn: LiveStreamCdnResponse? = null
)

@JsonClass(generateAdapter = true)
data class LiveStreamCdnResponse(
    val ingestionInfo: IngestionInfo? = null
)

@JsonClass(generateAdapter = true)
data class IngestionInfo(
    val ingestionAddress: String? = null,
    val streamName: String? = null,
    val rtmpsIngestionAddress: String? = null
)

interface GoogleOAuthApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun exchangeAuthCode(
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("grant_type") grantType: String = "authorization_code"
    ): OAuthTokenResponse

    @FormUrlEncoded
    @POST("token")
    suspend fun refreshAccessToken(
        @Field("refresh_token") refreshToken: String,
        @Field("client_id") clientId: String,
        @Field("grant_type") grantType: String = "refresh_token"
    ): OAuthTokenResponse
}

interface YouTubeDataApi {
    @GET("youtube/v3/channels")
    suspend fun getMyChannels(
        @Header("Authorization") bearer: String,
        @Query("part") part: String = "snippet,statistics",
        @Query("mine") mine: Boolean = true
    ): YouTubeChannelsResponse

    @POST("youtube/v3/liveBroadcasts")
    suspend fun createLiveBroadcast(
        @Header("Authorization") bearer: String,
        @Query("part") part: String = "snippet,status,contentDetails",
        @Body body: LiveBroadcastRequest
    ): LiveBroadcastResponse

    @POST("youtube/v3/liveStreams")
    suspend fun createLiveStream(
        @Header("Authorization") bearer: String,
        @Query("part") part: String = "snippet,cdn",
        @Body body: LiveStreamRequest
    ): LiveStreamResponse

    @POST("youtube/v3/liveBroadcasts/bind")
    suspend fun bindBroadcast(
        @Header("Authorization") bearer: String,
        @Query("id") broadcastId: String,
        @Query("part") part: String = "id,contentDetails",
        @Query("streamId") streamId: String
    ): LiveBroadcastResponse

    @POST("youtube/v3/liveBroadcasts/transition")
    suspend fun transitionBroadcast(
        @Header("Authorization") bearer: String,
        @Query("broadcastStatus") broadcastStatus: String,
        @Query("id") broadcastId: String,
        @Query("part") part: String = "id,status"
    ): LiveBroadcastResponse
}

object ApiQuotaUnits {
    const val READ_LIST = 1
    const val BROADCAST_WRITE = 50
    const val SEARCH_LIST = 100
    const val VIDEO_INSERT = 1600
    const val THUMBNAIL_SET = 50
    const val CAPTION_INSERT = 400
    const val VIDEO_UPDATE = 50
}

class YouTubeApiClient(
    private val onQuotaUnitsConsumed: ((Int) -> Unit)? = null
) {
    companion object {
        const val REDIRECT_URI = "com.aistudio.dreamiit28.hskxmw:/oauth2redirect"
        val YOUTUBE_SCOPES = listOf(
            "https://www.googleapis.com/auth/youtube",
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube.force-ssl",
            "https://www.googleapis.com/auth/yt-analytics.readonly",
            "https://www.googleapis.com/auth/yt-analytics-monetary.readonly"
        )
    }

    private fun trackQuota(units: Int) {
        if (units > 0) onQuotaUnitsConsumed?.invoke(units)
    }

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val oauthApi: GoogleOAuthApi = Retrofit.Builder()
        .baseUrl("https://oauth2.googleapis.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GoogleOAuthApi::class.java)

    private val dataApi: YouTubeDataApi = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttp)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(YouTubeDataApi::class.java)

    fun generatePkceVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun buildAuthorizationUrl(clientId: String, codeVerifier: String): Uri {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", YOUTUBE_SCOPES.joinToString(" "))
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("access_type", "offline")
            .appendQueryParameter("prompt", "consent")
            .build()
    }

    suspend fun exchangeCode(code: String, clientId: String, verifier: String): OAuthTokenResponse =
        oauthApi.exchangeAuthCode(code = code, clientId = clientId, redirectUri = REDIRECT_URI, codeVerifier = verifier)

    suspend fun refreshToken(refreshToken: String, clientId: String): OAuthTokenResponse =
        oauthApi.refreshAccessToken(refreshToken = refreshToken, clientId = clientId)

    suspend fun fetchAuthorizedChannels(accessToken: String, userId: String): List<YouTubeChannel> {
        trackQuota(ApiQuotaUnits.READ_LIST)
        val res = dataApi.getMyChannels("Bearer $accessToken")
        return res.items.mapIndexed { idx, item ->
            val thumb = item.snippet?.thumbnails?.high?.url
                ?: item.snippet?.thumbnails?.medium?.url
                ?: item.snippet?.thumbnails?.default?.url
            YouTubeChannel(
                userId = userId,
                channelId = item.id,
                title = item.snippet?.title.orEmpty().ifBlank { "YouTube Channel" },
                handle = item.snippet?.customUrl,
                thumbnailUrl = thumb,
                subscriberCount = item.statistics?.subscriberCount?.toLongOrNull() ?: 0L,
                viewCount = item.statistics?.viewCount?.toLongOrNull() ?: 0L,
                videoCount = item.statistics?.videoCount?.toLongOrNull() ?: 0L,
                activeChannel = idx == 0
            )
        }
    }

    suspend fun provisionLiveBroadcastAndStream(
        accessToken: String,
        title: String,
        description: String,
        scheduledStartIso: String,
        privacyStatus: String,
        autoEnd: Boolean,
        resolution: String,
        frameRate: String
    ): Triple<String, String, String?> {
        trackQuota(ApiQuotaUnits.BROADCAST_WRITE * 3)
        val bearer = "Bearer $accessToken"
        val broadcast = dataApi.createLiveBroadcast(
            bearer = bearer,
            body = LiveBroadcastRequest(
                snippet = BroadcastSnippet(
                    title = title,
                    description = description,
                    scheduledStartTime = scheduledStartIso
                ),
                status = BroadcastStatus(privacyStatus = privacyStatus.lowercase(Locale.US)),
                contentDetails = BroadcastContentDetails(
                    enableAutoStart = true,
                    enableAutoStop = autoEnd
                )
            )
        )
        val stream = dataApi.createLiveStream(
            bearer = bearer,
            body = LiveStreamRequest(
                snippet = StreamSnippet(title = "DREAMIIT28-$title".take(100)),
                cdn = StreamCdn(
                    frameRate = frameRate,
                    ingestionType = "rtmp",
                    resolution = resolution
                )
            )
        )
        dataApi.bindBroadcast(
            bearer = bearer,
            broadcastId = broadcast.id,
            streamId = stream.id
        )
        val ingestion = stream.cdn?.ingestionInfo
        val rtmpsUrl = ingestion?.rtmpsIngestionAddress?.let { addr ->
            val key = ingestion.streamName.orEmpty()
            if (key.isNotBlank()) "$addr/$key" else addr
        }
        return Triple(broadcast.id, stream.id, rtmpsUrl)
    }

    suspend fun transitionLiveBroadcast(
        accessToken: String,
        broadcastId: String,
        status: String
    ) {
        trackQuota(ApiQuotaUnits.BROADCAST_WRITE)
        dataApi.transitionBroadcast("Bearer $accessToken", status, broadcastId)
    }

    suspend fun verifyBroadcastLiveStatus(
        accessToken: String,
        broadcastId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            trackQuota(ApiQuotaUnits.READ_LIST)
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/liveBroadcasts?part=status&id=${URLEncoder.encode(broadcastId, "UTF-8")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("YouTube status HTTP ${resp.code}: $body"))
                }
                val item = JSONObject(body).optJSONArray("items")?.optJSONObject(0)
                    ?: return@withContext Result.failure(IllegalStateException("Broadcast $broadcastId not found on YouTube."))
                val lifeCycle = item.optJSONObject("status")?.optString("lifeCycleStatus", "unknown").orEmpty()
                Result.success(lifeCycle)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadCustomThumbnail(
        accessToken: String,
        videoId: String,
        jpegBytes: ByteArray
    ): Result<Unit> = withContext(Dispatchers.IO) {
        trackQuota(ApiQuotaUnits.THUMBNAIL_SET)
        try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/youtube/v3/thumbnails/set?videoId=$videoId")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("YouTube thumbnail upload HTTP ${resp.code}: ${resp.message}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadVideoResumable(
        accessToken: String,
        contentResolver: ContentResolver,
        videoUri: Uri,
        fileSizeBytes: Long,
        title: String,
        description: String,
        categoryId: String,
        privacyStatus: String,
        tags: List<String>,
        onProgress: (Float) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        trackQuota(ApiQuotaUnits.VIDEO_INSERT)
        try {
            val metadataJson = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("title", title.take(100))
                    put("description", description.take(5000))
                    put("categoryId", categoryId)
                    if (tags.isNotEmpty()) {
                        put("tags", JSONArray(tags.take(15)))
                    }
                })
                put("status", JSONObject().apply {
                    put("privacyStatus", privacyStatus.lowercase(Locale.US))
                    put("selfDeclaredMadeForKids", false)
                })
            }.toString()

            val initReq = Request.Builder()
                .url("https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status")
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("X-Upload-Content-Length", fileSizeBytes.toString())
                .addHeader("X-Upload-Content-Type", "video/*")
                .post(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val uploadSessionUrl = okHttp.newCall(initReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Resumable upload init failed: HTTP ${resp.code} ${resp.message}")
                    )
                }
                resp.header("Location")
                    ?: return@withContext Result.failure(IllegalStateException("Missing YouTube upload session Location header"))
            }

            val bytes = contentResolver.openInputStream(videoUri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Unable to read selected video stream"))
            onProgress(0.35f)

            val putReq = Request.Builder()
                .url(uploadSessionUrl)
                .put(bytes.toRequestBody("video/*".toMediaType()))
                .build()

            okHttp.newCall(putReq).execute().use { putResp ->
                val body = putResp.body?.string().orEmpty()
                if (!putResp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Video upload failed: HTTP ${putResp.code} $body")
                    )
                }
                onProgress(1f)
                val ytId = JSONObject(body).optString("id")
                Result.success(ytId)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V3.0 Feature #5: Upload Captions (.srt) to YouTube ---
    suspend fun uploadCaptionsSrt(
        accessToken: String,
        youtubeVideoId: String,
        languageCode: String,
        trackName: String,
        srtContent: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val snippetJson = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("videoId", youtubeVideoId)
                    put("language", languageCode)
                    put("name", trackName.take(80))
                    put("isDraft", false)
                })
            }.toString()

            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                    snippetJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                )
                .addFormDataPart(
                    "file",
                    "captions_$languageCode.srt",
                    srtContent.toRequestBody("application/x-subrip; charset=UTF-8".toMediaType())
                )
                .build()

            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/youtube/v3/captions?uploadType=multipart&part=snippet")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipart)
                .build()

            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val capId = JSONObject(body).optString("id", "uploaded")
                    Result.success(capId)
                } else {
                    Result.failure(IllegalStateException("Captions upload HTTP ${resp.code}: $body"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V3.0 Feature #13: Update YouTube Video Description (with Chapters) ---
    suspend fun updateYouTubeVideoDescription(
        accessToken: String,
        youtubeVideoId: String,
        newDescription: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val getReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/videos?part=snippet&id=${URLEncoder.encode(youtubeVideoId, "UTF-8")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val currentSnippet = okHttp.newCall(getReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Failed to fetch video snippet: HTTP ${resp.code}"))
                }
                val items = JSONObject(body).optJSONArray("items")
                if (items == null || items.length() == 0) {
                    return@withContext Result.failure(IllegalStateException("YouTube video $youtubeVideoId not found."))
                }
                items.getJSONObject(0).getJSONObject("snippet")
            }

            val updatedPayload = JSONObject().apply {
                put("id", youtubeVideoId)
                put("snippet", JSONObject().apply {
                    put("title", currentSnippet.optString("title"))
                    put("description", newDescription.take(5000))
                    put("categoryId", currentSnippet.optString("categoryId", "27"))
                    currentSnippet.optJSONArray("tags")?.let { put("tags", it) }
                })
            }.toString()

            val putReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/videos?part=snippet")
                .addHeader("Authorization", "Bearer $accessToken")
                .put(updatedPayload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            okHttp.newCall(putReq).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Update video HTTP ${resp.code}: ${resp.body?.string().orEmpty()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V3.0 Feature #7: Live Chat Monitor & Viewer Stats ---
    suspend fun fetchBroadcastChatAndViewers(
        accessToken: String,
        broadcastId: String
    ): Result<ChannelViewerStats> = withContext(Dispatchers.IO) {
        try {
            val bReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/liveBroadcasts?part=snippet&id=${URLEncoder.encode(broadcastId, "UTF-8")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            var liveChatId: String? = null
            okHttp.newCall(bReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val items = JSONObject(resp.body?.string().orEmpty()).optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        liveChatId = items.getJSONObject(0).optJSONObject("snippet")?.optString("liveChatId")?.takeIf { it.isNotBlank() }
                    }
                }
            }

            val vReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/videos?part=liveStreamingDetails&id=${URLEncoder.encode(broadcastId, "UTF-8")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            var viewers: Long? = null
            okHttp.newCall(vReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val items = JSONObject(resp.body?.string().orEmpty()).optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        val details = items.getJSONObject(0).optJSONObject("liveStreamingDetails")
                        val rawViewers = details?.optString("concurrentViewers")
                        viewers = rawViewers?.toLongOrNull()
                    }
                }
            }

            val status = when {
                liveChatId != null -> "Live Chat Connected"
                else -> "Broadcast has no active YouTube liveChatId yet"
            }
            Result.success(ChannelViewerStats(concurrentViewers = viewers, activeLiveChatId = liveChatId, statusText = status))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLiveChatMessages(
        accessToken: String,
        liveChatId: String,
        pageToken: String? = null
    ): Result<Pair<List<LiveChatMessage>, String?>> = withContext(Dispatchers.IO) {
        try {
            val tokenParam = if (!pageToken.isNullOrBlank()) "&pageToken=${URLEncoder.encode(pageToken, "UTF-8")}" else ""
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/liveChat/messages?liveChatId=${URLEncoder.encode(liveChatId, "UTF-8")}&part=snippet,authorDetails&maxResults=50$tokenParam")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Live Chat HTTP ${resp.code}: $body"))
                }
                val json = JSONObject(body)
                val nextToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
                val items = json.optJSONArray("items") ?: JSONArray()
                val msgs = (0 until items.length()).map { idx ->
                    val obj = items.getJSONObject(idx)
                    val snippet = obj.optJSONObject("snippet")
                    val author = obj.optJSONObject("authorDetails")
                    LiveChatMessage(
                        id = obj.optString("id"),
                        authorChannelId = author?.optString("channelId").orEmpty(),
                        authorName = author?.optString("displayName", "Viewer").orEmpty(),
                        authorAvatarUrl = author?.optString("profileImageUrl")?.takeIf { it.isNotBlank() },
                        messageText = snippet?.optString("displayMessage").orEmpty(),
                        publishedAt = snippet?.optString("publishedAt").orEmpty(),
                        isModerator = author?.optBoolean("isChatModerator") ?: false,
                        isOwner = author?.optBoolean("isChatOwner") ?: false
                    )
                }
                Result.success(msgs to nextToken)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendLiveChatMessage(
        accessToken: String,
        liveChatId: String,
        messageText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("liveChatId", liveChatId)
                    put("type", "textMessageEvent")
                    put("textMessageDetails", JSONObject().apply {
                        put("messageText", messageText.take(200))
                    })
                })
            }.toString()
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/liveChat/messages?part=snippet")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Send chat HTTP ${resp.code}: ${resp.body?.string().orEmpty()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteLiveChatMessage(accessToken: String, messageId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/liveChat/messages?id=${URLEncoder.encode(messageId, "UTF-8")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Delete chat HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun banLiveChatUser(accessToken: String, liveChatId: String, channelId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("liveChatId", liveChatId)
                    put("type", "temporary")
                    put("banDurationSeconds", 300)
                    put("bannedUserDetails", JSONObject().apply {
                        put("channelId", channelId)
                    })
                })
            }.toString()
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/liveChat/bans?part=snippet")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Timeout user HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V3.0 Feature #8: Comment Manager ---
    suspend fun fetchChannelComments(
        accessToken: String,
        channelId: String,
        searchTerms: String? = null
    ): Result<List<CommentThreadItem>> = withContext(Dispatchers.IO) {
        try {
            val searchParam = if (!searchTerms.isNullOrBlank()) {
                "&searchTerms=${URLEncoder.encode(searchTerms.trim(), "UTF-8")}"
            } else ""
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/commentThreads?part=snippet,replies&allThreadsRelatedToChannelId=${URLEncoder.encode(channelId, "UTF-8")}&maxResults=40&order=time$searchParam")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Comments API HTTP ${resp.code}: $body"))
                }
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                val list = (0 until items.length()).map { i ->
                    val thread = items.getJSONObject(i)
                    val tSnippet = thread.getJSONObject("snippet")
                    val topObj = tSnippet.getJSONObject("topLevelComment")
                    val cSnippet = topObj.getJSONObject("snippet")
                    val replyCount = tSnippet.optLong("totalReplyCount", 0L)
                    CommentThreadItem(
                        threadId = thread.optString("id"),
                        topCommentId = topObj.optString("id"),
                        videoId = cSnippet.optString("videoId"),
                        authorName = cSnippet.optString("authorDisplayName", "Viewer"),
                        authorAvatarUrl = cSnippet.optString("authorProfileImageUrl")?.takeIf { it.isNotBlank() },
                        text = cSnippet.optString("textDisplay"),
                        likeCount = cSnippet.optLong("likeCount", 0L),
                        replyCount = replyCount,
                        publishedAt = cSnippet.optString("publishedAt"),
                        updatedAt = cSnippet.optString("updatedAt"),
                        canReply = tSnippet.optBoolean("canReply", true),
                        isUnread = replyCount == 0L
                    )
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun replyToComment(
        accessToken: String,
        parentCommentId: String,
        replyText: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("snippet", JSONObject().apply {
                    put("parentId", parentCommentId)
                    put("textOriginal", replyText.take(2000))
                })
            }.toString()
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/comments?part=snippet")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(payload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Reply HTTP ${resp.code}: ${resp.body?.string().orEmpty()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteComment(accessToken: String, commentId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/comments?id=${URLEncoder.encode(commentId, "UTF-8")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .delete()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Delete comment HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moderateComment(accessToken: String, commentId: String, status: String = "heldForReview"): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/comments/setModerationStatus?id=${URLEncoder.encode(commentId, "UTF-8")}&moderationStatus=$status")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(ByteArray(0).toRequestBody(null))
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success(Unit)
                else Result.failure(IllegalStateException("Moderate comment HTTP ${resp.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V3.0 Feature #9: Channel Branding Manager ---
    suspend fun fetchChannelBranding(accessToken: String): Result<ChannelBrandingInfo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/channels?part=snippet,brandingSettings&mine=true")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Channel branding HTTP ${resp.code}: $body"))
                }
                val items = JSONObject(body).optJSONArray("items")
                if (items == null || items.length() == 0) {
                    return@withContext Result.failure(IllegalStateException("No YouTube channel returned."))
                }
                val ch = items.getJSONObject(0)
                val snippet = ch.optJSONObject("snippet")
                val branding = ch.optJSONObject("brandingSettings")
                val chBrand = branding?.optJSONObject("channel")
                val imgBrand = branding?.optJSONObject("image")
                val avatar = snippet?.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                Result.success(
                    ChannelBrandingInfo(
                        channelId = ch.optString("id"),
                        title = snippet?.optString("title").orEmpty(),
                        description = chBrand?.optString("description") ?: snippet?.optString("description").orEmpty(),
                        keywords = chBrand?.optString("keywords").orEmpty(),
                        profileImageUrl = avatar,
                        bannerImageUrl = imgBrand?.optString("bannerExternalUrl")?.takeIf { it.isNotBlank() },
                        statusMessage = "Loaded official YouTube branding settings."
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadBannerAndUpdateBranding(
        accessToken: String,
        channelId: String,
        description: String,
        keywords: String,
        bannerBytes: ByteArray?
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            var bannerExternalUrl: String? = null
            if (bannerBytes != null) {
                val bannerReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/youtube/v3/channelBanners/insert")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(bannerBytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()
                okHttp.newCall(bannerReq).execute().use { resp ->
                    val bBody = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        return@withContext Result.failure(IllegalStateException("Banner upload HTTP ${resp.code}: $bBody"))
                    }
                    bannerExternalUrl = JSONObject(bBody).optString("url").takeIf { it.isNotBlank() }
                }
            }

            val payload = JSONObject().apply {
                put("id", channelId)
                put("brandingSettings", JSONObject().apply {
                    put("channel", JSONObject().apply {
                        put("description", description.take(1000))
                        put("keywords", keywords.take(500))
                    })
                    if (!bannerExternalUrl.isNullOrBlank()) {
                        put("image", JSONObject().apply {
                            put("bannerExternalUrl", bannerExternalUrl)
                        })
                    }
                })
            }.toString()

            val putReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/channels?part=brandingSettings")
                .addHeader("Authorization", "Bearer $accessToken")
                .put(payload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            okHttp.newCall(putReq).execute().use { resp ->
                if (resp.isSuccessful) Result.success(bannerExternalUrl)
                else Result.failure(IllegalStateException("Branding update HTTP ${resp.code}: ${resp.body?.string().orEmpty()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V3.0 Feature #10: Video Comparison (Real Channel Videos + Watch Time) ---
    suspend fun fetchChannelVideosForComparison(accessToken: String): Result<List<ComparedVideoMetric>> = withContext(Dispatchers.IO) {
        try {
            val searchReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/search?forMine=true&type=video&part=snippet&maxResults=25&order=date")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val ids = mutableListOf<String>()
            okHttp.newCall(searchReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Channel videos HTTP ${resp.code}: $body"))
                }
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val vidId = items.getJSONObject(i).optJSONObject("id")?.optString("videoId")
                    if (!vidId.isNullOrBlank()) ids.add(vidId)
                }
            }
            if (ids.isEmpty()) return@withContext Result.success(emptyList())

            val statsReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=${ids.joinToString(",")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val metrics = mutableListOf<ComparedVideoMetric>()
            okHttp.newCall(statsReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Video stats HTTP ${resp.code}"))
                }
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val vidId = obj.optString("id")
                    val snippet = obj.optJSONObject("snippet")
                    val stats = obj.optJSONObject("statistics")
                    val thumb = snippet?.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url")
                    val watchTime = fetchSingleVideoWatchTime(accessToken, vidId)
                    metrics.add(
                        ComparedVideoMetric(
                            videoId = vidId,
                            title = snippet?.optString("title", "Video").orEmpty(),
                            thumbnailUrl = thumb,
                            publishedAt = snippet?.optString("publishedAt").orEmpty(),
                            viewCount = stats?.optString("viewCount")?.toLongOrNull() ?: 0L,
                            likeCount = stats?.optString("likeCount")?.toLongOrNull() ?: 0L,
                            commentCount = stats?.optString("commentCount")?.toLongOrNull() ?: 0L,
                            watchTimeMinutes = watchTime
                        )
                    )
                }
            }
            Result.success(metrics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchSingleVideoWatchTime(accessToken: String, videoId: String): Long? {
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val endDate = fmt.format(Date())
            val req = Request.Builder()
                .url("https://youtubeanalytics.googleapis.com/v2/reports?ids=channel==MINE&startDate=2020-01-01&endDate=$endDate&metrics=estimatedMinutesWatched&filters=video==$videoId")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val rows = JSONObject(resp.body?.string().orEmpty()).optJSONArray("rows")
                if (rows != null && rows.length() > 0) rows.getJSONArray(0).optLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun fetchAnalyticsReport(
        accessToken: String,
        days: Int
    ): YouTubeAnalyticsSnapshot = withContext(Dispatchers.IO) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val endDate = fmt.format(Date())
            val startDate = fmt.format(Date(System.currentTimeMillis() - days * 86_400_000L))
            val url = "https://youtubeanalytics.googleapis.com/v2/reports" +
                "?ids=channel==MINE" +
                "&startDate=$startDate" +
                "&endDate=$endDate" +
                "&metrics=views,estimatedMinutesWatched,averageViewDuration,likes,comments,subscribersGained"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext YouTubeAnalyticsSnapshot(
                        authorized = false,
                        dateRangeDays = days,
                        statusMessage = "YouTube Analytics API returned HTTP ${resp.code}. Ensure yt-analytics.readonly scope is authorized."
                    )
                }
                val json = JSONObject(body)
                val rows = json.optJSONArray("rows")
                if (rows == null || rows.length() == 0) {
                    return@withContext YouTubeAnalyticsSnapshot(
                        authorized = true,
                        dateRangeDays = days,
                        views = 0,
                        watchTimeMinutes = 0,
                        averageViewDurationSec = 0,
                        likes = 0,
                        comments = 0,
                        subscribersGained = 0,
                        statusMessage = "Authorized • No activity recorded in last $days days."
                    )
                }
                val row = rows.getJSONArray(0)
                YouTubeAnalyticsSnapshot(
                    authorized = true,
                    dateRangeDays = days,
                    views = row.optLong(0),
                    watchTimeMinutes = row.optLong(1),
                    averageViewDurationSec = row.optLong(2),
                    likes = row.optLong(3),
                    comments = row.optLong(4),
                    subscribersGained = row.optLong(5),
                    statusMessage = "Live YouTube Analytics API ($startDate → $endDate)"
                )
            }
        } catch (e: Exception) {
            YouTubeAnalyticsSnapshot(
                authorized = false,
                dateRangeDays = days,
                statusMessage = e.localizedMessage ?: "Failed to query YouTube Analytics API."
            )
        }
    }

    // --- V3.0 Feature #15: YouTube Revenue Dashboard ---
    suspend fun fetchRevenueReport(
        accessToken: String,
        days: Int
    ): RevenueSnapshot = withContext(Dispatchers.IO) {
        try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val endDate = fmt.format(Date())
            val startDate = fmt.format(Date(System.currentTimeMillis() - days * 86_400_000L))
            val url = "https://youtubeanalytics.googleapis.com/v2/reports" +
                "?ids=channel==MINE" +
                "&startDate=$startDate" +
                "&endDate=$endDate" +
                "&dimensions=day" +
                "&sort=day" +
                "&metrics=estimatedRevenue,estimatedAdRevenue,estimatedRedPartnerRevenue,cpm,monetizedPlaybacks"
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext RevenueSnapshot(
                        authorized = false,
                        dateRangeDays = days,
                        statusMessage = "Monetary Analytics unavailable (HTTP ${resp.code}). Channel must be in YouTube Partner Program and authorize yt-analytics-monetary.readonly scope."
                    )
                }
                val rows = JSONObject(body).optJSONArray("rows")
                if (rows == null || rows.length() == 0) {
                    return@withContext RevenueSnapshot(
                        authorized = true,
                        dateRangeDays = days,
                        estimatedRevenueUsd = 0.0,
                        adRevenueUsd = 0.0,
                        redPartnerRevenueUsd = 0.0,
                        cpmUsd = 0.0,
                        monetizedPlaybacks = 0L,
                        dailyPoints = emptyList(),
                        statusMessage = "Authorized • $0.00 monetized activity in last $days days."
                    )
                }
                var totalRev = 0.0
                var totalAd = 0.0
                var totalRed = 0.0
                var totalPlaybacks = 0L
                var cpmSum = 0.0
                val points = mutableListOf<RevenueDayPoint>()
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONArray(i)
                    val date = r.optString(0)
                    val rev = r.optDouble(1, 0.0)
                    val ad = r.optDouble(2, 0.0)
                    val red = r.optDouble(3, 0.0)
                    val cpm = r.optDouble(4, 0.0)
                    val mp = r.optLong(5, 0L)
                    totalRev += rev
                    totalAd += ad
                    totalRed += red
                    cpmSum += cpm
                    totalPlaybacks += mp
                    points.add(RevenueDayPoint(date, rev, ad, mp))
                }
                RevenueSnapshot(
                    authorized = true,
                    dateRangeDays = days,
                    estimatedRevenueUsd = totalRev,
                    adRevenueUsd = totalAd,
                    redPartnerRevenueUsd = totalRed,
                    cpmUsd = if (rows.length() > 0) cpmSum / rows.length() else 0.0,
                    monetizedPlaybacks = totalPlaybacks,
                    dailyPoints = points,
                    statusMessage = "Verified YouTube Monetary API ($startDate → $endDate)"
                )
            }
        } catch (e: Exception) {
            RevenueSnapshot(
                authorized = false,
                dateRangeDays = days,
                statusMessage = e.localizedMessage ?: "Failed to query YouTube Monetary Analytics."
            )
        }
    }

    // --- V4.1 Module 1: Public Competitor Channel Tracker ---
    suspend fun fetchPublicCompetitorChannel(
        accessToken: String?,
        rawQuery: String,
        userId: String
    ): Result<CompetitorChannel> = withContext(Dispatchers.IO) {
        if (accessToken.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Connect YouTube OAuth 2.0 in Settings to query the official YouTube Data API.")
            )
        }
        val cleaned = rawQuery.trim()
            .removePrefix("https://www.youtube.com/")
            .removePrefix("https://youtube.com/")
            .removePrefix("http://youtube.com/")
            .trim('/')
        if (cleaned.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Enter a @handle or UC... Channel ID."))
        }

        val handleCandidate = when {
            cleaned.startsWith("@") -> cleaned.substringBefore("/")
            cleaned.startsWith("channel/") -> ""
            !cleaned.startsWith("UC") -> "@${cleaned.substringBefore("/")}"
            else -> ""
        }
        val channelIdCandidate = when {
            cleaned.startsWith("channel/") -> cleaned.removePrefix("channel/").substringBefore("/")
            cleaned.startsWith("UC") -> cleaned.substringBefore("/")
            else -> ""
        }

        val url = if (channelIdCandidate.isNotBlank()) {
            "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&id=${URLEncoder.encode(channelIdCandidate, "UTF-8")}"
        } else {
            "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&forHandle=${URLEncoder.encode(handleCandidate, "UTF-8")}"
        }

        trackQuota(ApiQuotaUnits.READ_LIST)
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()
            okHttp.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("YouTube Channels API HTTP ${resp.code}: $body")
                    )
                }
                val items = JSONObject(body).optJSONArray("items")
                if (items == null || items.length() == 0) {
                    return@withContext Result.failure(
                        IllegalStateException("No public YouTube channel found for '$rawQuery'.")
                    )
                }
                val item = items.getJSONObject(0)
                val snippet = item.optJSONObject("snippet")
                val stats = item.optJSONObject("statistics")
                val thumb = snippet?.optJSONObject("thumbnails")?.let { t ->
                    t.optJSONObject("high")?.optString("url")
                        ?: t.optJSONObject("medium")?.optString("url")
                        ?: t.optJSONObject("default")?.optString("url")
                }
                Result.success(
                    CompetitorChannel(
                        userId = userId,
                        channelId = item.optString("id"),
                        title = snippet?.optString("title").orEmpty().ifBlank { rawQuery },
                        handle = snippet?.optString("customUrl")?.takeIf { it.isNotBlank() }
                            ?: handleCandidate.takeIf { it.isNotBlank() },
                        thumbnailUrl = thumb,
                        subscriberCount = stats?.optString("subscriberCount")?.toLongOrNull() ?: 0L,
                        viewCount = stats?.optString("viewCount")?.toLongOrNull() ?: 0L,
                        videoCount = stats?.optString("videoCount")?.toLongOrNull() ?: 0L,
                        lastSyncedEpochMs = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V4.1 Module 1: Detailed Video Performance & YouTube Policy Report ---
    suspend fun fetchDetailedVideosAndPolicies(
        accessToken: String
    ): Result<List<VideoPerformanceDetail>> = withContext(Dispatchers.IO) {
        try {
            trackQuota(ApiQuotaUnits.SEARCH_LIST)
            val searchReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/search?forMine=true&type=video&part=snippet&maxResults=20&order=date")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val ids = mutableListOf<String>()
            okHttp.newCall(searchReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Video search HTTP ${resp.code}: $body"))
                }
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val vidId = items.getJSONObject(i).optJSONObject("id")?.optString("videoId")
                    if (!vidId.isNullOrBlank()) ids.add(vidId)
                }
            }
            if (ids.isEmpty()) return@withContext Result.success(emptyList())

            trackQuota(ApiQuotaUnits.READ_LIST)
            val statsReq = Request.Builder()
                .url("https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics,status,contentDetails&id=${ids.joinToString(",")}")
                .addHeader("Authorization", "Bearer $accessToken")
                .get()
                .build()

            val details = mutableListOf<VideoPerformanceDetail>()
            okHttp.newCall(statsReq).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Video details HTTP ${resp.code}: $body"))
                }
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                for (i in 0 until items.length()) {
                    val obj = items.getJSONObject(i)
                    val vidId = obj.optString("id")
                    val snippet = obj.optJSONObject("snippet")
                    val stats = obj.optJSONObject("statistics")
                    val statusObj = obj.optJSONObject("status")
                    val contentDetails = obj.optJSONObject("contentDetails")

                    val views = stats?.optString("viewCount")?.toLongOrNull() ?: 0L
                    val likes = stats?.optString("likeCount")?.toLongOrNull() ?: 0L
                    val comments = stats?.optString("commentCount")?.toLongOrNull() ?: 0L
                    val engagement = if (views > 0L) {
                        ((likes + comments).toDouble() / views.toDouble()) * 100.0
                    } else 0.0

                    val uploadStatus = statusObj?.optString("uploadStatus", "processed").orEmpty()
                    val rejectionReason = statusObj?.optString("rejectionReason")?.takeIf { it.isNotBlank() }
                    val privacy = statusObj?.optString("privacyStatus", "public").orEmpty()
                    val madeForKids = statusObj?.optBoolean("madeForKids", false) ?: false
                    val licensedContent = contentDetails?.optBoolean("licensedContent", false) ?: false
                    val license = statusObj?.optString("license", "youtube").orEmpty()
                    val thumb = snippet?.optJSONObject("thumbnails")?.optJSONObject("medium")?.optString("url")
                    val watchTime = fetchSingleVideoWatchTime(accessToken, vidId)

                    val alerts = buildList {
                        if (!rejectionReason.isNullOrBlank()) {
                            add("Rejection: $rejectionReason")
                        }
                        if (uploadStatus.equals("rejected", true) || uploadStatus.equals("failed", true)) {
                            add("Upload status is '$uploadStatus'. Check YouTube Studio.")
                        }
                        if (contentDetails?.has("regionRestriction") == true) {
                            add("Region restriction detected on this video.")
                        }
                        if (licensedContent) {
                            add("Marked as licensed/Content ID claimed content by YouTube.")
                        }
                        if (madeForKids) {
                            add("Made for Kids: personalized ads & comments disabled per COPPA.")
                        }
                    }

                    details.add(
                        VideoPerformanceDetail(
                            videoId = vidId,
                            title = snippet?.optString("title", "Video").orEmpty(),
                            publishedAt = snippet?.optString("publishedAt").orEmpty(),
                            thumbnailUrl = thumb,
                            privacyStatus = privacy.uppercase(Locale.US),
                            uploadStatus = uploadStatus,
                            madeForKids = madeForKids,
                            license = license,
                            rejectionReason = rejectionReason,
                            viewCount = views,
                            likeCount = likes,
                            commentCount = comments,
                            engagementRatePercent = engagement,
                            watchTimeMinutes = watchTime,
                            policyAlerts = alerts
                        )
                    )
                }
            }
            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- V4.1 Module 2: Live Broadcast & Ingest Health Monitor ---
    suspend fun fetchLiveStreamHealth(
        accessToken: String,
        broadcastId: String?,
        streamId: String?
    ): Result<StreamHealthReport> = withContext(Dispatchers.IO) {
        if (broadcastId.isNullOrBlank() && streamId.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("No YouTube broadcastId or streamId bound yet."))
        }
        try {
            var lifeCycle = "unknown"
            var healthStatus = "noData"
            val issues = mutableListOf<String>()

            if (!broadcastId.isNullOrBlank()) {
                trackQuota(ApiQuotaUnits.READ_LIST)
                val bReq = Request.Builder()
                    .url("https://www.googleapis.com/youtube/v3/liveBroadcasts?part=status&id=${URLEncoder.encode(broadcastId, "UTF-8")}")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                okHttp.newCall(bReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val item = JSONObject(resp.body?.string().orEmpty())
                            .optJSONArray("items")?.optJSONObject(0)
                        lifeCycle = item?.optJSONObject("status")?.optString("lifeCycleStatus", "unknown").orEmpty()
                    }
                }
            }

            if (!streamId.isNullOrBlank()) {
                trackQuota(ApiQuotaUnits.READ_LIST)
                val sReq = Request.Builder()
                    .url("https://www.googleapis.com/youtube/v3/liveStreams?part=status&id=${URLEncoder.encode(streamId, "UTF-8")}")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()
                okHttp.newCall(sReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val item = JSONObject(resp.body?.string().orEmpty())
                            .optJSONArray("items")?.optJSONObject(0)
                        val statusObj = item?.optJSONObject("status")
                        val hs = statusObj?.optJSONObject("healthStatus")
                        healthStatus = hs?.optString("status", statusObj?.optString("streamStatus") ?: "noData").orEmpty()
                        val cfgArr = hs?.optJSONArray("configurationIssues")
                        if (cfgArr != null) {
                            for (i in 0 until cfgArr.length()) {
                                val issueObj = cfgArr.optJSONObject(i) ?: continue
                                val sev = issueObj.optString("severity", "info")
                                val desc = issueObj.optString("description", issueObj.optString("type"))
                                if (desc.isNotBlank()) issues.add("[$sev] $desc")
                            }
                        }
                    }
                }
            }

            Result.success(
                StreamHealthReport(
                    broadcastId = broadcastId.orEmpty(),
                    lifeCycleStatus = lifeCycle,
                    healthStatus = healthStatus,
                    configIssues = issues,
                    checkedEpochMs = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
