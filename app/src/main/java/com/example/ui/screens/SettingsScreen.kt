package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.OAuthCredentials
import com.example.data.model.PrivacyStatus
import com.example.data.model.UserSettings
import com.example.data.model.YouTubeChannel
import com.example.localization.LocalAppStrings
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElevatedCard
import com.example.ui.theme.LiveCrimson
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SuccessEmerald
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VoidBlack
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    userEmail: String,
    settings: UserSettings?,
    channels: List<YouTubeChannel>,
    oauthCredentials: OAuthCredentials,
    onPrepareOAuthUrl: suspend (String) -> android.net.Uri,
    onSwitchChannel: (String) -> Unit,
    onDisconnectChannel: (String) -> Unit,
    onDisconnectOAuth: () -> Unit,
    onUpdateSettings: (Map<String, Any>) -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val strings = LocalAppStrings.current

    var oauthClientId by remember(settings?.youtubeOAuthClientId) {
        mutableStateOf(settings?.youtubeOAuthClientId.orEmpty())
    }
    var workerUrl by remember(settings?.workerBaseUrl) {
        mutableStateOf(settings?.workerBaseUrl.orEmpty())
    }
    var billingUrl by remember(settings?.billingConnectedUrl) {
        mutableStateOf(settings?.billingConnectedUrl.orEmpty())
    }
    var timezone by remember(settings?.timezone) {
        mutableStateOf(settings?.timezone ?: "Asia/Kolkata")
    }
    var durationText by remember(settings?.defaultLiveDurationMin) {
        mutableStateOf((settings?.defaultLiveDurationMin ?: 60).toString())
    }

    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showRevokeOAuthConfirm by remember { mutableStateOf(false) }
    var disconnectChannelTarget by remember { mutableStateOf<YouTubeChannel?>(null) }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Confirm Secure Sign Out") },
            text = { Text("Sign out of DREAMIIT28 on this device, revoke the active session, and clear local OAuth tokens?") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showRevokeOAuthConfirm) {
        AlertDialog(
            onDismissRequest = { showRevokeOAuthConfirm = false },
            title = { Text("Revoke YouTube OAuth?") },
            text = { Text("Remove YouTube API access tokens from this device? Scheduled uploads and API fetches will pause until re-authorized.") },
            confirmButton = {
                Button(
                    onClick = {
                        showRevokeOAuthConfirm = false
                        onDisconnectOAuth()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Revoke OAuth") }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeOAuthConfirm = false }) { Text("Cancel") }
            }
        )
    }

    disconnectChannelTarget?.let { ch ->
        AlertDialog(
            onDismissRequest = { disconnectChannelTarget = null },
            title = { Text("Disconnect Channel?") },
            text = { Text("Disconnect '${ch.title}' from DREAMIIT28?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDisconnectChannel(ch.id)
                        disconnectChannelTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson)
                ) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { disconnectChannelTarget = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        containerColor = VoidBlack,
        topBar = {
            TopAppBar(
                title = { Text(strings.settingsTitle, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianSurface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Google Account Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Google Owner Account", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text("Harsh Singh • $userEmail", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { showSignOutConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                            modifier = Modifier.testTag("settings_sign_out_btn")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.signOut)
                        }
                    }
                }
            }

            // YouTube OAuth 2.0 & Multi-Channel Management (Modules 1 & 16)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, ElectricBlue.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("YouTube OAuth 2.0 & Multi-Channel", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(
                            "Enter your Google Cloud Android/Web OAuth Client ID (with redirect com.aistudio.dreamiit28.hskxmw:/oauth2redirect) to authorize YouTube Data, Live Streaming & Analytics APIs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = oauthClientId,
                            onValueChange = { oauthClientId = it },
                            label = { Text("YouTube OAuth 2.0 Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (oauthClientId.isNotBlank()) {
                                        onUpdateSettings(mapOf("youtubeOAuthClientId" to oauthClientId.trim()))
                                        scope.launch {
                                            val authUri = onPrepareOAuthUrl(oauthClientId.trim())
                                            context.startActivity(Intent(Intent.ACTION_VIEW, authUri))
                                        }
                                    }
                                },
                                enabled = oauthClientId.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                modifier = Modifier.weight(1f).testTag("authorize_youtube_oauth_btn")
                            ) {
                                Icon(Icons.Default.AddLink, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Authorize YouTube")
                            }
                            if (oauthCredentials.accessToken.isNotBlank()) {
                                OutlinedButton(onClick = { showRevokeOAuthConfirm = true }) {
                                    Text("Revoke", color = LiveCrimson)
                                }
                            }
                        }
                    }
                }
            }

            // Connected Channels List
            if (channels.isNotEmpty()) {
                item {
                    Text("Connected YouTube Channels", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
                items(channels, key = { it.id }) { ch ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                        border = BorderStroke(1.dp, if (ch.activeChannel) ElectricCyan else BorderSubtle),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ch.title, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text("${ch.subscriberCount} subs • ${ch.videoCount} videos", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            if (ch.activeChannel) {
                                StatusBadge("ACTIVE", SuccessEmerald)
                            } else {
                                OutlinedButton(onClick = { onSwitchChannel(ch.channelId) }) {
                                    Text("Switch")
                                }
                            }
                            IconButton(onClick = { disconnectChannelTarget = ch }) {
                                Icon(Icons.Default.Delete, contentDescription = "Disconnect Channel", tint = LiveCrimson)
                            }
                        }
                    }
                }
            }

            // Language Switcher (Module 21)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = (settings?.language ?: "en") == "en",
                                onClick = { onUpdateSettings(mapOf("language" to "en")) },
                                label = { Text("English") },
                                modifier = Modifier.testTag("lang_en_chip")
                            )
                            FilterChip(
                                selected = settings?.language == "hi",
                                onClick = { onUpdateSettings(mapOf("language" to "hi")) },
                                label = { Text("हिन्दी (Hindi)") },
                                modifier = Modifier.testTag("lang_hi_chip")
                            )
                        }
                    }
                }
            }

            // Streaming Defaults & Cloud Worker Server
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cloud Worker, Billing & Streaming Defaults", style = MaterialTheme.typography.titleMedium, color = Color.White)

                        OutlinedTextField(
                            value = workerUrl,
                            onValueChange = { workerUrl = it },
                            label = { Text("Cloud Streaming Worker URL (https://...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = billingUrl,
                            onValueChange = { billingUrl = it },
                            label = { Text("Connected Billing JSON URL (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("billing_url_input")
                        )

                        Text("Default Resolution", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("1080p", "1080p60", "720p").forEach { res ->
                                FilterChip(
                                    selected = (settings?.defaultResolution ?: "1080p") == res,
                                    onClick = { onUpdateSettings(mapOf("defaultResolution" to res)) },
                                    label = { Text(res) }
                                )
                            }
                        }

                        Text("Default Privacy", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrivacyStatus.entries.forEach { p ->
                                FilterChip(
                                    selected = (settings?.defaultPrivacy ?: "PUBLIC") == p.name,
                                    onClick = { onUpdateSettings(mapOf("defaultPrivacy" to p.name)) },
                                    label = { Text(p.label) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = timezone,
                            onValueChange = { timezone = it },
                            label = { Text("Default Timezone") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = durationText,
                            onValueChange = { durationText = it.filter { c -> c.isDigit() } },
                            label = { Text("Default Live Duration (Minutes)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Smart Push Notifications", color = Color.White)
                            Switch(
                                checked = settings?.notificationsEnabled ?: true,
                                onCheckedChange = { onUpdateSettings(mapOf("notificationsEnabled" to it)) }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Automatic Worker Failure Recovery", color = Color.White)
                            Switch(
                                checked = settings?.autoRecoveryEnabled ?: true,
                                onCheckedChange = { onUpdateSettings(mapOf("autoRecoveryEnabled" to it)) }
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.autoRetryLabel, color = Color.White)
                            Switch(
                                checked = settings?.autoStreamRetryEnabled ?: true,
                                onCheckedChange = { onUpdateSettings(mapOf("autoStreamRetryEnabled" to it)) }
                            )
                        }

                        Button(
                            onClick = {
                                val updates = mutableMapOf<String, Any>(
                                    "timezone" to timezone.trim().ifBlank { "Asia/Kolkata" },
                                    "defaultLiveDurationMin" to (durationText.toIntOrNull()?.coerceIn(5, 720) ?: 60),
                                    "workerBaseUrl" to workerUrl.trim().take(300),
                                    "billingConnectedUrl" to billingUrl.trim().take(300)
                                )
                                if (oauthClientId.isNotBlank()) updates["youtubeOAuthClientId"] = oauthClientId.trim().take(200)
                                onUpdateSettings(updates)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            modifier = Modifier.fillMaxWidth().testTag("save_platform_settings_btn")
                        ) {
                            Text("Save Platform Settings", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

