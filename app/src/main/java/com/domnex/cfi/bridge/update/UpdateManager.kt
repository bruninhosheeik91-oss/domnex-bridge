package com.domnex.cfi.bridge.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/**
 * Orquestra o download e a instalação real do APK via mecanismo oficial do
 * Android.
 *
 * MECANISMO DE DOWNLOAD ESCOLHIDO: [DownloadManager] — serviço de download do
 * próprio sistema. É seguro, baixa em segundo plano e lida com conectividade/
 * retomada de forma nativa, sem exigir bibliotecas externas.
 *
 * INSTALAÇÃO: usa [android.content.Intent.ACTION_VIEW] com uma [FileProvider]
 * content URI (nunca `file://` inseguro) apontando para o pacote oficial.
 * O Android abre o instalador padrão — o usuário confirma. NUNCA instalação
 * silenciosa.
 *
 * FONTE DESCONHECIDA: se o app não tiver a permissão "Instalar apps
 * desconhecidos", antes de instalar orientamos o usuário e abrimos a tela
 * oficial de configuração ([Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES]).
 */
class UpdateManager(private val context: Context) {

    private val appContext = context.applicationContext

    /**
     * Agenda o download do APK oficial via DownloadManager.
     * Retorna o `downloadId` para acompanhamento (ou -1 se a agenda falhar).
     */
    fun downloadApk(info: UpdateInfo): Long {
        val request = DownloadManager.Request(Uri.parse(info.apkUrl)).apply {
            setTitle("DOMNEX BRIDGE ${info.latestVersionName}")
            setDescription("Baixando atualização oficial do DOMNEX BRIDGE")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalFilesDir(
                appContext,
                Environment.DIRECTORY_DOWNLOADS,
                APK_FILE_NAME
            )
        }
        return try {
            downloadManager.enqueue(request)
        } catch (_: Exception) {
            -1L
        }
    }

    /**
     * Verifica se o download com [downloadId] foi concluído com sucesso.
     */
    fun isDownloadComplete(downloadId: Long): Boolean {
        val cursor = try {
            downloadManager.query(
                DownloadManager.Query().setFilterById(downloadId)
            )
        } catch (_: Exception) {
            return false
        }
        return cursor.use { c ->
            if (c != null && c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                status == DownloadManager.STATUS_SUCCESSFUL
            } else {
                false
            }
        }
    }

    /**
     * Inicia a instalação do APK baixado usando FileProvider + ACTION_VIEW.
     *
     * Antes, verifica a permissão "Instalar apps desconhecidos". Se não estiver
     * concedida, orienta o usuário e abre a configuração oficial do Android.
     */
    fun installApk(): Boolean {
        val apk = localApkFile()
        if (!apk.exists() || apk.length() <= 0L) return false

        if (!canRequestUnknownSources()) {
            openUnknownSourcesSettings()
            return true
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            FILE_PROVIDER_AUTHORITY,
            apk
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            appContext.startActivity(intent)
        }.isSuccess
    }

    /**
     * true se o app tem permissão para instalar apps de fontes desconhecidas.
     */
    fun canRequestUnknownSources(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.packageManager.canRequestPackageInstalls()
        } else {
            // Antes de O o fluxo era permissão de runtime; tratamos como "ok a
            // pedir" e o instalador cuida da permissão quando a instalação começa.
            true
        }
    }

    private fun openUnknownSourcesSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${appContext.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
    }

    private val downloadManager: DownloadManager
        get() = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun localApkFile(): File =
        File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)

    companion object {
        const val APK_FILE_NAME = "domnex-bridge-update.apk"
        const val FILE_PROVIDER_AUTHORITY = "com.domnex.cfi.bridge.fileprovider"
    }
}
