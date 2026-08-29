package com.domnex.cfi.bridge.update

import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Camada isolada responsável por consultar a informação de versão publicada e
 * compará-la com a versão realmente instalada no dispositivo.
 *
 * A comparação é baseada NO versionCode (número inteiro), nunca em comparação
 * textual de versionName — versionName é apenas apresentacional.
 *
 * A versão instalada é fornecida por [installedVersionCodeProvider], reavaliada
 * a cada [check] — nunca congelada em uma constante — para que, após uma
 * atualização instalada por cima (mesmo mantendo o processo vivo via foreground
 * service), a comparação reflita o pacote real do dispositivo.
 *
 * Enquanto não houver uma fonte oficial de atualização configurada, este
 * repository devolve [UpdateStatus.NOT_CONFIGURED] (estado normal, sem fingir
 * que existe atualização).
 */
class UpdateRepository(
    private val installedVersionCodeProvider: () -> Int,
    private val httpClient: SupabaseHttpClient? = null,
    private val source: UpdateSource? = null
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Verifica se existe atualização. Método bloqueante — chamar fora da main
     * thread.
     *
     * Ordem de resolução:
     *  1. Sem fonte configurada  -> NOT_CONFIGURED (nada a fazer).
     *  2. Falha de rede/backend  -> ERROR (não quebra o app).
     *  3. Comparar versionCode    -> UP_TO_DATE / OPTIONAL / REQUIRED.
     */
    fun check(): UpdateCheckResult {
        val http = httpClient ?: return UpdateCheckResult.NotConfigured
        val src = source ?: return UpdateCheckResult.NotConfigured
        val installedVersionCode = installedVersionCodeProvider()

        val updates = try {
            fetch(src, http)
        } catch (_: java.io.IOException) {
            return UpdateCheckResult.CheckError("Falha de rede ao consultar atualizações.")
        }

        // null = resposta inválida / status não-2xx / RLS bloqueou (anon).
        if (updates == null) {
            return UpdateCheckResult.CheckError("Resposta inválida do serviço de atualizações.")
        }

        // Lista vazia = ainda não existe release publicada (ou a instalada é a
        // mais recente). Sem atualização disponível — estado normal.
        if (updates.isEmpty()) {
            return UpdateCheckResult.NoUpdate()
        }

        val info = updates.first()

        // A versão instalada está abaixo do mínimo suportado -> obrigatório,
        // independentemente do flag `mandatory` (o serviço não pode seguir).
        if (installedVersionCode < info.minimumVersionCode && info.minimumVersionCode > 0) {
            return UpdateCheckResult.Available(info, required = true)
        }

        // Sem versão mais nova publicada -> atualizado.
        if (info.latestVersionCode <= installedVersionCode) {
            return UpdateCheckResult.NoUpdate(info)
        }

        return UpdateCheckResult.Available(info, required = info.mandatory)
    }

    /**
     * Consulta a fonte e devolve a lista de releases publicadas (na prática, 0
     * ou 1 linhas). `null` indica resposta inválida/não-2xx — o chamador trata
     * como erro. Uma lista vazia equivale a "nenhuma atualização".
     */
    private fun fetch(src: UpdateSource, http: SupabaseHttpClient): List<UpdateInfo>? {
        val response = http.execute(
            HttpRequest(
                url = src.endpoint(),
                method = "GET",
                headers = src.headers()
            )
        )
        if (response.statusCode !in 200..299) return null
        val rows = runCatching {
            json.decodeFromString(ListSerializer(VersionDbRow.serializer()), response.bodyText())
        }.getOrNull() ?: return null
        return rows
            .filter { it.usable }
            .map { it.toUpdateInfo() }
    }
}

/**
 * Fonte oficial da configuração de atualização. A URL do APK vem EXCLUSIVAMENTE
 * daqui (endpoint oficial) — nunca de entrada livre do usuário.
 *
 * Implementações futuras (conectar ao backend DOMNEX sem reescrever a UI):
 *  - `VersionTableUpdateSource` (consultar tabela Supabase via rest/v1);
 *  - `EdgeFunctionUpdateSource` (chamar uma Edge Function dedicada);
 */
interface UpdateSource {
    fun endpoint(): String
    fun headers(): Map<String, String> = emptyMap()
}
