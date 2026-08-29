package com.domnex.cfi.bridge.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import com.domnex.cfi.bridge.BuildConfig

/**
 * Resolve a versão REALMENTE instalada do DOMNEX BRIDGE a partir do APK (via
 * PackageManager), com fallback para os valores de [BuildConfig].
 *
 * Motivação: o DOMNEX BRIDGE roda um foreground service que pode manter o
 * processo vivo mesmo após uma atualização ser instalada por cima. Nesse caso,
 * os valores COMPILADOS (estáticos) de BuildConfig no DEX já carregado continuam
 * sendo os ANTIGOS até o processo ser reciclado. Consultando o PackageManager a
 * cada verificação/exibição, garantimos que a versão reflete o pacote real mesmo
 * nesse cenário — assim, após instalar 1.0.1/versionCode 2, o app deixa de
 * oferecer a própria atualização.
 *
 * BuildConfig (com.domnex.cfi.bridge.BuildConfig) é usado APENAS como fallback:
 * é a mesma versão embarcada no APK, nunca um valor hardcoded.
 */
object InstalledVersion {

    fun versionCode(context: Context): Int {
        val app = context.applicationContext
        return runCatching {
            val info = packageInfo(app)
            info?.let { PackageInfoCompat.getLongVersionCode(it).toInt() }
        }.getOrNull() ?: BuildConfig.VERSION_CODE
    }

    fun versionName(context: Context): String {
        val app = context.applicationContext
        return runCatching {
            packageInfo(app)?.versionName
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: BuildConfig.VERSION_NAME
    }

    private fun packageInfo(app: Context): android.content.pm.PackageInfo? {
        val pm = app.packageManager
        val name = app.packageName
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(name, 0)
        }
    }
}
