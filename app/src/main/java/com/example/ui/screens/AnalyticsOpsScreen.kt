package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.OAuthCredentials
import com.example.data.model.ActivityLog
import com.example.data.model.ApiUsageReport
import com.example.data.model.BackupRecord
import com.example.data.model.ChannelBrandingInfo
import com.example.data.model.CloudCostBreakdown
import com.example.data.model.CloudVideo
import com.example.data.model.CommentThreadItem
import com.example.data.model.ComparedVideoMetric
import com.example.data.model.CompetitorChannel
import com.example.data.model.ConnectedBillingReport
import com.example.data.model.CopyrightRiskReport
import com.example.data.model.LiveSchedule
import com.example.data.model.RevenueSnapshot
import com.example.data.model.SecurityAlertSummary
import com.example.data.model.SecurityAuditLog
import com.example.data.model.StorageAlertReport
import com.example.data.model.TeamMember
import com.example.data.model.TeamRole
import com.example.data.model.UserSession
import com.example.data.model.UserSettings
import com.example.data.model.VideoPerformanceDetail
import com.example.data.model.WorkerJob
import com.example.data.model.YouTubeAnalyticsSnapshot
import com.example.data.model.YouTubeChannel
import com.example.data.model.YouTubePolicyAlert
import com.example.data.network.WorkerHealthResponse
import com.example.localization.LocalAppStrings
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.DreamiitStudioHelpers

