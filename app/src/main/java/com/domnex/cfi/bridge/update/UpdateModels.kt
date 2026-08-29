package com.domnex.cfi.bridge.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Informações de versão publicada, conforme vêm da fonte oficial de atualização
 * (futuramente uma tabela/endpoint do backend DOMNEX).
 *
 * Nenhum dado é inventado aqui: tudo vem do JSON saneado do servidor. A URL do
 * APK é fornecida exclusivamente por esta configuração oficial — nunca por
 * input livre do usuário.
 */
@Serializable
data class UpdateInfo(
    val latestVersionName: String = "",
    val latestVersionCode: Int = 0,
    val minimumVersionCode: Int = 0,
    val apkUrl: String = "",
    val releaseNotes: String = "",
    val mandatory: Boolean = false,
    val publishedAt: String? = null
) {
    val isUsable: Boolean
        get() = latestVersionCode > 0 && apkUrl.startsWith("https://")
}

/**
 * Linha real da tabela `domnex_bridge_versions` retornada pelo Supabase REST
 * com a chave anon/publishable. Campos em snake_case conforme a coluna do
 * banco; mapeados para o modelo de domínio [UpdateInfo].
 */
@Serializable
data class VersionDbRow(
    val id: String? = null,
    @SerialName("version_name") val versionName: String = "",
    @SerialName("version_code") val versionCode: Int = 0,
    @SerialName("minimum_version_code") val minimumVersionCode: Int = 0,
    @SerialName("apk_url") val apkUrl: String = "",
    @SerialName("release_notes") val releaseNotes: String = "",
    val mandatory: Boolean = false,
    val published: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null
) {
    val usable: Boolean
        get() = published && versionCode > 0 && apkUrl.startsWith("https://")
}

/**
 * Mapeia a linha real da tabela para o modelo de domínio [UpdateInfo].
 */
fun VersionDbRow.toUpdateInfo(): UpdateInfo =
    UpdateInfo(
        latestVersionName = versionName,
        latestVersionCode = versionCode,
        minimumVersionCode = minimumVersionCode,
        apkUrl = apkUrl,
        releaseNotes = releaseNotes,
        mandatory = mandatory,
        publishedAt = publishedAt
    )

/**
 * Resultado da comparação entre a versão instalada e a publicada.
 *
 *  - [UP_TO_DATE]: instalada é a mais recente publicada — nada a mostrar.
 *  - [OPTIONAL_UPDATE]: há versão mais nova, atualização opcional.
 *  - [REQUIRED_UPDATE]: a instalada está abaixo do mínimo suportado, ou a
 *    atualização foi marcada como obrigatória — o serviço não deve seguir sem ela.
 *  - [NOT_CONFIGURED]: ainda não existe fonte de atualização configurada neste
 *    build. Estado normal enquanto o backend não for preparado — NUNCA falsa
 *    atualização disponível.
 *  - [ERROR]: falha real (rede/backend) ao consultar. Não quebra o app.
 */
enum class UpdateStatus {
    UP_TO_DATE,
    OPTIONAL_UPDATE,
    REQUIRED_UPDATE,
    NOT_CONFIGURED,
    ERROR
}

sealed interface UpdateCheckResult {
    val status: UpdateStatus
    val info: UpdateInfo?

    data class NoUpdate(override val info: UpdateInfo? = null) : UpdateCheckResult {
        override val status: UpdateStatus = UpdateStatus.UP_TO_DATE
    }

    data class Available(
        override val info: UpdateInfo,
        val required: Boolean
    ) : UpdateCheckResult {
        override val status: UpdateStatus =
            if (required) UpdateStatus.REQUIRED_UPDATE else UpdateStatus.OPTIONAL_UPDATE
    }

    data object NotConfigured : UpdateCheckResult {
        override val status: UpdateStatus = UpdateStatus.NOT_CONFIGURED
        override val info: UpdateInfo? = null
    }

    data class CheckError(val message: String, override val info: UpdateInfo? = null) : UpdateCheckResult {
        override val status: UpdateStatus = UpdateStatus.ERROR
    }
}
