package com.domnex.cfi.bridge.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Controle operacional do monitoramento do Bridge (pausar/ativar).
 *
 * Responsável APENAS pelo estado operacional — NÃO interfere em:
 *  - autenticação/sessão (Supabase Auth);
 *  - configuração de endpoint/token (`cfi_bridge_prefs`);
 *  - permissão de acessibilidade (permanece concedida);
 *  - deduplicação/histórico de vendas já capturadas.
 *
 * Persistência: arquivo próprio `bridge_monitor_prefs`, chave `bridge_enabled`
 * (default true = Bridge ativo). Todos os componentes rodam no mesmo processo,
 * então o [MutableStateFlow] funciona como gate compartilhado e barato para os
 * caminhos quentes (ticker do FGS e eventos de acessibilidade).
 */
object BridgeMonitor {

    private const val PREFS_NAME = "bridge_monitor_prefs"
    const val KEY_ENABLED = "bridge_enabled"

    val enabled = MutableStateFlow(true)

    /** Recarrega o estado persistido (chamado no início do processo). */
    fun refresh(context: Context) {
        enabled.value = readPersisted(context)
    }

    /** Persiste e propaga o novo estado operacional. */
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        enabled.value = value
    }

    /**
     * Gate barato (sem I/O) para interromper o processamento operacional
     * quando o usuário pausou o Bridge.
     */
    fun isActive(): Boolean = enabled.value

    private fun readPersisted(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
