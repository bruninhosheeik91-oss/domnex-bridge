package com.domnex.cfi.bridge.service

import android.content.Context
import android.util.Log
import com.domnex.cfi.bridge.BuildConfig
import com.domnex.cfi.bridge.model.SaleData
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SaleSender {

    private const val TAG = "CFIBridge"
    private const val PREFS_NAME = "cfi_bridge_prefs"
    private const val KEY_BASE_URL = "api_base_url"
    private const val KEY_BRIDGE_TOKEN = "bridge_token"
    private const val KEY_PENDING = "pending_sales"
    private const val KEY_SENT = "sent_tx_codes"
    private const val DEFAULT_BASE_URL = ""
    private const val DEFAULT_BRIDGE_TOKEN = ""
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 10_000

    fun getBaseUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, url).apply()
    }

    fun getBridgeToken(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BRIDGE_TOKEN, DEFAULT_BRIDGE_TOKEN) ?: DEFAULT_BRIDGE_TOKEN
    }

    fun setBridgeToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BRIDGE_TOKEN, token).apply()
    }

    fun sendSale(context: Context, sale: SaleData) {
        val txCode = sale.codigoTransacao
        if (txCode.isEmpty()) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sent = prefs.getStringSet(KEY_SENT, emptySet()) ?: emptySet()
        if (txCode in sent) return

        if (getBaseUrl(context).isBlank()) {
            Log.w(TAG, "Endpoint CFI não configurado")
            TonAccessibilityService.lastLog.value = "Endpoint CFI não configurado"
            savePending(context, sale)
            return
        }

        if (getBridgeToken(context).isBlank()) {
            Log.w(TAG, "Bridge token não configurado")
            TonAccessibilityService.lastLog.value = "Bridge token não configurado"
            savePending(context, sale)
            return
        }

        Thread {
            Log.i(TAG, "Enviando venda...")
            TonAccessibilityService.lastLog.value = "Enviando venda..."
            val json = buildPayload(sale)
            val code = doPost(context, json)
            when (code) {
                200, 201 -> {
                    Log.i(TAG, "Venda enviada com sucesso ($code)")
                    TonAccessibilityService.lastLog.value = "Venda enviada com sucesso"
                    val updated = sent.toMutableSet()
                    updated.add(txCode)
                    prefs.edit().putStringSet(KEY_SENT, updated).apply()
                }
                202 -> {
                    Log.i(TAG, "Serial aguardando vínculo")
                    TonAccessibilityService.lastLog.value = "Serial aguardando vínculo"
                    savePending(context, sale)
                }
                else -> {
                    Log.w(TAG, "Falha no envio (HTTP $code)")
                    TonAccessibilityService.lastLog.value = "Falha no envio (HTTP $code)"
                    savePending(context, sale)
                }
            }
        }.start()
    }

    private fun doPost(context: Context, json: JSONObject): Int {
        val conn: HttpURLConnection = try {
            URL(getBaseUrl(context)).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            Log.e(TAG, "Falha no envio", e)
            return -1
        }
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            val token = getBridgeToken(context)
            if (token.isNotBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            conn.doOutput = true
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT

            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }

            conn.responseCode
        } catch (e: Exception) {
            Log.e(TAG, "Falha no envio", e)
            -1
        } finally {
            conn.disconnect()
        }
    }

    private fun parseCurrency(value: String): Double {
        return try {
            value.replace("R$", "")
                .replace(" ", "")
                .replace(".", "")
                .replace(",", ".")
                .trim()
                .toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    private fun buildPayload(sale: SaleData): JSONObject {
        return JSONObject().apply {
            put("provider", "TON")
            put("amount", parseCurrency(sale.valorVenda))
            put("occurredAt", sale.dataHora)
            put("status", sale.situacao)
            put("netAmount", parseCurrency(sale.totalReceber))
            put("fee", parseCurrency(sale.taxaVenda))
            put("paymentMethod", sale.formaPagamento)
            put("brand", sale.bandeira)
            put("captureMethod", sale.meioCaptura)
            put("serialNumber", sale.numeroSerie)
            put("transactionCode", sale.codigoTransacao)
            put("authorizationCode", sale.codigoAutorizacao)
        }
    }

    private fun savePending(context: Context, sale: SaleData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getString(KEY_PENDING, "[]") ?: "[]"
        val array = JSONArray(pending)

        val txCode = sale.codigoTransacao
        for (i in 0 until array.length()) {
            if (array.getJSONObject(i).optString("transactionCode") == txCode) return
        }

        array.put(buildPayload(sale))
        prefs.edit().putString(KEY_PENDING, array.toString()).apply()
    }

    fun retryPending(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getString(KEY_PENDING, null) ?: return
        val array = JSONArray(pending)
        if (array.length() == 0) return

        if (getBaseUrl(context).isBlank()) {
            Log.w(TAG, "Endpoint CFI não configurado — retry adiado")
            return
        }

        if (getBridgeToken(context).isBlank()) {
            Log.w(TAG, "Bridge token não configurado — retry adiado")
            return
        }

        Thread {
            val retryArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val txCode = obj.optString("transactionCode", "")
                val code = doPost(context, obj)
                when (code) {
                    200, 201 -> {
                        val sent = prefs.getStringSet(KEY_SENT, emptySet())?.toMutableSet()
                            ?: mutableSetOf()
                        sent.add(txCode)
                        prefs.edit().putStringSet(KEY_SENT, sent).apply()
                    }
                    202 -> {
                        if (BuildConfig.DEBUG) Log.i(TAG, "Serial aguardando vínculo ($txCode)")
                        retryArray.put(obj)
                    }
                    else -> {
                        retryArray.put(obj)
                    }
                }
            }
            prefs.edit().putString(KEY_PENDING, retryArray.toString()).apply()
        }.start()
    }
}
