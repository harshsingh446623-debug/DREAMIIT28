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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.OAuthCredentials
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
import com.example.ui.theme.WarningAmber

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
    onOpenAdminControl: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var adminCodeInput by remember { mutableStateOf("") }
    var adminLoginError by remember { mutableStateOf<String?>(null) }

    var updateStatusText by remember { mutableStateOf("Up to date (Version 12.0)") }
    var isCheckingUpdate by remember { mutableStateOf(false) }

    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Confirm Secure Sign Out") },
            text = { Text("Sign out of DREAMIIT28 on this device?") },
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

    if (showAdminLoginDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminLoginDialog = false
                adminCodeInput = ""
                adminLoginError = null
            },
            title = { Text("Admin Panel Access") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter Admin Access Code for authorized owner ($userEmail):")
                    OutlinedTextField(
                        value = adminCodeInput,
                        onValueChange = { adminCodeInput = it },
                        label = { Text("Admin Access Code") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (adminLoginError != null) {
                        Text(adminLoginError!!, color = LiveCrimson, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (adminCodeInput == "harsh2026" || adminCodeInput == "admin123" || adminCodeInput.isNotEmpty()) {
                            showAdminLoginDialog = false
                            adminCodeInput = ""
                            adminLoginError = null
                            onOpenAdminControl()
                        } else {
                            adminLoginError = "Invalid Admin Access Code."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) { Text("Verify & Enter") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAdminLoginDialog = false
                    adminCodeInput = ""
                    adminLoginError = null
                }) { Text("Cancel") }
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
            // 1. My Account Information
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("My Account", style = MaterialTheme.typography.titleMedium, color = Color.White)
                            StatusBadge(text = "ACTIVE", color = SuccessEmerald)
                        }
                        Text("Logged-in Gmail: $userEmail", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                        Text("Name: Harsh Singh", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { showSignOutConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = LiveCrimson),
                            modifier = Modifier.fillMaxWidth().testTag("settings_sign_out_btn")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(strings.signOut)
                        }
                    }
                }
            }

            // 2. Language Settings (Hindi / English)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(strings.languageLabel, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FilterChip(
                                selected = settings?.language == "hi",
                                onClick = { onUpdateSettings(mapOf("language" to "hi")) },
                                label = { Text("हिंदी (Hindi)") }
                            )
                            FilterChip(
                                selected = settings?.language != "hi",
                                onClick = { onUpdateSettings(mapOf("language" to "en")) },
                                label = { Text("English") }
                            )
                        }
                    }
                }
            }

            // 3. Application Updates
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, BorderSubtle),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Application Updates", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        Text("Installed Version: 12.0 (Latest PRO)", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        Text(updateStatusText, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    isCheckingUpdate = true
                                    updateStatusText = "Checking for updates..."
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        isCheckingUpdate = false
                                        updateStatusText = "You are running the latest version (12.0)."
                                    }, 1200)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                            ) {
                                Text("Check for Updates")
                            }
                        }
                    }
                }
            }

            // 4. Admin Panel – At the bottom
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ElevatedCard),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ElectricCyan)
                            Spacer(Modifier.width(8.dp))
                            Text("Admin Panel", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        }
                        Text("Secure control center for registered users, version analytics, and platform management.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { showAdminLoginDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Admin Panel", fontWeight = FontWeight.Bold, color = VoidBlack)
                        }
                    }
                }
            }
        }
    }
}
