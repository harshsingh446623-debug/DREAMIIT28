package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.firebase.configureAppCheck
import com.example.ui.DreamiitApp
import com.example.ui.theme.DreamiitTheme

class MainActivity : ComponentActivity() {
    private var pendingOAuthUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before setContent / ViewModels
        configureAppCheck(this, intent)
        captureOAuthRedirect(intent)

        enableEdgeToEdge()
        setContent {
            DreamiitTheme {
                DreamiitApp(
                    pendingOAuthUri = pendingOAuthUri,
                    onConsumeOAuthUri = { pendingOAuthUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureOAuthRedirect(intent)
    }

    private fun captureOAuthRedirect(intent: Intent?) {
        val data = intent?.data
        if (data != null && data.scheme == "com.aistudio.dreamiit28.hskxmw" && data.host == "oauth2redirect") {
            pendingOAuthUri = data
        }
    }
}
