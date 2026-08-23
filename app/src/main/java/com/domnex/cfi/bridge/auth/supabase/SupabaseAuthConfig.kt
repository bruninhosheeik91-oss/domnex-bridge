package com.domnex.cfi.bridge.auth.supabase

data class SupabaseAuthConfig(
    val projectUrl: String,
    val anonKey: String,
    val timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS
) {
    fun isConfigured(): Boolean =
        projectUrl.startsWith("https://") && anonKey.isNotBlank()

    fun authUrl(path: String): String = "$projectUrl/auth/v1$path"

    fun restUrl(path: String): String = "$projectUrl/rest/v1$path"

    fun functionsUrl(name: String): String = "$projectUrl/functions/v1/$name"

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 15_000

        fun fromBuildConfig(projectUrl: String?, anonKey: String?): SupabaseAuthConfig =
            SupabaseAuthConfig(
                projectUrl = projectUrl.orEmpty().trim().trimEnd('/'),
                anonKey = anonKey.orEmpty().trim()
            )
    }
}
