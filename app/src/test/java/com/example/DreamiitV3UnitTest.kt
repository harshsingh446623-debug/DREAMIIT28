package com.example

import com.example.data.model.BackupRecord
import com.example.data.model.CloudVideo
import com.example.data.model.LiveSchedule
import com.example.data.model.ScheduleStatus
import com.example.data.model.TeamRole
import com.example.data.model.UploadSchedule
import com.example.data.model.UploadScheduleStatus
import com.example.data.model.UserSettings
import com.example.ui.viewmodel.DreamiitStudioHelpers
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class DreamiitV3UnitTest {

    @Test
    fun `countdown calculates exact days hours minutes seconds for earliest upcoming live`() {
        val now = 1_700_000_000_000L
        val targetDelta = ((2L * 86_400L) + (3L * 3600L) + (14L * 60L) + 25L) * 1000L
        val schedules = listOf(
            LiveSchedule(
                id = "completed",
                liveTitle = "Old Live",
                scheduledStartEpochMs = now + 10_000L,
                status = ScheduleStatus.COMPLETED.name
            ),
            LiveSchedule(
                id = "later",
                liveTitle = "Later Live",
                scheduledStartEpochMs = now + targetDelta + 500_000L,
                status = ScheduleStatus.SCHEDULED.name
            ),
            LiveSchedule(
                id = "next",
                liveTitle = "Physics Marathon 1080p",
                scheduledStartEpochMs = now + targetDelta,
                status = ScheduleStatus.SCHEDULED.name
            )
        )

        val cd = DreamiitStudioHelpers.computeNextCountdown(schedules, now)
        assertNotNull(cd)
        assertEquals("next", cd!!.schedule.id)
        assertEquals(2L, cd.days)
        assertEquals(3L, cd.hours)
        assertEquals(14L, cd.minutes)
        assertEquals(25L, cd.seconds)
    }

    @Test
    fun `calendar conflict prevents overlapping upload or live within 15 minutes`() {
        val base = 1_700_000_000_000L
        val lives = listOf(
            LiveSchedule(
                id = "l1",
                channelId = "ch1",
                liveTitle = "Morning Live",
                scheduledStartEpochMs = base,
                status = ScheduleStatus.SCHEDULED.name
            )
        )
        val uploads = listOf(
            UploadSchedule(
                id = "u1",
                channelId = "ch1",
                title = "Mock Test Solution",
                scheduledPublishEpochMs = base + 3_600_000L,
                status = UploadScheduleStatus.SCHEDULED.name
            )
        )

        val conflictLive = DreamiitStudioHelpers.detectScheduleConflict(
            channelId = "ch1",
            targetEpochMs = base + 10 * 60_000L,
            liveSchedules = lives,
            uploadSchedules = uploads
        )
        assertNotNull(conflictLive)
        assertTrue(conflictLive!!.contains("Morning Live"))

        val conflictUpload = DreamiitStudioHelpers.detectScheduleConflict(
            channelId = "ch1",
            targetEpochMs = base + 3_600_000L - 5 * 60_000L,
            liveSchedules = lives,
            uploadSchedules = uploads
        )
        assertNotNull(conflictUpload)
        assertTrue(conflictUpload!!.contains("Mock Test Solution"))

        val clearSlot = DreamiitStudioHelpers.detectScheduleConflict(
            channelId = "ch1",
            targetEpochMs = base + 20 * 60_000L,
            liveSchedules = lives,
            uploadSchedules = uploads
        )
        assertNull(clearSlot)
    }

    @Test
    fun `storage alert triggers 80 percent warning and 90 percent critical thresholds`() {
        val oneGb = 1024L * 1024L * 1024L
        val report82 = DreamiitStudioHelpers.computeStorageAlertReport(
            videos = listOf(CloudVideo(id = "v1", fileSizeBytes = (8.2 * oneGb).toLong())),
            quotaGb = 10.0
        )
        assertTrue(report82.alert80Triggered)
        assertFalse(report82.alert90Triggered)

        val report95 = DreamiitStudioHelpers.computeStorageAlertReport(
            videos = listOf(CloudVideo(id = "v2", fileSizeBytes = (9.5 * oneGb).toLong())),
            quotaGb = 10.0
        )
        assertTrue(report95.alert80Triggered)
        assertTrue(report95.alert90Triggered)
    }

    @Test
    fun `auto backup scheduler checks OFF DAILY and WEEKLY intervals accurately`() {
        val now = 1_700_000_000_000L
        val recentBackup = BackupRecord(
            id = "b1",
            createdAt = Timestamp(Date(now - 2 * 3600_000L))
        )
        val oldDailyBackup = BackupRecord(
            id = "b2",
            createdAt = Timestamp(Date(now - 25 * 3600_000L))
        )

        assertFalse(DreamiitStudioHelpers.isAutoBackupDue("OFF", emptyList(), now))
        assertTrue(DreamiitStudioHelpers.isAutoBackupDue("DAILY", emptyList(), now))
        assertFalse(DreamiitStudioHelpers.isAutoBackupDue("DAILY", listOf(recentBackup), now))
        assertTrue(DreamiitStudioHelpers.isAutoBackupDue("DAILY", listOf(oldDailyBackup), now))
        assertFalse(DreamiitStudioHelpers.isAutoBackupDue("WEEKLY", listOf(oldDailyBackup), now))
    }

    @Test
    fun `rbac permission gating enforces OWNER EDITOR and VIEWER rules`() {
        assertTrue(DreamiitStudioHelpers.canPerformAction(TeamRole.OWNER, requiresOwner = true, requiresEditor = true))
        assertTrue(DreamiitStudioHelpers.canPerformAction(TeamRole.EDITOR, requiresOwner = false, requiresEditor = true))
        assertFalse(DreamiitStudioHelpers.canPerformAction(TeamRole.EDITOR, requiresOwner = true, requiresEditor = true))
        assertFalse(DreamiitStudioHelpers.canPerformAction(TeamRole.VIEWER, requiresOwner = false, requiresEditor = true))
        assertTrue(DreamiitStudioHelpers.canPerformAction(TeamRole.VIEWER, requiresOwner = false, requiresEditor = false))
    }

    @Test
    fun `cost breakdown flags monthly budget exceeded`() {
        val oneGb = 1024L * 1024L * 1024L
        val res = DreamiitStudioHelpers.computeCostBreakdown(
            videos = listOf(CloudVideo(id = "v1", fileSizeBytes = 100L * oneGb, durationSec = 3600L)),
            schedules = listOf(LiveSchedule(id = "s1", status = ScheduleStatus.COMPLETED.name)),
            settings = UserSettings(monthlyBudgetUsd = 1.0, defaultLiveDurationMin = 60)
        )
        assertTrue(res.budgetExceeded)
        assertTrue(res.totalMonthlyUsd > 1.0)
    }
}
