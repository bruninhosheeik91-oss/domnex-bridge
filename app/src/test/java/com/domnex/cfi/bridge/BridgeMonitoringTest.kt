package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.auth.supabase.HttpRequest
import com.domnex.cfi.bridge.auth.supabase.HttpResponse
import com.domnex.cfi.bridge.auth.supabase.SupabaseAuthConfig
import com.domnex.cfi.bridge.auth.supabase.SupabaseHttpClient
import com.domnex.cfi.bridge.monitoring.BridgeActivityStatus
import com.domnex.cfi.bridge.monitoring.BridgeCredentialStatus
import com.domnex.cfi.bridge.monitoring.BridgeMonitoringRepository
import com.domnex.cfi.bridge.monitoring.BridgeMonitoringResponse
import com.domnex.cfi.bridge.monitoring.BridgeMonitoringResult
import com.domnex.cfi.bridge.monitoring.BridgeMonitoringUiModel
import com.domnex.cfi.bridge.monitoring.activityStatus
import com.domnex.cfi.bridge.monitoring.credentialStatus
import com.domnex.cfi.bridge.monitoring.formatBridgeTimestamp
import com.domnex.cfi.bridge.monitoring.maskTransactionCode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeMonitoringTest {

    private class FakeHttpClient(
        var handler: ((HttpRequest) -> HttpResponse)? = null
    ) : SupabaseHttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return handler?.invoke(request) ?: HttpResponse.of(500, """{"error":"no_handler"}""")
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val config = SupabaseAuthConfig(
        projectUrl = "https://projeto.supabase.co",
        anonKey = "anon-test-key"
    )

    // ------------------------------------------------------------ modelos

    @Test
    fun `resposta com summary aninhado parseia KPIs reais do servidor`() {
        val body = """
            {
              "summary": {
                "totalBridges": 1,
                "activeBridges": 1,
                "suspendedBridges": 0,
                "revokedBridges": 0,
                "ingestsToday": 0
              },
              "bridges": [
                {
                  "bridgeId":"b-1","bridgeName":"Bridge Financeiro Principal",
                  "organizationId":"o-1","organizationName":"adbras",
                  "status":"ACTIVE","sistemaDestino":"CFI",
                  "lastActivityAt":"2026-08-26T10:30:00Z",
                  "lastSerialNumber":"SN8821","lastTransactionCode":"20260826001234",
                  "totalIngests":40,"ingestsToday":0,"successfulIngests":40,
                  "pendingMappingCount":0,"failedIngests":0,"activityStatus":"RECENT"
                }
              ]
            }
        """.trimIndent()
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val result = repo.load()
        assertTrue(result is BridgeMonitoringResult.Success)
        val model = BridgeMonitoringUiModel.from((result as BridgeMonitoringResult.Success).data)

        assertEquals(1, model.total)
        assertEquals(1, model.active)
        assertEquals(0, model.attention)
        assertEquals(0, model.ingestsToday)
        assertEquals(1, model.bridges.size)
        assertEquals(BridgeCredentialStatus.ACTIVE, model.bridges[0].credentialStatus())
        assertFalse(model.empty)
    }

    @Test
    fun `resposta sem summary cai no fallback dos contadores da raiz`() {
        val body = """
            {
              "totalBridges":1,"activeBridges":1,"suspendedBridges":0,"revokedBridges":0,
              "ingestsToday":0,
              "bridges":[{"bridgeId":"b-1","bridgeName":"B","status":"ACTIVE"}]
            }
        """.trimIndent()
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val model = BridgeMonitoringUiModel.from(
            (repo.load() as BridgeMonitoringResult.Success).data
        )
        assertEquals(1, model.total)
        assertEquals(1, model.active)
        assertEquals(0, model.attention)
        assertEquals(0, model.ingestsToday)
    }

    @Test
    fun `resposta vazia parseia e vira estado vazio`() {
        val http = FakeHttpClient { _ ->
            HttpResponse.of(200, """{"totalBridges":0,"bridges":[]}""")
        }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val result = repo.load()
        assertTrue(result is BridgeMonitoringResult.Success)
        val model = BridgeMonitoringUiModel.from((result as BridgeMonitoringResult.Success).data)
        assertEquals(0, model.total)
        assertTrue(model.empty)
    }

    @Test
    fun `bridge ACTIVE parseia com KPIs do resumo`() {
        val body = """
            {
              "totalBridges":1,"activeBridges":1,"suspendedBridges":0,"revokedBridges":0,
              "recentActivityBridges":1,"noRecentActivityBridges":0,"neverUsedBridges":0,
              "pendingMappingTotal":2,"ingestsToday":3,
              "bridges":[
                {
                  "bridgeId":"b-1","bridgeName":"Bridge Padaria","organizationId":"o-1",
                  "organizationName":"Padaria Estrela","status":"ACTIVE",
                  "sistemaDestino":"TOTVS","createdAt":"2026-08-01T12:00:00Z",
                  "revokedAt":null,"lastActivityAt":"2026-08-26T10:30:00Z",
                  "lastSerialNumber":"SN8821","lastTransactionCode":"20260826001234",
                  "totalIngests":40,"ingestsToday":3,"successfulIngests":38,
                  "pendingMappingCount":2,"failedIngests":1,"activityStatus":"RECENT"
                }
              ]
            }
        """.trimIndent()
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val result = repo.load()
        assertTrue(result is BridgeMonitoringResult.Success)
        val model = BridgeMonitoringUiModel.from((result as BridgeMonitoringResult.Success).data)
        assertEquals(1, model.total)
        assertEquals(1, model.active)
        assertEquals(0, model.attention)
        assertEquals(3, model.ingestsToday)
        assertEquals(1, model.bridges.size)

        val row = model.bridges[0]
        assertEquals(BridgeCredentialStatus.ACTIVE, row.credentialStatus())
        assertEquals(BridgeActivityStatus.RECENT, row.activityStatus())
        assertEquals("•••• 1234", maskTransactionCode(row.lastTransactionCode))
        assertFalse(maskTransactionCode(row.lastTransactionCode).contains("20260826001234"))
    }

    @Test
    fun `bridge SUSPENDED fica como requis atenção`() {
        val body = """
            {"totalBridges":1,"suspendedBridges":1,
              "bridges":[{"bridgeName":"B","status":"SUSPENDED","activityStatus":"NO_RECENT"}]}
        """.trimIndent()
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val model = BridgeMonitoringUiModel.from(
            (repo.load() as BridgeMonitoringResult.Success).data
        )
        assertEquals(1, model.attention)
        assertEquals(BridgeCredentialStatus.SUSPENDED, model.bridges[0].credentialStatus())
        assertEquals(BridgeActivityStatus.NO_RECENT, model.bridges[0].activityStatus())
    }

    @Test
    fun `bridge REVOKED fica como requis atenção`() {
        val body = """
            {"totalBridges":1,"revokedBridges":1,
              "bridges":[{"bridgeName":"B","status":"REVOKED","activityStatus":"NO_RECENT"}]}
        """.trimIndent()
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val model = BridgeMonitoringUiModel.from(
            (repo.load() as BridgeMonitoringResult.Success).data
        )
        assertEquals(1, model.attention)
        assertEquals(BridgeCredentialStatus.REVOKED, model.bridges[0].credentialStatus())
    }

    @Test
    fun `atividade antiga sem activityStatus vira sem atividade recente`() {
        val body = """{"totalBridges":1,
          "bridges":[{"bridgeName":"B","status":"ACTIVE","lastActivityAt":"2026-08-01T00:00:00Z","activityStatus":"NO_RECENT"}]}"""
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val model = BridgeMonitoringUiModel.from(
            (repo.load() as BridgeMonitoringResult.Success).data
        )
        assertEquals(BridgeActivityStatus.NO_RECENT, model.bridges[0].activityStatus())
    }

    @Test
    fun `sem lastActivityAt vira nunca recebeu venda`() {
        val body = """{"totalBridges":1,
          "bridges":[{"bridgeName":"B","status":"ACTIVE","activityStatus":"NEVER"}]}"""
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val model = BridgeMonitoringUiModel.from(
            (repo.load() as BridgeMonitoringResult.Success).data
        )
        assertEquals(BridgeActivityStatus.NEVER, model.bridges[0].activityStatus())
    }

    @Test
    fun `maskTransactionCode mascara e esconde o valor completo`() {
        assertEquals("—", maskTransactionCode(null))
        assertEquals("—", maskTransactionCode("  "))
        assertEquals("•••• 8821", maskTransactionCode("SN-2026-00008821"))
        assertFalse(maskTransactionCode("SN-2026-00008821").contains("2026"))
    }

    @Test
    fun `formata timestamp iso`() {
        assertTrue(formatBridgeTimestamp("2026-08-26T10:30:00Z").contains("08/2026"))
        assertEquals("—", formatBridgeTimestamp(null))
        assertEquals("—", formatBridgeTimestamp("nao-e-um-timestamp"))
    }

    @Test
    fun `resposta sem secrets nunca os carrega`() {
        val body = "{\"totalBridges\":0,\"bridges\":[]}"
        val http = FakeHttpClient { _ -> HttpResponse.of(200, body) }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        val text = (repo.load() as BridgeMonitoringResult.Success).data.toString()
        assertFalse(text.contains("service_role"))
        assertFalse(text.contains("M2M"))
        assertFalse(text.contains("Bearer"))
    }

    // ------------------------------------------------------------ repository

    @Test
    fun `load envia JWT real na Edge Function bridge-monitoring-proxy`() {
        val http = FakeHttpClient { request ->
            assertTrue(request.url.endsWith("/functions/v1/bridge-monitoring-proxy"))
            assertEquals("Bearer tok-admin", request.headers["Authorization"])
            assertEquals("GET", request.method)
            HttpResponse.of(200, """{"totalBridges":0,"bridges":[]}""")
        }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        assertTrue(repo.load() is BridgeMonitoringResult.Success)
        assertEquals(1, http.requests.size)
    }

    @Test
    fun `erro 401 vira falha`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(401, """{"error":"UNAUTHENTICATED"}""") }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        assertTrue(repo.load() is BridgeMonitoringResult.Failed)
    }

    @Test
    fun `erro 403 vira falha`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(403, """{"error":"FORBIDDEN"}""") }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        assertTrue(repo.load() is BridgeMonitoringResult.Failed)
    }

    @Test
    fun `erro 5xx vira falha sem mascarar sucesso`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(503, """{"error":"UPSTREAM_ERROR"}""") }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        assertTrue(repo.load() is BridgeMonitoringResult.Failed)
    }

    @Test
    fun `JSON inválido vira falha`() {
        val http = FakeHttpClient { _ -> HttpResponse.of(200, "not json at all") }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        assertTrue(repo.load() is BridgeMonitoringResult.Failed)
    }

    @Test
    fun `falha de rede vira falha`() {
        val http = FakeHttpClient { _ -> throw java.io.IOException("boom") }
        val repo = BridgeMonitoringRepository(config, http) { "tok-admin" }

        assertTrue(repo.load() is BridgeMonitoringResult.Failed)
    }

    @Test
    fun `sem JWT vira falha sem chamar backend`() {
        val http = FakeHttpClient()
        val repo = BridgeMonitoringRepository(config, http) { null }

        assertTrue(repo.load() is BridgeMonitoringResult.Failed)
        assertEquals(0, http.requests.size)
    }

    @Test
    fun `backend não configurado vira falha`() {
        val http = FakeHttpClient()
        val repo = BridgeMonitoringRepository(
            SupabaseAuthConfig(projectUrl = "", anonKey = ""), http
        ) { "tok-admin" }

        assertTrue(repo.load() is BridgeMonitoringResult.Failed)
        assertEquals(0, http.requests.size)
    }
}
