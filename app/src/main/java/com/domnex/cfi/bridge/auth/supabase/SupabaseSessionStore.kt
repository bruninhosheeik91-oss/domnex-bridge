package com.domnex.cfi.bridge.auth.supabase

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredSession(
    val accessToken: String,
    val refreshToken: String,
    @SerialName("expires_at_epoch_seconds") val expiresAtEpochSeconds: Long,
    val user: StoredUserSnapshot
)

@Serializable
data class StoredUserSnapshot(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    @SerialName("client_name") val clientName: String? = null,
    val status: String,
    @SerialName("created_at_millis") val createdAtMillis: Long
)

interface SupabaseSessionStore {
    fun load(): StoredSession?
    fun save(session: StoredSession)
    fun clear()
}

class PrefsSupabaseSessionStore(context: Context) : SupabaseSessionStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): StoredSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString<StoredSession>(raw) }.getOrNull()
    }

    override fun save(session: StoredSession) {
        prefs.edit().putString(KEY_SESSION, json.encodeToString(StoredSession.serializer(), session)).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "domnex_bridge_supabase_session"
        const val KEY_SESSION = "session_json"
    }
}
