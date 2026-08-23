package com.domnex.cfi.bridge.auth.supabase

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null
) {
    fun equalsIgnoringBody(other: Any?): Boolean =
        other is HttpRequest &&
            url == other.url &&
            method == other.method &&
            headers == other.headers
}

class HttpResponse(
    val statusCode: Int,
    val body: ByteArray
) {
    fun bodyText(): String = String(body, Charsets.UTF_8)

    companion object {
        fun of(statusCode: Int, text: String): HttpResponse =
            HttpResponse(statusCode, text.toByteArray(Charsets.UTF_8))
    }
}

interface SupabaseHttpClient {
    fun execute(request: HttpRequest): HttpResponse
}

class HttpUrlConnectionSupabaseClient(
    private val timeoutMillis: Int = SupabaseAuthConfig.DEFAULT_TIMEOUT_MILLIS
) : SupabaseHttpClient {

    @Throws(IOException::class)
    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method.uppercase()
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (request.method.uppercase() == "GET") {
                connection.doOutput = false
            } else {
                connection.doOutput = true
                val body = request.body ?: ByteArray(0)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { output -> output.write(body) }
            }
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return HttpResponse(statusCode, bytes)
        } finally {
            connection.disconnect()
        }
    }
}