@Composable
fun AnalyticsOpsScreen(
    requestedTab: Int? = null,
    onConsumeRequestedTab: () -> Unit = {},
    activeChannel: YouTubeChannel?,
    settings: UserSettings? = null,
    analytics: YouTubeAnalyticsSnapshot,
    videoPerformanceList: List<VideoPerformanceDetail> = emptyList(),
    videoPerformanceStatus: String = "",
    policyAlerts: List<YouTubePolicyAlert> = emptyList(),
    videos: List<CloudVideo> = emptyList(),
    schedules: List<LiveSchedule> = emptyList(),
    competitors: List<CompetitorChannel> = emptyList(),
    competitorBusy: Boolean = false,
    copyrightReports: List<CopyrightRiskReport> = emptyList(),
    copyrightScanBusy: Boolean = false,
    revenue: RevenueSnapshot = RevenueSnapshot(),
    comparedVideos: List<ComparedVideoMetric> = emptyList(),
    comparisonStatus: String = "",
    comments: List<CommentThreadItem> = emptyList(),
    commentsStatus: String = "",
    commentsLoading: Boolean = false,
    brandingInfo: ChannelBrandingInfo = ChannelBrandingInfo(),
    brandingBusy: Boolean = false,
    workerHealth: WorkerHealthResponse = WorkerHealthResponse(),
    jobs: List<WorkerJob> = emptyList(),
    apiUsage: ApiUsageReport = ApiUsageReport(0, 10_000, 0, 0.0, false, ""),
    connectedBilling: ConnectedBillingReport = ConnectedBillingReport(),
    cost: CloudCostBreakdown,
    storageAlert: StorageAlertReport = StorageAlertReport(0L, 0.0, 50.0, 50.0, 0.0, false, false),
    backups: List<BackupRecord>,
    teamMembers: List<TeamMember> = emptyList(),
    activeRole: TeamRole = TeamRole.OWNER,
    activityLogs: List<ActivityLog> = emptyList(),
    auditLogs: List<SecurityAuditLog>,
    sessions: List<UserSession> = emptyList(),
    currentSessionId: String = "",
    securitySummary: SecurityAlertSummary = SecurityAlertSummary(false, false, false, "Android", 1, false),
    oauthCredentials: OAuthCredentials,
    onLoadAnalytics: (Int) -> Unit,
    onLoadVideoPerformance: () -> Unit = {},
    onAddCompetitor: (String) -> Unit = {},
    onRefreshCompetitors: () -> Unit = {},
    onRemoveCompetitor: (String) -> Unit = {},
    onRunCopyrightScan: (String, String, String, List<String>, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onDeleteCopyrightReport: (String) -> Unit = {},
    onLoadRevenue: (Int) -> Unit = {},
    onLoadComparedVideos: () -> Unit = {},
    onRefreshComments: (String?) -> Unit = {},
    onReplyComment: (String, String) -> Unit = { _, _ -> },
    onModerateComment: (String, Boolean) -> Unit = { _, _ -> },
    onLoadBranding: () -> Unit = {},
    onSaveBranding: (String, String, ByteArray?) -> Unit = { _, _, _ -> },
    onRefreshHealth: () -> Unit = {},
    onRefreshBilling: () -> Unit = {},
    onUpdateBudget: (Double) -> Unit,
    onUpdateApiQuotaLimit: (Int) -> Unit = {},
    onCleanupDrafts: () -> Unit = {},
    onCreateBackup: (String, Int) -> Unit,
    onRestoreBackup: (BackupRecord) -> Unit = {},
    onUpdateAutoBackup: (enabled: Boolean, frequency: String) -> Unit = { _, _ -> },
    onAddTeamMember: (String, String, TeamRole, String) -> Unit = { _, _, _, _ -> },
    onUpdateTeamMember: (TeamMember, TeamRole, Boolean) -> Unit = { _, _, _ -> },
    onRemoveTeamMember: (TeamMember) -> Unit = {},
    onSwitchActiveRole: (TeamRole) -> Unit = {},
    onTrustCurrentDevice: () -> Unit = {},
    onConfirmTwoFactor: (Boolean) -> Unit = {},
    onSnoozeTwoFactor: () -> Unit = {},
    onRevokeSession: (String) -> Unit = {},
    onRevokeOtherSessions: () -> Unit = {},
    onLogoutAllDevices: () -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        strings.analyticsTab,
        strings.competitorsTab,
        strings.copyrightPolicyTab,
        strings.revenueTab,
        strings.compareVideosTab,
        strings.commentsTab,
        strings.brandingTab,
        strings.cloudOpsTab,
        strings.backupsTab,
        strings.teamAccessTab,
        strings.activityLogTab,
        strings.securityTab
    )

    val cleanupCandidatesCount = remember(videos, schedules) {
        DreamiitStudioHelpers.collectCleanupCandidates(videos, schedules).size
    }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null && requestedTab in tabs.indices) {
            selectedTab = requestedTab
            onConsumeRequestedTab()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = ObsidianSurface,
            contentColor = ElectricCyan,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag("ops_tab_$index")
                )
            }
        }

        when (selectedTab) {
            0 -> AnalyticsV4ReportTab(
                activeChannel = activeChannel,
                analytics = analytics,
                videoPerformanceList = videoPerformanceList,
                videoPerformanceStatus = videoPerformanceStatus,
                policyAlerts = policyAlerts,
                cloudVideos = videos,
                schedules = schedules,
                onLoadAnalytics = onLoadAnalytics,
                onLoadVideoPerformance = onLoadVideoPerformance
            )
            1 -> CompetitorsTrackerTab(
                competitors = competitors,
                busy = competitorBusy,
                onAddCompetitor = onAddCompetitor,
                onRefreshCompetitors = onRefreshCompetitors,
                onRemoveCompetitor = onRemoveCompetitor
            )
            2 -> CopyrightRiskTab(
                videos = videos,
                reports = copyrightReports,
                busy = copyrightScanBusy,
                onRunScan = onRunCopyrightScan,
                onDeleteReport = onDeleteCopyrightReport
            )
            3 -> RevenueDashboardTab(revenue, onLoadRevenue)
            4 -> VideoComparisonTab(comparedVideos, comparisonStatus, onLoadComparedVideos)
            5 -> CommentManagerTab(comments, commentsStatus, commentsLoading, onRefreshComments, onReplyComment, onModerateComment)
            6 -> ChannelBrandingTab(brandingInfo, brandingBusy, onLoadBranding, onSaveBranding)
            7 -> CloudManagementV4Tab(
                workerHealth = workerHealth,
                jobs = jobs,
                apiUsage = apiUsage,
                billing = connectedBilling,
                cost = cost,
                storageAlert = storageAlert,
                cleanupCandidateCount = cleanupCandidatesCount,
                onRefreshHealth = onRefreshHealth,
                onRefreshBilling = onRefreshBilling,
                onUpdateBudget = onUpdateBudget,
                onUpdateApiQuotaLimit = onUpdateApiQuotaLimit,
                onCleanupDrafts = onCleanupDrafts
            )
            8 -> BackupsTab(
                backups = backups,
                autoBackupEnabled = settings?.autoBackupEnabled == true,
                autoBackupFrequency = settings?.autoBackupFrequency ?: "OFF",
                onCreateBackup = onCreateBackup,
                onRestoreBackup = onRestoreBackup,
                onUpdateAutoBackup = onUpdateAutoBackup
            )
            9 -> TeamAccessTab(teamMembers, activeRole, onAddTeamMember, onUpdateTeamMember, onRemoveTeamMember, onSwitchActiveRole)
            10 -> ActivityLogTab(activityLogs)
            11 -> SecurityManagementV4Tab(
                auditLogs = auditLogs,
                sessions = sessions,
                currentSessionId = currentSessionId,
                securitySummary = securitySummary,
                oauthCredentials = oauthCredentials,
                onTrustCurrentDevice = onTrustCurrentDevice,
                onConfirmTwoFactor = onConfirmTwoFactor,
                onSnoozeTwoFactor = onSnoozeTwoFactor,
                onRevokeSession = onRevokeSession,
                onRevokeOtherSessions = onRevokeOtherSessions,
                onSecureLogoutConfirmed = onLogoutAllDevices
            )
        }
    }
}



