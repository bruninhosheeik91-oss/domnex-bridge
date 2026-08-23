package com.domnex.cfi.bridge.auth

import android.content.Context

object LocalSessionStore {

    private const val PREFS_NAME = "domnex_bridge_ui_session"
    private const val KEY_SESSION_ID = "session_id"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_USER_CLIENT = "user_client_name"

    fun readSession(context: Context): AuthSession? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sessionId = prefs.getString(KEY_SESSION_ID, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val name = prefs.getString(KEY_USER_NAME, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        val role = runCatching {
            UserRole.valueOf(prefs.getString(KEY_USER_ROLE, null) ?: return null)
        }.getOrNull() ?: return null
        val clientName = prefs.getString(KEY_USER_CLIENT, null)

        return AuthSession(
            sessionId = sessionId,
            user = UserAccount(
                id = userId,
                name = name,
                email = email,
                role = role,
                clientName = clientName
            )
        )
    }

    fun saveSession(context: Context, session: AuthSession) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SESSION_ID, session.sessionId)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_USER_NAME, session.user.name)
            .putString(KEY_USER_EMAIL, session.user.email)
            .putString(KEY_USER_ROLE, session.user.role.name)
            .putString(KEY_USER_CLIENT, session.user.clientName)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
