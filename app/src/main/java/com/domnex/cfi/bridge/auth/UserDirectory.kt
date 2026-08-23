package com.domnex.cfi.bridge.auth

enum class AccessFilter {
    ALL,
    CLIENTS,
    ADMINS,
    SUSPENDED
}

sealed interface CreateUserOutcome {
    data class Created(val user: UserAccount) : CreateUserOutcome
    data object EmailInUse : CreateUserOutcome
    data class Failed(val message: String) : CreateUserOutcome
}

interface UserDirectory {
    fun listUsers(query: String = "", filter: AccessFilter = AccessFilter.ALL): List<UserAccount>
    fun getUser(userId: String): UserAccount?
    fun findClientNames(): List<String>
    fun createAccess(
        name: String,
        email: String,
        role: UserRole,
        clientName: String?,
        status: UserStatus
    ): CreateUserOutcome

    fun setStatus(userId: String, status: UserStatus): Boolean
}