@Composable
private fun BackupsTab(
    backups: List<BackupRecord>,
    autoBackupEnabled: Boolean,
    autoBackupFrequency: String,
    onCreateBackup: (String, Int) -> Unit,
    onRestoreBackup: (BackupRecord) -> Unit,
    onUpdateAutoBackup: (enabled: Boolean, frequency: String) -> Unit
) {
    var label by remember { mutableStateOf("DREAMIIT28 Snapshot") }
    var retentionDays by remember { mutableIntStateOf(30) }
    var restoreTarget by remember { mutableStateOf<BackupRecord?>(null) }

    restoreTarget?.let { bak ->
        AlertDialog(
            onDismissRequest = { restoreTarget = null },
            title = { Text("Restore Backup Snapshot") },
            text = {
                Text(
                    "Restore settings and recover any missing schedules from '${bak.label}'? " +
                        (bak.checksumSha256?.let { "SHA-256: ${it.take(16)}..." } ?: "")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onRestoreBackup(bak)
                        restoreTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) { Text("Restore Now") }
            },
            dismissButton = {
                TextButton(onClick = { restoreTarget = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Automatic Backup Scheduler Card (#16)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (autoBackupEnabled) SuccessEmerald.copy(alpha = 0.6f) else BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Backup Scheduler", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Text("Auto-snapshots with SHA-256 integrity", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = autoBackupEnabled,
                            onCheckedChange = { enabled ->
                                val nextFreq = if (enabled && autoBackupFrequency == "OFF") "DAILY" else if (!enabled) "OFF" else autoBackupFrequency
                                onUpdateAutoBackup(enabled, nextFreq)
                            },
                            modifier = Modifier.testTag("auto_backup_switch")
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("OFF", "DAILY", "WEEKLY").forEach { freq ->
                            FilterChip(
                                selected = autoBackupFrequency == freq,
                                onClick = {
                                    onUpdateAutoBackup(freq != "OFF", freq)
                                },
                                label = { Text(freq) },
                                modifier = Modifier.testTag("auto_backup_freq_$freq")
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Backup, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(8.dp))
                        Text("Database & Schedule Backup", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    }
                    Text(
                        "Backs up settings, channel metadata, schedules, and playlists with SHA-256 verification.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Backup Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30, 90).forEach { d ->
                            FilterChip(
                                selected = retentionDays == d,
                                onClick = { retentionDays = d },
                                label = { Text("Retention: ${d}d") }
                            )
                        }
                    }
                    Button(
                        onClick = { onCreateBackup(label, retentionDays) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth().testTag("create_backup_now_btn")
                    ) {
                        Text("Create Metadata Backup Now")
                    }
                }
            }
        }

        items(backups, key = { it.id }) { bak ->
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(bak.label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
                        StatusBadge(text = "${bak.retentionDays}d Policy", color = ElectricCyan)
                    }
                    Text(bak.snapshotSummary, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    if (!bak.checksumSha256.isNullOrBlank()) {
                        Text(
                            text = "SHA-256: ${bak.checksumSha256.take(24)}...",
                            style = MaterialTheme.typography.labelSmall,
                            color = SuccessEmerald
                        )
                    }
                    OutlinedButton(
                        onClick = { restoreTarget = bak },
                        modifier = Modifier.fillMaxWidth().testTag("restore_backup_${bak.id}")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Restore Settings & Missing Schedules")
                    }
                }
            }
        }
    }
}


