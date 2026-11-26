package com.example

import com.example.data.model.CloudVideo
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.StreamHealthReport
import com.example.data.model.UploadStatus
import com.example.data.network.CloudWorkerClient
import com.example.data.network.WorkerHealthResponse
import com.example.ui.viewmodel.DreamiitStudioHelpers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamiitV4UnitTest {

    @Test
    fun `engagement rate calculates percentage cleanly and guards zero views`() {
        assertEquals(0.0, DreamiitStudioHelpers.computeEngagementRatePercent(0L, 10L, 2L), 0.001)
        assertEquals(6.5, DreamiitStudioHelpers.computeEngagementRatePercent(1000L, 50L, 15L), 0.001)
    }

    @Test
    fun `api usage report flags 80 percent quota warning threshold`() {
        val below = DreamiitStudioHelpers.computeApiUsageReport(7900, 10_000, 4, "2026-11-25")
        assertFalse(below.warning80Triggered)
        assertEquals(79.0, below.usagePercent, 0.01)

        val above = DreamiitStudioHelpers.computeApiUsageReport(8200, 10_000, 9, "2026-11-25")
        assertTrue(above.warning80Triggered)
        assertEquals(82.0, above.usagePercent, 0.01)
        assertEquals(9, above.geminiCallsSession)
    }

    @Test
    fun `unknown device detection ignores case and respects empty seed list`() {
        assertFalse(DreamiitStudioHelpers.isUnknownDevice("Google Pixel 9", null))
        assertFalse(DreamiitStudioHelpers.isUnknownDevice("Google Pixel 9", emptyList()))
        assertFalse(DreamiitStudioHelpers.isUnknownDevice("Google Pixel 9", listOf("google pixel 9")))
        assertTrue(DreamiitStudioHelpers.isUnknownDevice("Samsung Galaxy S25", listOf("Google Pixel 9")))
    }

    @Test
    fun `two step verification reminder honors verified state and 7 day snooze`() {
        val now = 1_700_000_000_000L
        assertFalse(DreamiitStudioHelpers.isTwoStepReminderDue(true, 0L, now))
        assertTrue(DreamiitStudioHelpers.isTwoStepReminderDue(false, null, now))
        assertFalse(DreamiitStudioHelpers.isTwoStepReminderDue(false, now - 2 * 86_400_000L, now))
        assertTrue(DreamiitStudioHelpers.isTwoStepReminderDue(false, now - 8 * 86_400_000L, now))
    }

    @Test
    fun `auto stream retry checks toggles status and max retries`() {
        val failedRetryable = LiveSchedule(id = "s1", status = ScheduleStatus.FAILED.name, retryCount = 1, maxRetries = 3)
        assertTrue(DreamiitStudioHelpers.shouldAutoRetryStream(failedRetryable, autoRetryEnabled = true, autoRecoveryEnabled = true))
        assertFalse(DreamiitStudioHelpers.shouldAutoRetryStream(failedRetryable, autoRetryEnabled = false, autoRecoveryEnabled = true))

        val exhausted = failedRetryable.copy(retryCount = 3)
        assertFalse(DreamiitStudioHelpers.shouldAutoRetryStream(exhausted, autoRetryEnabled = true, autoRecoveryEnabled = true))
    }

    @Test
    fun `storage cleanup excludes videos bound to active or scheduled live streams`() {
        val videos = listOf(
            CloudVideo(id = "v_bound", uploadStatus = UploadStatus.READY.name, youtubeVideoId = null),
            CloudVideo(id = "v_draft", uploadStatus = UploadStatus.READY.name, youtubeVideoId = null),
            CloudVideo(id = "v_failed", uploadStatus = UploadStatus.FAILED.name, youtubeVideoId = null),
            CloudVideo(id = "v_yt", uploadStatus = UploadStatus.UPLOADED.name, youtubeVideoId = "yt_123")
        )
        val schedules = listOf(
            LiveSchedule(id = "l1", videoId = "v_bound", status = ScheduleStatus.SCHEDULED.name)
        )
        val candidates = DreamiitStudioHelpers.collectCleanupCandidates(videos, schedules)
        assertEquals(setOf("v_draft", "v_failed"), candidates.map { it.id }.toSet())
    }

    @Test
    fun `live connection alert flags unreachable worker or bad YouTube stream health`() {
        val live = LiveSchedule(id = "l1", liveTitle = "JEE Marathon", status = ScheduleStatus.LIVE.name)
        val offlineWorker = WorkerHealthResponse(reachable = false, status = "UNREACHABLE")
        assertNotNull(DreamiitStudioHelpers.evaluateLiveConnectionAlert(live, offlineWorker, null))

        val onlineWorker = WorkerHealthResponse(reachable = true, status = "ONLINE")
        val badYt = StreamHealthReport("b1", "live", "bad", listOf("No incoming RTMP frames"))
        val ytAlert = DreamiitStudioHelpers.evaluateLiveConnectionAlert(live, onlineWorker, badYt)
        assertNotNull(ytAlert)
        assertTrue(ytAlert!!.contains("No incoming RTMP frames"))

        val goodYt = StreamHealthReport("b1", "live", "good")
        assertNull(DreamiitStudioHelpers.evaluateLiveConnectionAlert(live, onlineWorker, goodYt))
    }

    @Test
    fun `cloud billing json parser never fabricates spend when disconnected`() {
        val disconnected = CloudWorkerClient.parseBillingJsonString(
            """{"connected":false,"message":"Billing API Not Connected"}""",
            source = "https://worker.example/api/billing"
        )
        assertFalse(disconnected.connected)
        assertNull(disconnected.totalMonthlyUsd)
        assertEquals("Billing API Not Connected", disconnected.statusMessage)

        val connected = CloudWorkerClient.parseBillingJsonString(
            """{
                "connected":true,
                "currency":"USD",
                "totalMonthlyUsd":18.42,
                "billingPeriod":"2026-11",
                "lineItems":[
                    {"label":"Cloud Run Worker","amountUsd":12.10},
                    {"label":"Firebase Storage","amountUsd":6.32}
                ]
            }""",
            source = "https://worker.example/api/billing"
        )
        assertTrue(connected.connected)
        assertEquals(18.42, connected.totalMonthlyUsd ?: 0.0, 0.001)
        assertEquals("2026-11", connected.billingPeriod)
        assertEquals(2, connected.lineItems.size)
        assertEquals("Cloud Run Worker", connected.lineItems[0].label)
    }
}
