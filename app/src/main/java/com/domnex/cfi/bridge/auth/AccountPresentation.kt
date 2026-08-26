package com.domnex.cfi.bridge.auth

/**
 * Apresentação PURA da Conta (testável em JVM). Converte dados reais da
 * sessão em rótulos de UI — nunca expõe id/UUID, tokens ou credenciais.
 */
object AccountPresentation {

    /** CLIENT → "Cliente"; DOMNEX_ADMIN → "Administrador Domnex". */
    fun roleLabel(role: UserRole?): String = when (role) {
        UserRole.DOMNEX_ADMIN -> "Administrador Domnex"
        UserRole.CLIENT -> "Cliente"
        null -> "Cliente"
    }

    /** Status real da conta; null quando desconhecido (UI omite a linha). */
    fun statusLabel(status: UserStatus?): String? = when (status) {
        UserStatus.ACTIVE -> "Ativo"
        UserStatus.PENDING -> "Pendente"
        UserStatus.SUSPENDED -> "Suspenso"
        null -> null
    }

    /**
     * Nome do cliente/organização vinculada — somente para CLIENT e somente
     * quando a informação realmente existe. Para DOMNEX_ADMIN retorna null
     * (nunca inventar cliente vinculado). NUNCA retorna o id/UUID.
     */
    fun linkedClientLabel(account: UserAccount?): String? {
        if (account == null) return null
        if (account.role != UserRole.CLIENT) return null
        val name = account.clientName?.trim().orEmpty()
        return name.ifEmpty { null }
    }
}
