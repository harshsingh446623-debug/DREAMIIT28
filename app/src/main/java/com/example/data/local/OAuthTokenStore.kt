package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.oauthDataStore by preferencesDataStore(name = "dreamiit28_oauth_store")

data class OAuthCredentials(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val scope: String = "",
    val connectedEmail: String = "",
    val codeVerifier: String = "",
    val workerApiToken: String = ""
) {
    val hasValidToken: Boolean
        get() = accessToken.isNotBlank() && System.currentTimeMillis() < expiresAtEpochMs - 60_000L
}

class OAuthTokenStore(private val context: Context) {
    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("yt_access_token")
        val REFRESH_TOKEN = stringPreferencesKey("yt_refresh_token")
        val EXPIRES_AT = longPreferencesKey("yt_expires_at")
        val SCOPE = stringPreferencesKey("yt_scope")
        val EMAIL = stringPreferencesKey("yt_connected_email")
        val CODE_VERIFIER = stringPreferencesKey("yt_pkce_verifier")
        val WORKER_API_TOKEN = stringPreferencesKey("worker_api_bearer_token")
    }

    val credentialsFlow: Flow<OAuthCredentials> = context.oauthDataStore.data.map { prefs ->
        OAuthCredentials(
            accessToken = prefs[Keys.ACCESS_TOKEN].orEmpty(),
            refreshToken = prefs[Keys.REFRESH_TOKEN].orEmpty(),
            expiresAtEpochMs = prefs[Keys.EXPIRES_AT] ?: 0L,
            scope = prefs[Keys.SCOPE].orEmpty(),
            connectedEmail = prefs[Keys.EMAIL].orEmpty(),
            codeVerifier = prefs[Keys.CODE_VERIFIER].orEmpty(),
            workerApiToken = prefs[Keys.WORKER_API_TOKEN].orEmpty()
        )
    }

    suspend fun getSnapshot(): OAuthCredentials = credentialsFlow.first()

    suspend fun savePkceVerifier(verifier: String) {
        context.oauthDataStore.edit { it[Keys.CODE_VERIFIER] = verifier }
    }

    suspend fun saveWorkerApiToken(token: String) {
        context.oauthDataStore.edit { prefs ->
            if (token.isBlank()) prefs.remove(Keys.WORKER_API_TOKEN)
            else prefs[Keys.WORKER_API_TOKEN] = token.trim()
        }
    }

    suspend fun saveScheduleIngestUrl(scheduleId: String, rawIngestUrl: String) {
        if (scheduleId.isBlank() || rawIngestUrl.isBlank()) return
        val key = stringPreferencesKey("ingest_$scheduleId")
        context.oauthDataStore.edit { it[key] = rawIngestUrl.trim() }
    }

    suspend fun getScheduleIngestUrl(scheduleId: String): String? {
        if (scheduleId.isBlank()) return null
        val key = stringPreferencesKey("ingest_$scheduleId")
        return context.oauthDataStore.data.first()[key]
    }

    suspend fun clearScheduleIngestUrl(scheduleId: String) {
        if (scheduleId.isBlank()) return
        val key = stringPreferencesKey("ingest_$scheduleId")
        context.oauthDataStore.edit { it.remove(key) }
    }

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSec: Long,
        scope: String,
        email: String = ""
    ) {
        val expiry = System.currentTimeMillis() + (expiresInSec * 1000L)
        context.oauthDataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            if (!refreshToken.isNullOrBlank()) {
                prefs[Keys.REFRESH_TOKEN] = refreshToken
            }
            prefs[Keys.EXPIRES_AT] = expiry
            prefs[Keys.SCOPE] = scope
            if (email.isNotBlank()) prefs[Keys.EMAIL] = email
            prefs.remove(Keys.CODE_VERIFIER)
        }
    }

    suspend fun clearAll() {
        context.oauthDataStore.edit { it.clear() }
    }
}
