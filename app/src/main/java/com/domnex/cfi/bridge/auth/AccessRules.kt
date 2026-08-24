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
}
