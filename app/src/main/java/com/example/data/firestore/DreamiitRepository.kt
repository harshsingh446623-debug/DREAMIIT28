package com.example.data.firestore

import android.content.Context
import com.example.R
import com.example.data.model.ActivityLog
import com.example.data.model.BackupRecord
import com.example.data.model.CloudVideo
import com.example.data.model.CompetitorChannel
import com.example.data.model.CopyrightRiskReport
import com.example.data.model.LivePlaylist
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleRecurrence
import com.example.data.model.ScheduleStatus
import com.example.data.model.SecurityAuditLog
import com.example.data.model.SmartNotification
import com.example.data.model.TeamMember
import com.example.data.model.UploadSchedule
import com.example.data.model.UserSession
import com.example.data.model.UserSettings
import com.example.data.model.WorkerJob
import com.example.data.model.YouTubeChannel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.abs

class DreamiitRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    private fun <T> safeCollectionFlow(
        uid: String,
        subcollection: String,
        queryBuilder: (Query) -> Query = { it },
        mapper: (List<DocumentSnapshot>) -> List<T>
    ): Flow<List<T>> = flow {
        val currentUid = requireUserId()
        check(currentUid == uid) { "User session changed." }
        val path = "users/$uid/$subcollection"
        val query = queryBuilder(userDoc(uid).collection(subcollection))
        emitAll(
            query.snapshots()
                .map { snapshot -> mapper(snapshot.documents) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeSettings(uid: String): Flow<UserSettings?> = flow {
        val currentUid = requireUserId()
        check(currentUid == uid) { "User session changed." }
        val path = "users/$uid"
        emitAll(
            userDoc(uid).snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeChannels(uid: String): Flow<List<YouTubeChannel>> =
        safeCollectionFlow(uid, "channels") { docs ->
            docs.mapNotNull {
                it.toObject(YouTubeChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeVideos(uid: String): Flow<List<CloudVideo>> =
        safeCollectionFlow(
            uid,
            "videos",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.DESCENDING) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(CloudVideo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeSchedules(uid: String): Flow<List<LiveSchedule>> =
        safeCollectionFlow(
            uid,
            "schedules",
            queryBuilder = { it.orderBy("scheduledStartEpochMs", Query.Direction.ASCENDING) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(LiveSchedule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeUploadSchedules(uid: String): Flow<List<UploadSchedule>> =
        safeCollectionFlow(
            uid,
            "uploadSchedules",
            queryBuilder = { it.orderBy("scheduledPublishEpochMs", Query.Direction.ASCENDING) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(UploadSchedule::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observePlaylists(uid: String): Flow<List<LivePlaylist>> =
        safeCollectionFlow(
            uid,
            "playlists",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.DESCENDING) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(LivePlaylist::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeJobs(uid: String): Flow<List<WorkerJob>> =
        safeCollectionFlow(
            uid,
            "jobs",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.DESCENDING).limit(40) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(WorkerJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeNotifications(uid: String): Flow<List<SmartNotification>> =
        safeCollectionFlow(
            uid,
            "notifications",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.DESCENDING).limit(50) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(SmartNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeAuditLogs(uid: String): Flow<List<SecurityAuditLog>> =
        safeCollectionFlow(
            uid,
            "auditLogs",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.DESCENDING).limit(40) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(SecurityAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeBackups(uid: String): Flow<List<BackupRecord>> =
        safeCollectionFlow(
            uid,
            "backups",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.DESCENDING).limit(20) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(BackupRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeTeamMembers(uid: String): Flow<List<TeamMember>> =
        safeCollectionFlow(
            uid,
            "teamMembers",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.ASCENDING) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(TeamMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeActivityLogs(uid: String): Flow<List<ActivityLog>> =
        safeCollectionFlow(
            uid,
            "activityLogs",
            queryBuilder = { it.orderBy("timestampEpochMs", Query.Direction.DESCENDING).limit(100) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(ActivityLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeCompetitors(uid: String): Flow<List<CompetitorChannel>> =
        safeCollectionFlow(
            uid,
            "competitors",
            queryBuilder = { it.orderBy("subscriberCount", Query.Direction.DESCENDING).limit(25) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(CompetitorChannel::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeSessions(uid: String): Flow<List<UserSession>> =
        safeCollectionFlow(
            uid,
            "sessions",
            queryBuilder = { it.orderBy("lastActiveEpochMs", Query.Direction.DESCENDING).limit(30) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(UserSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    fun observeCopyrightReports(uid: String): Flow<List<CopyrightRiskReport>> =
        safeCollectionFlow(
            uid,
            "copyrightReports",
            queryBuilder = { it.orderBy("createdAt", Query.Direction.DESCENDING).limit(30) }
        ) { docs ->
            docs.mapNotNull {
                it.toObject(CopyrightRiskReport::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
        }

    suspend fun ensureDefaultSettings(userId: String, ownerName: String, deviceName: String): Result<Unit> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Session mismatch." }
            val ref = userDoc(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val initial = UserSettings(
                    userId = uid,
                    ownerName = ownerName.ifBlank { "Harsh Singh" },
                    lastLoginDevice = deviceName
                )
                ref.set(initial.toCreateMap()).await()
            } else {
                ref.update(
                    mapOf(
                        "lastLoginDevice" to deviceName.take(120),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSettings(updates: Map<String, Any>): Result<Unit> {
        var path = "users"
        return try {
            val uid = requireUserId()
            path = "users/$uid"
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            userDoc(uid).update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertChannel(channel: YouTubeChannel): Result<Unit> {
        var path = "users/channels"
        return try {
            val uid = requireUserId()
            val safeId = channel.channelId.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
            require(safeId.isNotBlank()) { "Channel ID cannot be blank." }
            val docRef = userDoc(uid).collection("channels").document(safeId)
            path = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(channel.copy(userId = uid).toCreateMap()).await()
            } else {
                val updateMap = mapOf(
                    "title" to channel.title.take(120),
                    "handle" to channel.handle?.take(80),
                    "thumbnailUrl" to channel.thumbnailUrl?.take(500),
                    "subscriberCount" to channel.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to channel.viewCount.coerceAtLeast(0L),
                    "videoCount" to channel.videoCount.coerceAtLeast(0L),
                    "activeChannel" to channel.activeChannel,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updateMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun switchActiveChannel(targetChannelId: String): Result<Unit> {
        var path = "users/channels"
        return try {
            val uid = requireUserId()
            path = "users/$uid/channels"
            val channelsSnap = userDoc(uid).collection("channels").get().await()
            val batch = db.batch()
            for (doc in channelsSnap.documents) {
                val isTarget = doc.getString("channelId") == targetChannelId || doc.id == targetChannelId
                batch.update(
                    doc.reference,
                    mapOf(
                        "activeChannel" to isTarget,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.update(
                userDoc(uid),
                mapOf(
                    "activeChannelId" to targetChannelId.take(128),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun disconnectChannel(channelDocId: String): Result<Unit> {
        var path = "users/channels/$channelDocId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("channels").document(channelDocId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun addVideo(video: CloudVideo): Result<String> {
        var path = "users/videos"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("videos").document()
            path = docRef.path
            docRef.set(video.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVideoDetails(
        videoId: String,
        title: String,
        description: String,
        privacyStatus: String,
        tags: List<String>
    ): Result<Unit> {
        var path = "users/videos/$videoId"
        return try {
            val uid = requireUserId()
            require(videoId.isNotBlank()) { "Video ID required" }
            require(title.trim().isNotEmpty()) { "Title cannot be empty" }
            val docRef = userDoc(uid).collection("videos").document(videoId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "title" to title.trim().take(150),
                    "description" to description.take(5000),
                    "privacyStatus" to privacyStatus,
                    "tags" to tags.take(15).filter { it.isNotBlank() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateVideoFields(videoId: String, fields: Map<String, Any>): Result<Unit> {
        var path = "users/videos/$videoId"
        return try {
            val uid = requireUserId()
            require(videoId.isNotBlank()) { "Video ID required" }
            val docRef = userDoc(uid).collection("videos").document(videoId)
            path = docRef.path
            val payload = fields.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteVideoGuarded(videoId: String, activeSchedules: List<LiveSchedule>): Result<Unit> {
        var path = "users/videos/$videoId"
        return try {
            val uid = requireUserId()
            val inUse = activeSchedules.any {
                it.videoId == videoId &&
                    it.status in listOf("LIVE", "PREPARING", "SCHEDULED")
            }
            if (inUse) {
                return Result.failure(
                    IllegalStateException("Cannot delete video: it is linked to an active or scheduled broadcast.")
                )
            }
            val docRef = userDoc(uid).collection("videos").document(videoId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createSchedule(
        schedule: LiveSchedule,
        existingSchedules: List<LiveSchedule>,
        existingUploadSchedules: List<UploadSchedule> = emptyList()
    ): Result<String> {
        var path = "users/schedules"
        return try {
            val uid = requireUserId()
            val liveConflict = existingSchedules.firstOrNull {
                it.channelId == schedule.channelId &&
                    it.status in listOf("SCHEDULED", "PREPARING", "LIVE") &&
                    abs(it.scheduledStartEpochMs - schedule.scheduledStartEpochMs) < 15 * 60 * 1000L
            }
            if (liveConflict != null) {
                return Result.failure(
                    IllegalStateException("Scheduling conflict: '${liveConflict.liveTitle}' is already scheduled within 15 minutes on this channel.")
                )
            }
            val uploadConflict = existingUploadSchedules.firstOrNull {
                it.channelId == schedule.channelId &&
                    it.status in listOf("SCHEDULED", "UPLOADING") &&
                    abs(it.scheduledPublishEpochMs - schedule.scheduledStartEpochMs) < 15 * 60 * 1000L
            }
            if (uploadConflict != null) {
                return Result.failure(
                    IllegalStateException("Calendar conflict: Scheduled upload '${uploadConflict.title}' is within 15 minutes on this channel.")
                )
            }
            val docRef = userDoc(uid).collection("schedules").document()
            path = docRef.path
            docRef.set(schedule.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateSchedule(scheduleId: String, updates: Map<String, Any>): Result<Unit> {
        var path = "users/schedules/$scheduleId"
        return try {
            val uid = requireUserId()
            require(scheduleId.isNotBlank()) { "Schedule ID required" }
            val docRef = userDoc(uid).collection("schedules").document(scheduleId)
            path = docRef.path
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String): Result<Unit> {
        var path = "users/schedules/$scheduleId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("schedules").document(scheduleId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- V3.0 Automatic Video Upload Scheduler ---
    suspend fun createUploadSchedule(
        uploadSchedule: UploadSchedule,
        existingUploads: List<UploadSchedule>,
        existingLives: List<LiveSchedule>
    ): Result<String> {
        var path = "users/uploadSchedules"
        return try {
            val uid = requireUserId()
            val uploadConflict = existingUploads.firstOrNull {
                it.channelId == uploadSchedule.channelId &&
                    it.status in listOf("SCHEDULED", "UPLOADING") &&
                    abs(it.scheduledPublishEpochMs - uploadSchedule.scheduledPublishEpochMs) < 15 * 60 * 1000L
            }
            if (uploadConflict != null) {
                return Result.failure(
                    IllegalStateException("Scheduling conflict: Upload '${uploadConflict.title}' is already scheduled within 15 minutes.")
                )
            }
            val liveConflict = existingLives.firstOrNull {
                it.channelId == uploadSchedule.channelId &&
                    it.status in listOf("SCHEDULED", "PREPARING", "LIVE") &&
                    abs(it.scheduledStartEpochMs - uploadSchedule.scheduledPublishEpochMs) < 15 * 60 * 1000L
            }
            if (liveConflict != null) {
                return Result.failure(
                    IllegalStateException("Calendar conflict: Live broadcast '${liveConflict.liveTitle}' is within 15 minutes.")
                )
            }
            val docRef = userDoc(uid).collection("uploadSchedules").document()
            path = docRef.path
            docRef.set(uploadSchedule.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateUploadSchedule(uploadId: String, updates: Map<String, Any>): Result<Unit> {
        var path = "users/uploadSchedules/$uploadId"
        return try {
            val uid = requireUserId()
            require(uploadId.isNotBlank()) { "Upload Schedule ID required" }
            val docRef = userDoc(uid).collection("uploadSchedules").document(uploadId)
            path = docRef.path
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteUploadSchedule(uploadId: String): Result<Unit> {
        var path = "users/uploadSchedules/$uploadId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("uploadSchedules").document(uploadId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun createPlaylist(playlist: LivePlaylist): Result<String> {
        var path = "users/playlists"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("playlists").document()
            path = docRef.path
            docRef.set(playlist.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePlaylist(playlistId: String, updates: Map<String, Any>): Result<Unit> {
        var path = "users/playlists/$playlistId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("playlists").document(playlistId)
            path = docRef.path
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        var path = "users/playlists/$playlistId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("playlists").document(playlistId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun logWorkerJob(job: WorkerJob): Result<String> {
        var path = "users/jobs"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("jobs").document()
            path = docRef.path
            docRef.set(job.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addNotification(
        eventType: String,
        title: String,
        body: String,
        severity: String = "INFO"
    ): Result<Unit> {
        var path = "users/notifications"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("notifications").document()
            path = docRef.path
            val notif = SmartNotification(
                userId = uid,
                eventType = eventType,
                title = title,
                body = body,
                severity = severity,
                readState = false
            )
            docRef.set(notif.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun markNotificationRead(notificationId: String): Result<Unit> {
        var path = "users/notifications/$notificationId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("notifications").document(notificationId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "readState" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun logAuditEvent(
        eventType: String,
        deviceName: String,
        status: String,
        details: String
    ): Result<Unit> {
        var path = "users/auditLogs"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("auditLogs").document()
            path = docRef.path
            val log = SecurityAuditLog(
                userId = uid,
                eventType = eventType,
                deviceName = deviceName,
                status = status,
                details = details
            )
            docRef.set(log.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- V3.0 Activity Log ---
    suspend fun logActivity(
        category: String,
        action: String,
        actorName: String,
        actorRole: String,
        details: String
    ): Result<Unit> {
        var path = "users/activityLogs"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("activityLogs").document()
            path = docRef.path
            val entry = ActivityLog(
                userId = uid,
                category = category,
                action = action,
                actorName = actorName,
                actorRole = actorRole,
                details = details,
                timestampEpochMs = System.currentTimeMillis()
            )
            docRef.set(entry.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    // --- V3.0 Team Access Management ---
    suspend fun addTeamMember(member: TeamMember): Result<String> {
        var path = "users/teamMembers"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("teamMembers").document()
            path = docRef.path
            docRef.set(member.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateTeamMember(memberId: String, updates: Map<String, Any>): Result<Unit> {
        var path = "users/teamMembers/$memberId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("teamMembers").document(memberId)
            path = docRef.path
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteTeamMember(memberId: String): Result<Unit> {
        var path = "users/teamMembers/$memberId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("teamMembers").document(memberId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- V3.0 Backups with SHA-256 Checksum & Real Restore ---
    suspend fun createBackupSnapshot(
        label: String,
        videos: List<CloudVideo>,
        schedules: List<LiveSchedule>,
        playlists: List<LivePlaylist>,
        retentionDays: Int,
        settings: UserSettings? = null
    ): Result<Unit> {
        var path = "users/backups"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("backups").document()
            path = docRef.path
            val summary = buildString {
                append("Videos=${videos.size}, ")
                append("Schedules=${schedules.size}, ")
                append("Playlists=${playlists.size} | ")
                append("Upcoming: ")
                append(
                    schedules.filter { it.status == ScheduleStatus.SCHEDULED.name }
                        .take(5)
                        .joinToString("; ") { it.liveTitle }
                        .ifBlank { "None" }
                )
            }
            val settingsJson = settings?.let {
                JSONObject().apply {
                    put("defaultPrivacy", it.defaultPrivacy)
                    put("defaultResolution", it.defaultResolution)
                    put("defaultLiveDurationMin", it.defaultLiveDurationMin)
                    put("timezone", it.timezone)
                    put("language", it.language)
                    put("monthlyBudgetUsd", it.monthlyBudgetUsd)
                    put("autoBackupFrequency", it.autoBackupFrequency ?: "OFF")
                    put("storageQuotaGb", it.storageQuotaGb ?: 50.0)
                }.toString()
            }
            val schedulesArr = JSONArray().apply {
                schedules.take(30).forEach { s ->
                    put(JSONObject().apply {
                        put("channelId", s.channelId)
                        put("videoId", s.videoId)
                        put("videoTitle", s.videoTitle)
                        put("liveTitle", s.liveTitle)
                        put("liveDescription", s.liveDescription)
                        put("privacyStatus", s.privacyStatus)
                        put("scheduledStartIso", s.scheduledStartIso)
                        put("scheduledStartEpochMs", s.scheduledStartEpochMs)
                        put("timezone", s.timezone)
                        put("recurrence", s.recurrence)
                        put("targetProfile", s.targetProfile)
                        put("status", s.status)
                        put("autoEndOnFinish", s.autoEndOnFinish)
                    })
                }
            }.toString()

            val digestInput = "$summary|$settingsJson|$schedulesArr"
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(digestInput.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val backup = BackupRecord(
                userId = uid,
                label = label.ifBlank { "Manual Metadata Backup" },
                videoCount = videos.size,
                scheduleCount = schedules.size,
                playlistCount = playlists.size,
                retentionDays = retentionDays.coerceIn(1, 3650),
                snapshotSummary = summary,
                settingsJson = settingsJson,
                schedulesJson = schedulesArr,
                checksumSha256 = sha256
            )
            docRef.set(backup.toCreateMap()).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreFromBackup(
        backup: BackupRecord,
        currentSchedules: List<LiveSchedule>
    ): Result<Int> {
        var path = "users/backups"
        return try {
            val uid = requireUserId()
            // Verify checksum if present
            if (!backup.checksumSha256.isNullOrBlank()) {
                val digestInput = "${backup.snapshotSummary}|${backup.settingsJson}|${backup.schedulesJson}"
                val expected = MessageDigest.getInstance("SHA-256")
                    .digest(digestInput.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                if (expected != backup.checksumSha256) {
                    return Result.failure(IllegalStateException("Backup integrity checksum mismatch. Restore aborted."))
                }
            }

            if (!backup.settingsJson.isNullOrBlank()) {
                val sObj = JSONObject(backup.settingsJson)
                val settingsMap = mutableMapOf<String, Any>(
                    "defaultPrivacy" to sObj.optString("defaultPrivacy", "PUBLIC"),
                    "defaultResolution" to sObj.optString("defaultResolution", "1080p"),
                    "defaultLiveDurationMin" to sObj.optInt("defaultLiveDurationMin", 60),
                    "timezone" to sObj.optString("timezone", "Asia/Kolkata"),
                    "language" to sObj.optString("language", "en"),
                    "monthlyBudgetUsd" to sObj.optDouble("monthlyBudgetUsd", 50.0),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc(uid).update(settingsMap).await()
            }

            var restoredSchedules = 0
            if (!backup.schedulesJson.isNullOrBlank()) {
                val arr = JSONArray(backup.schedulesJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val epoch = obj.optLong("scheduledStartEpochMs", 0L)
                    val title = obj.optString("liveTitle", "")
                    val alreadyExists = currentSchedules.any {
                        it.liveTitle == title && abs(it.scheduledStartEpochMs - epoch) < 60_000L
                    }
                    if (!alreadyExists && epoch > 0L && title.isNotBlank()) {
                        val s = LiveSchedule(
                            userId = uid,
                            channelId = obj.optString("channelId", "unassigned"),
                            videoId = obj.optString("videoId", "restored"),
                            videoTitle = obj.optString("videoTitle", title),
                            liveTitle = title,
                            liveDescription = obj.optString("liveDescription", ""),
                            privacyStatus = obj.optString("privacyStatus", "PUBLIC"),
                            scheduledStartIso = obj.optString("scheduledStartIso", ""),
                            scheduledStartEpochMs = epoch,
                            timezone = obj.optString("timezone", "Asia/Kolkata"),
                            recurrence = ScheduleRecurrence.fromRaw(obj.optString("recurrence")).name,
                            targetProfile = obj.optString("targetProfile", "1080p30_6M"),
                            status = if (epoch > System.currentTimeMillis()) ScheduleStatus.SCHEDULED.name else obj.optString("status", ScheduleStatus.COMPLETED.name),
                            autoEndOnFinish = obj.optBoolean("autoEndOnFinish", true)
                        )
                        val ref = userDoc(uid).collection("schedules").document()
                        path = ref.path
                        ref.set(s.toCreateMap()).await()
                        restoredSchedules++
                    }
                }
            }
            Result.success(restoredSchedules)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // --- V4.1 Competitor Channel Tracker ---
    suspend fun upsertCompetitor(competitor: CompetitorChannel): Result<String> {
        var path = "users/competitors"
        return try {
            val uid = requireUserId()
            val safeId = competitor.channelId.replace("/", "_").ifBlank {
                userDoc(uid).collection("competitors").document().id
            }
            val docRef = userDoc(uid).collection("competitors").document(safeId)
            path = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                val updates = mapOf(
                    "title" to competitor.title.take(120).ifBlank { "Public Channel" },
                    "handle" to competitor.handle?.take(80),
                    "thumbnailUrl" to competitor.thumbnailUrl?.take(500),
                    "subscriberCount" to competitor.subscriberCount.coerceAtLeast(0L),
                    "viewCount" to competitor.viewCount.coerceAtLeast(0L),
                    "videoCount" to competitor.videoCount.coerceAtLeast(0L),
                    "lastSyncedEpochMs" to competitor.lastSyncedEpochMs.coerceAtLeast(1L),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updates).await()
            } else {
                docRef.set(competitor.copy(userId = uid).toCreateMap()).await()
            }
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCompetitor(competitorId: String): Result<Unit> {
        var path = "users/competitors/$competitorId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("competitors").document(competitorId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // --- V4.1 Active Session & Login Device History ---
    suspend fun upsertSession(sessionId: String, session: UserSession): Result<Unit> {
        var path = "users/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("sessions").document(sessionId)
            path = docRef.path
            val snap = docRef.get().await()
            if (snap.exists()) {
                val updates = mapOf(
                    "lastActiveEpochMs" to session.lastActiveEpochMs.coerceAtLeast(1L),
                    "revoked" to session.revoked,
                    "trustedDevice" to session.trustedDevice,
                    "networkHint" to session.networkHint?.take(120)?.takeIf { it.isNotBlank() },
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null } as Map<String, Any>
                docRef.update(updates).await()
            } else {
                docRef.set(session.copy(userId = uid).toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeSession(sessionId: String): Result<Unit> {
        var path = "users/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("sessions").document(sessionId)
            path = docRef.path
            docRef.update(
                mapOf(
                    "revoked" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun revokeSessions(sessionIds: List<String>): Result<Int> {
        var count = 0
        for (sid in sessionIds) {
            if (revokeSession(sid).isSuccess) count++
        }
        return Result.success(count)
    }

    // --- V4.1 AI Copyright Risk Reports ---
    suspend fun addCopyrightReport(report: CopyrightRiskReport): Result<String> {
        var path = "users/copyrightReports"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("copyrightReports").document()
            path = docRef.path
            docRef.set(report.copy(userId = uid).toCreateMap()).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCopyrightReport(reportId: String): Result<Unit> {
        var path = "users/copyrightReports/$reportId"
        return try {
            val uid = requireUserId()
            val docRef = userDoc(uid).collection("copyrightReports").document(reportId)
            path = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
