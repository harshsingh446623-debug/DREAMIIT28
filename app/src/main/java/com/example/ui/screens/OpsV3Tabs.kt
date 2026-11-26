package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ActivityLog
import com.example.data.model.RevenueSnapshot
import com.example.data.model.TeamMember
import com.example.data.model.TeamRole
import com.example.localization.LocalAppStrings
import com.example.ui.components.StatTileCard
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Feature #15: YouTube Revenue Dashboard (YPP monetary metrics, date filter, daily chart, honest unavailable state).
 */
@Composable
fun RevenueDashboardTab(
    revenue: RevenueSnapshot,
    onLoadRevenue: (Int) -> Unit
) {
    val strings = LocalAppStrings.current
    var selectedDays by remember { mutableIntStateOf(revenue.dateRangeDays) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7, 28, 90, 365).forEach { d ->
                        FilterChip(
                            selected = selectedDays == d,
                            onClick = {
                                selectedDays = d
                                onLoadRevenue(d)
                            },
                            label = { Text("${d}d") }
                        )
                    }
                }
                OutlinedButton(onClick = { onLoadRevenue(selectedDays) }, modifier = Modifier.testTag("fetch_revenue_btn")) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = ElectricCyan)
                    Spacer(Modifier.width(4.dp))
                    Text("Fetch")
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, if (revenue.authorized) SuccessEmerald else WarningAmber.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (revenue.authorized) "AUTHORIZED YOUTUBE REVENUE (USD)" else "MONETIZATION STATUS",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (revenue.authorized) SuccessEmerald else WarningAmber
                    )
                    Text(revenue.statusMessage, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTileCard(
                        label = "Est. Revenue (${selectedDays}d)",
                        value = revenue.estimatedRevenueUsd?.let { String.format(Locale.US, "$%.2f", it) } ?: strings.unavailableLabel,
                        icon = Icons.Default.AttachMoney,
                        accent = SuccessEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    StatTileCard(
                        label = "Ad Revenue",
                        value = revenue.adRevenueUsd?.let { String.format(Locale.US, "$%.2f", it) } ?: strings.unavailableLabel,
                        icon = Icons.Default.AttachMoney,
                        accent = ElectricCyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatTileCard(
                        label = "YouTube Premium",
                        value = revenue.redPartnerRevenueUsd?.let { String.format(Locale.US, "$%.2f", it) } ?: strings.unavailableLabel,
                        icon = Icons.Default.AttachMoney,
                        accent = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    )
                    StatTileCard(
                        label = "Playback CPM",
                        value = revenue.cpmUsd?.let { String.format(Locale.US, "$%.2f", it) } ?: strings.unavailableLabel,
                        icon = Icons.Default.AttachMoney,
                        accent = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (revenue.dailyPoints.isNotEmpty()) {
            item {
                val maxDay = max(0.01, revenue.dailyPoints.maxOf { it.estimatedRevenueUsd }).toFloat()
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Daily Revenue History (${revenue.dailyPoints.size} days)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        ) {
                            val pts = revenue.dailyPoints.takeLast(30)
                            val slotW = size.width / pts.size.coerceAtLeast(1)
                            val barW = (slotW * 0.65f).coerceAtLeast(3f)
                            pts.forEachIndexed { idx, pt ->
                                val ratio = (pt.estimatedRevenueUsd.toFloat() / maxDay).coerceIn(0.04f, 1f)
                                val h = size.height * 0.88f * ratio
                                drawRoundRect(
                                    color = SuccessEmerald,
                                    topLeft = Offset(idx * slotW + (slotW - barW) / 2f, size.height - h),
                                    size = Size(barW, h),
                                    cornerRadius = CornerRadius(6f, 6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Feature #18: Team Access Management (Harsh Singh locked Owner, add/edit/remove members, RBAC enforcement).
 */
@Composable
fun TeamAccessTab(
    teamMembers: List<TeamMember>,
    activeRole: TeamRole,
    onAddMember: (name: String, email: String, role: TeamRole, notes: String) -> Unit,
    onUpdateMember: (member: TeamMember, newRole: TeamRole, active: Boolean) -> Unit,
    onRemoveMember: (member: TeamMember) -> Unit,
    onSwitchActiveRole: (TeamRole) -> Unit
) {
    var nameInput by remember { mutableStateOf("") }
    var emailInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }
    var selectedNewRole by remember { mutableStateOf(TeamRole.EDITOR) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Primary Owner Locked Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.5.dp, SuccessEmerald.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessEmerald)
                            Spacer(Modifier.width(6.dp))
                            Text("Harsh Singh (Primary Owner)", color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                        StatusBadge("PERMANENT OWNER", SuccessEmerald)
                    }
                    Text("harshsingh223388@gmail.com • Cannot be removed or downgraded.", color = TextSecondary)
                }
            }
        }

        // Active Role Enforcement Switcher
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Active Permission Mode (Enforced Across App)", color = ElectricCyan, style = MaterialTheme.typography.labelLarge)
                    Text(activeRole.summary, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TeamRole.entries.forEach { role ->
                            FilterChip(
                                selected = activeRole == role,
                                onClick = { onSwitchActiveRole(role) },
                                label = { Text(role.label) },
                                modifier = Modifier.testTag("switch_role_${role.name}")
                            )
                        }
                    }
                }
            }
        }

        // Add Member Form
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Group, contentDescription = null, tint = ElectricCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Team Member", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Member Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("team_name_input")
                    )
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Google Account Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("team_email_input")
                    )
                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Responsibility Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TeamRole.entries.forEach { role ->
                            FilterChip(
                                selected = selectedNewRole == role,
                                onClick = { selectedNewRole = role },
                                label = { Text(role.label) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            onAddMember(nameInput, emailInput, selectedNewRole, notesInput)
                            nameInput = ""
                            emailInput = ""
                            notesInput = ""
                        },
                        enabled = nameInput.isNotBlank() && emailInput.contains("@"),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        modifier = Modifier.fillMaxWidth().testTag("add_team_member_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add Team Member")
                    }
                }
            }
        }

        items(teamMembers, key = { it.id }) { member ->
            val currentRole = TeamRole.fromRaw(member.role)
            Card(
                colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                border = BorderStroke(1.dp, BorderSubtle),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.memberName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            Text(member.memberEmail, color = ElectricCyan, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { onRemoveMember(member) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove Member", tint = LiveCrimson)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TeamRole.entries.forEach { role ->
                                FilterChip(
                                    selected = currentRole == role,
                                    onClick = { onUpdateMember(member, role, member.activeMember) },
                                    label = { Text(role.label) }
                                )
                            }
                        }
                        Switch(
                            checked = member.activeMember,
                            onCheckedChange = { onUpdateMember(member, currentRole, it) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Feature #19: Comprehensive Activity Log (Logins, uploads, live scheduling, start/stop, branding, team, backup, media).
 */
@Composable
fun ActivityLogTab(logs: List<ActivityLog>) {
    var selectedCategory by remember { mutableStateOf("ALL") }
    val categories = listOf("ALL", "AUTH", "UPLOAD", "LIVE", "ACCOUNT", "MEDIA", "BACKUP", "TEAM")
    val filtered = remember(logs, selectedCategory) {
        if (selectedCategory == "ALL") logs else logs.filter { it.category == selectedCategory }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        modifier = Modifier.testTag("activity_filter_$cat")
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Text("No activity logs recorded for this filter yet.", color = TextSecondary)
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                val ts = SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault())
                    .format(Date(item.timestampEpochMs))
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.History, contentDescription = null, tint = ElectricCyan)
                                Spacer(Modifier.width(6.dp))
                                Text(item.action, color = Color.White, style = MaterialTheme.typography.titleMedium)
                            }
                            StatusBadge(item.category, ElectricBlue)
                        }
                        if (item.details.isNotBlank()) {
                            Text(item.details, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("$ts • ${item.actorName} (${item.actorRole})", color = ElectricCyan, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
