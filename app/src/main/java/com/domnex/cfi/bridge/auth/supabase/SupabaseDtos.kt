package com.domnex.cfi.bridge.auth.supabase

import com.domnex.cfi.bridge.auth.UserAccount
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class SupabaseTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: SupabaseAuthUser? = null
) {
    fun expiresAtEpochSeconds(fallbackNowEpochSeconds: Long): Long =
        expiresAt ?: (fallbackNowEpochSeconds + expiresIn)
}

@Serializable
data class SupabaseAuthUser(
    val id: String,
    val email: String? = null
)

@Serializable
data class BridgeProfileRow(
    val id: String,
    val name: String,
    val email: String,
    val role: String,
    @SerialName("client_id") val clientId: String? = null,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    val client: BridgeClientEmbed? = null
) {
    @Serializable
    data class BridgeClientEmbed(val name: String? = null)
}

@Serializable
data class BridgeClientRow(
    val id: String,
    val name: String
)

fun BridgeProfileRow.toUserAccount(): UserAccount {
    val createdAtMillis = runCatching { OffsetDateTime.parse(createdAt).toInstant().toEpochMilli() }
        .getOrDefault(0L)
    return UserAccount(
        id = id,
        name = name.ifBlank { email.substringBefore("@") },
        email = email,
        role = UserRole.valueOf(role),
        clientName = client?.name ?: clientId,
        status = parseStatus(status),
        createdAtMillis = createdAtMillis
    )
}

private fun parseStatus(raw: String): UserStatus =
    runCatching { UserStatus.valueOf(raw) }.getOrDefault(UserStatus.PENDING)
