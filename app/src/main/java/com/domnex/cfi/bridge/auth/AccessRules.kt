package com.domnex.cfi.bridge.auth

/**
 * Regras de vínculo papel/status/cliente — espelho puro (testável) das
 * constraints aplicadas no servidor:
 *
 *   * DOMNEX_ADMIN não tem cliente vinculado;
 *   * CLIENT PENDING pode existir sem client_id;
 *   * CLIENT ACTIVE/SUSPENDED exige client_id real.
 *
 * O servidor continua sendo a autoridade final (RPC/Edge Function + constraint
 * do banco); esta validação apenas evita viagens inúteis e dá feedback imediato.
 */
object AccessRules {

    const val ADMIN_MUST_NOT_HAVE_CLIENT = "DOMNEX_ADMIN não deve ter cliente vinculado."
    const val CLIENT_REQUIRES_CLIENT = "CLIENT ativo ou suspenso exige cliente vinculado."

    /**
     * Limites da senha inicial criada pelo administrador. O teto de 72 bytes
     * acompanha o limite do bcrypt usado pelo Supabase Auth (GoTrue).
     */
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 72

    const val PASSWORD_REQUIRED =
        "Defina uma senha inicial com pelo menos $MIN_PASSWORD_LENGTH caracteres, ou deixe os campos vazios."
    const val PASSWORD_TOO_SHORT =
        "A senha inicial deve ter pelo menos $MIN_PASSWORD_LENGTH caracteres."
    const val PASSWORD_TOO_LONG =
        "A senha inicial deve ter no máximo $MAX_PASSWORD_LENGTH caracteres."
    const val PASSWORD_MISMATCH = "As senhas não coincidem."

    /** Retorna null quando a combinação é válida; caso contrário, a mensagem do problema. */
    fun validate(role: UserRole, status: UserStatus, clientId: String?): String? {
        val boundClient = clientId?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            role == UserRole.DOMNEX_ADMIN && boundClient != null -> ADMIN_MUST_NOT_HAVE_CLIENT
            role == UserRole.CLIENT &&
                status != UserStatus.PENDING &&
                boundClient == null -> CLIENT_REQUIRES_CLIENT
            else -> null
        }
    }

    /**
     * Valida UMA senha isoladamente (sem comparar com a confirmação).
     * Senha vazia é válida aqui: significa "usar convite por e-mail".
     * Retorna null quando válida; caso contrário, a mensagem do problema.
     */
    fun passwordIssue(password: String): String? = when {
        password.isEmpty() -> null
        password.length < MIN_PASSWORD_LENGTH -> PASSWORD_TOO_SHORT
        password.length > MAX_PASSWORD_LENGTH -> PASSWORD_TOO_LONG
        else -> null
    }

    /**
     * Valida o par senha + confirmação. Ambos vazios = fluxo de convite.
     * Retorna null quando válidos; caso contrário, a mensagem do problema.
     */
    fun initialPasswordPairIssue(password: String, confirmation: String): String? {
        if (password.isEmpty() && confirmation.isEmpty()) return null
        return passwordIssue(password)
            ?: if (password != confirmation) PASSWORD_MISMATCH else null
    }
}

/**
 * Regras de confirmação forte para operações destrutivas ("Zona de risco"):
 * o texto digitado precisa ser EXATAMENTE igual ao nome real — sem trim,
 * sem ignorar maiúsculas/minúsculas. Qualquer diferença bloqueia a ação.
 */
object ClientDeletionConfirmation {

    fun matches(typedName: String, realName: String): Boolean =
        typedName.isNotEmpty() && typedName == realName
}
