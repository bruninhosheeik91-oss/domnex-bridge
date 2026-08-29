package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.HttpResponse
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import com.domnex.cfi.bridge.update.UpdateCheckResult
import com.domnex.cfi.bridge.update.UpdateRepository
import com.domnex.cfi.bridge.update.UpdateSource
import com.domnex.cfi.bridge.update.UpdateStatus
import com.domnex.cfi.bridge.update.VersionDbRow
import com.domnex.cfi.bridge.update.toUpdateInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRepositoryTest {

    private class FakeHttpClient(
        var handler: ((HttpRequest) -> HttpResponse)? = null
    ) : SupabaseHttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler?.invoke(request) ?: HttpResponse.of(500, "[]")
        }
    }

    private class FakeSource(
        private val endpointUrl: String =
            "https://projeto.supabase.co/rest/v1/domnex_bridge_versions?select=*&published=eq.true&order=version_code.desc&limit=1"
    ) : UpdateSource {
        override fun endpoint(): String = endpointUrl
    }

    private fun okRepo(
        installedCode: Int,
        body: String,
        http: FakeHttpClient = FakeHttpClient()
    ): UpdateRepository {
        http.handler = { _ -> HttpResponse.of(200, body) }
        return UpdateRepository(installedVersionCodeProvider = { installedCode }, httpClient = http, source = FakeSource())
    }

    /** Gera o corpo de resposta real do Supabase REST (array de rows snake_case). */
    private fun row(
        versionName: String = "1.0.1",
        versionCode: Int = 2,
        min: Int = 1,
        apkUrl: String = "https://cdn.domnex.com/domnex-bridge-1.0.1.apk",
        notes: String = "Melhorias de estabilidade.",
        mandatory: Boolean = false,
        published: Boolean = true,
        publishedAt: String? = "2026-08-01T00:00:00Z"
    ): String =
        """{
            "id": "00000000-0000-0000-0000-000000000001",
            "version_name": "$versionName",
            "version_code": $versionCode,
            "minimum_version_code": $min,
            "apk_url": "$apkUrl",
            "release_notes": "$notes",
            "mandatory": $mandatory,
            "published": $published,
            "published_at": ${publishedAt?.let { "\"$it\"" } ?: "null"}
        }"""

    // ------------------------------------------------------------ parser/map

    @Test
    fun `mapeia linha snake_case real para UpdateInfo`() {
        val row = VersionDbRow.deserialize(
            """{"version_name":"1.0.1","version_code":2,"minimum_version_code":1,
                 "apk_url":"https://cdn.domnex.com/a.apk","release_notes":"ok",
                 "mandatory":true,"published":true,"published_at":"2026-08-01T00:00:00Z"}"""
        )
        val info = row.toUpdateInfo()
        assertEquals("1.0.1", info.latestVersionName)
        assertEquals(2, info.latestVersionCode)
        assertEquals(1, info.minimumVersionCode)
        assertEquals("https://cdn.domnex.com/a.apk", info.apkUrl)
        assertEquals("ok", info.releaseNotes)
        assertTrue(info.mandatory)
        assertEquals("2026-08-01T00:00:00Z", info.publishedAt)
    }

    @Test
    fun `linha nao publicada nao e utilizavel`() {
        val row = VersionDbRow.deserialize(
            """{"version_name":"1.0.1","version_code":2,"apk_url":"https://cdn.domnex.com/a.apk",
                 "published":false}"""
        )
        assertTrue(!row.usable)
    }

    // ------------------------------------------------------------ repositorio

    @Test
    fun `lista vazia vira UP_TO_DATE (nenhuma release publicada)`() {
        val http = FakeHttpClient()
        val repo = okRepo(installedCode = 1, body = "[]", http = http)

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.NoUpdate)
        assertEquals(UpdateStatus.UP_TO_DATE, result.status)
    }

    @Test
    fun `release code 1 com app code 1 vira UP_TO_DATE`() {
        val http = FakeHttpClient()
        val repo = okRepo(
            installedCode = 1,
            body = "[${row(versionName = "1.0", versionCode = 1)}]",
            http = http
        )

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.NoUpdate)
        assertEquals(UpdateStatus.UP_TO_DATE, result.status)
    }

    @Test
    fun `release code 2 com app code 1 mandatory false vira OPTIONAL_UPDATE`() {
        val http = FakeHttpClient()
        val repo = okRepo(
            installedCode = 1,
            body = "[${row(versionName = "1.0.1", versionCode = 2)}]",
            http = http
        )

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(UpdateStatus.OPTIONAL_UPDATE, result.status)
        assertTrue(result.info!!.isUsable)
    }

    @Test
    fun `release code 2 com app code 1 mandatory true vira REQUIRED_UPDATE`() {
        val http = FakeHttpClient()
        val repo = okRepo(
            installedCode = 1,
            body = "[${row(versionCode = 2, mandatory = true)}]",
            http = http
        )

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(UpdateStatus.REQUIRED_UPDATE, result.status)
    }

    @Test
    fun `release nao publicada nao e retornada pelo repository`() {
        val http = FakeHttpClient()
        // Simula: RLS/REST só devolveria publicadas, mas incluso linha não-publicada
        // para garantir defesa em profundidade.
        val repo = okRepo(
            installedCode = 1,
            body = "[${row(versionCode = 2, published = false)}]",
            http = http
        )

        val result = repo.check()
        // Linha descartada -> lista vazia -> sem update.
        assertTrue(result is UpdateCheckResult.NoUpdate)
        assertEquals(UpdateStatus.UP_TO_DATE, result.status)
    }

    @Test
    fun `minimumVersionCode maior que installed vira REQUIRED_UPDATE independente do mandatory`() {
        val http = FakeHttpClient()
        val repo = okRepo(
            installedCode = 1,
            body = "[${row(versionCode = 2, min = 5)}]",
            http = http
        )

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.Available)
        assertEquals(UpdateStatus.REQUIRED_UPDATE, result.status)
    }

    @Test
    fun `erro de rede vira ERROR sem quebrar`() {
        val http = FakeHttpClient(handler = { _ -> throw java.io.IOException("network down") })
        val repo = UpdateRepository(installedVersionCodeProvider = { 1 }, httpClient = http, source = FakeSource())

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.CheckError)
        assertEquals(UpdateStatus.ERROR, result.status)
    }

    @Test
    fun `erro 5xx vira ERROR`() {
        val http = FakeHttpClient(handler = { _ -> HttpResponse.of(503, "[]") })
        val repo = UpdateRepository(installedVersionCodeProvider = { 1 }, httpClient = http, source = FakeSource())

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.CheckError)
        assertEquals(UpdateStatus.ERROR, result.status)
    }

    @Test
    fun `JSON invalido vira ERROR`() {
        val http = FakeHttpClient(handler = { _ -> HttpResponse.of(200, "not json") })
        val repo = UpdateRepository(installedVersionCodeProvider = { 1 }, httpClient = http, source = FakeSource())

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.CheckError)
        assertEquals(UpdateStatus.ERROR, result.status)
    }

    @Test
    fun `sem fonte configurada vira NOT_CONFIGURED e nao chama backend`() {
        val http = FakeHttpClient()
        val repo = UpdateRepository(installedVersionCodeProvider = { 1 }, httpClient = http, source = null)

        val result = repo.check()
        assertSame(UpdateCheckResult.NotConfigured, result)
        assertEquals(UpdateStatus.NOT_CONFIGURED, result.status)
        assertEquals(0, http.requests.size)
    }

    @Test
    fun `sem http client vira NOT_CONFIGURED`() {
        val repo = UpdateRepository(installedVersionCodeProvider = { 1 }, httpClient = null, source = FakeSource())

        val result = repo.check()
        assertEquals(UpdateStatus.NOT_CONFIGURED, result.status)
    }

    @Test
    fun `compara por versionCode nao por versionName`() {
        // Instalado code 2 com versionName "1.0"; publicado code 2 com versionName "1.0.1".
        // Como o code é igual (2 <= 2), NÃO deve oferecer update — só versionName mudou.
        val http = FakeHttpClient()
        val repo = okRepo(
            installedCode = 2,
            body = "[${row(versionName = "1.0.1", versionCode = 2)}]",
            http = http
        )

        val result = repo.check()
        assertTrue(result is UpdateCheckResult.NoUpdate)
        assertEquals(UpdateStatus.UP_TO_DATE, result.status)
    }

    @Test
    fun `apkUrl nao-https vira resultado nao utilizavel (descarta e fica UP_TO_DATE)`() {
        val http = FakeHttpClient()
        val repo = okRepo(
            installedCode = 1,
            body = "[${row(versionCode = 2, apkUrl = "http://inseguro.com/a.apk")}]",
            http = http
        )

        val result = repo.check()
        // Linha descartada por apk não-HTTPS -> sem update disponível.
        assertTrue(result is UpdateCheckResult.NoUpdate)
        assertEquals(UpdateStatus.UP_TO_DATE, result.status)
    }

    @Test
    fun `resposta sem secrets nao os carrega`() {
        val http = FakeHttpClient()
        val repo = okRepo(
            installedCode = 1,
            body = "[${row(versionCode = 2)}]",
            http = http
        )

        val result = repo.check() as UpdateCheckResult.Available
        val text = result.toString()
        assertTrue(!text.contains("service_role"))
        assertTrue(!text.contains("M2M"))
        assertTrue(!text.contains("bridge_token"))
    }

    @Test
    fun `installedVersionCode e reavaliado a cada check apos instalar atualizacao`() {
        // Cenário real reportado: enquanto o processo ainda mostra a versão antiga,
        // o PackageManager já reflete o APK novo. A versão instalada DEVE ser lida
        // via provider dinâmico, não congelada em constante.
        val http = FakeHttpClient()
        http.handler = { _ ->
            HttpResponse.of(200, "[${row(versionName = "1.0.1", versionCode = 2)}]")
        }
        var installedCode = 1 // versão "antiga" ainda em memória
        val repo = UpdateRepository(
            installedVersionCodeProvider = { installedCode },
            httpClient = http,
            source = FakeSource()
        )

        // Antes de instalar: code 1 < 2 -> oferece update.
        val before = repo.check()
        assertTrue(before is UpdateCheckResult.Available)

        // Após instalar o APK novo (code 2), o provider passa a devolver 2.
        installedCode = 2
        val after = repo.check()
        assertTrue(after is UpdateCheckResult.NoUpdate)
        assertEquals(UpdateStatus.UP_TO_DATE, after.status)
    }
}

/** Helper para desserializar uma [VersionDbRow] em teste. */
private fun VersionDbRow.Companion.deserialize(body: String): VersionDbRow =
    UpdateRepositoryTestJson.decodeFromString(VersionDbRow.serializer(), body)

private val UpdateRepositoryTestJson =
    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
