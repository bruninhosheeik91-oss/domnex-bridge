package com.domnex.cfi.bridge.auth

interface AuthGateway {
    fun login(email: String, password: String): AuthResult
    fun logout()
    fun currentSession(): AuthSession?
    fun currentUser(): UserAccount?
}
