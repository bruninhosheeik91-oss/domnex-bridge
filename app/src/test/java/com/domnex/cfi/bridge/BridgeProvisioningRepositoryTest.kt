package com.domnex.cfi.bridge

import com.domnex.cfi.bridge.provisioning.BridgeProvisioningRepository
import com.domnex.cfi.bridge.provisioning.PrefsBridgeProvisioningRepository
import com.domnex.cfi.bridge.provisioning.ProvisioningState
import com.domnex.cfi.bridge.provisioning.TechnicalConfig
import com.domnex.cfi.bridge.provisioning.TechnicalConfigValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Testes da FASE 7: provisionamento seguro e separação de perfis.
 * O CLIENT nunca vê endpoint/token; apenas DOMNEX_ADMIN acessa a config técnica.
 */
class BridgeProvisioningRepositoryTest {

    private class FakeStorage(
        initial: Map<String, String> = emptyMap()
    ) : com.domnex.cfi.bridge.provisioning.ProvisioningStorage {
        val map = initial.toMutableMap()
        var failReads = false

        override fun getString(key: String): String? {
            if (failReads) throw IllegalStateException("storage indisponível")
            return map[key]
        }

        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    private fun repo(storage: FakeStorage): BridgeProvisioningRepository =
        PrefsBridgeProvisioningRepository(storage)

    // 1) Estado UNCONFIGURED correto quando não há configuração.
    @Test
    fun `estado UNCONFIGURED quando armazenamento vazio`() {
        val repository = repo(FakeStorage())
        assertEquals(ProvisioningState.UNCONFIGURED, repository.state())
    }

    // 2) Estado CONFIGURED correto com endpoint + token.
    @Test
    fun `estado CONFIGURED com endpoint e token preenchidos`() {
        val repository = repo(
            FakeStorage(
                mapOf(
                    PrefsBridgeProvisioningRepository.KEY_BASE_URL to "https://webhook.exemplo.com/vendas",
                    PrefsBridgeProvisioningRepository.KEY_BRIDGE_TOKEN to "tok-123456"
                )
            )
        )
        assertEquals(ProvisioningState.CONFIGURED, repository.state())
    }

    // 3) Valores atuais preservados (roundtrip nas MESMAS chaves históricas).
    @Test
    fun `save e load preservam valores exatos nas chaves obrigatorias`() {
        val storage = FakeStorage()
        val repository = repo(storage)
        repository.save(
            TechnicalConfig(
                baseUrl = "https://api.cliente.com/bridge",
                bridgeToken = "token-seguro-9876",
                targetSystemName = "CFI"
            )
        )
        assertEquals("https://api.cliente.com/bridge", storage.map["api_base_url"])
        assertEquals("token-seguro-9876", storage.map["bridge_token"])
        assertEquals("CFI", storage.map["target_system_name"])

        val loaded = repository.load()
        assertEquals("https://api.cliente.com/bridge", loaded.baseUrl)
        assertEquals("token-seguro-9876", loaded.bridgeToken)
        assertEquals("CFI", loaded.targetSystemName)
    }

    // 4) Configuração existente NUNCA é apagada por load/state (leitura não destrutiva).
    @Test
    fun `leituras sucessivas preservam configuracao existente`() {
        val storage = FakeStorage(
            mapOf(
                PrefsBridgeProvisioningRepository.KEY_BASE_URL to "https://existente.com/hook",
                PrefsBridgeProvisioningRepository.KEY_BRIDGE_TOKEN to "tok-existente"
            )
        )
        val repository = repo(storage)
        repeat(3) {
            assertEquals(ProvisioningState.CONFIGURED, repository.state())
            assertEquals("https://existente.com/hook", repository.load().baseUrl)
        }
        assertTrue(storage.map.containsKey(PrefsBridgeProvisioningRepository.KEY_BRIDGE_TOKEN))
    }

    // 5) Token é mascarado na exibição (nunca revelado sem ação explícita).
    @Test
    fun `maskToken esconde o token e revela no maximo os 4 ultimos caracteres`() {
        val masked = TechnicalConfigValidator.maskToken("token-super-secreto-4321")
        assertFalse(masked.contains("super"))
        assertTrue(masked.endsWith("4321"))
        assertTrue(masked.startsWith("•"))

        val shortMasked = TechnicalConfigValidator.maskToken("abc")
        assertEquals("•••", shortMasked)

        assertNotEquals("", TechnicalConfigValidator.maskToken(""))
    }

    // 6) Validação local rejeita endpoint inseguro e campos vazios — e as
    //    mensagens de erro NUNCA ecoam o token.
    @Test
    fun `validacao local rejeita http vazio e nao ecoa o token`() {
        val token = "token-confidencial-xyz"

        val httpReasons =
            TechnicalConfigValidator.validate("http://inseguro.com/hook", token)
        assertTrue(httpReasons.any { it.contains("HTTPS") })

        val emptyUrlReasons = TechnicalConfigValidator.validate("", token)
        assertTrue(emptyUrlReasons.isNotEmpty())

        val emptyTokenReasons =
            TechnicalConfigValidator.validate("https://ok.com/hook", "  ")
        assertTrue(emptyTokenReasons.any { it.contains("token") })

        val allReasons = httpReasons + emptyUrlReasons + emptyTokenReasons
        assertTrue(allReasons.none { it.contains(token) })

        assertTrue(TechnicalConfigValidator.validate("https://ok.com/hook", token).isEmpty())
    }

    // 7) Falha de leitura do armazenamento resulta em ERROR (não em UNCONFIGURED).
    @Test
    fun `falha de storage produz estado ERROR`() {
        val storage = FakeStorage().apply { failReads = true }
        assertEquals(ProvisioningState.ERROR, repo(storage).state())
    }

    // 8) Logout NÃO apaga configuração: sessão usa arquivos distintos do motor,
    //    e o repositório não possui operação de limpeza.
    @Test
    fun `arquivos de sessao sao isolados do arquivo de provisionamento`() {
        assertEquals("cfi_bridge_prefs", PrefsBridgeProvisioningRepository.PREFS_FILE)
        assertNotEquals(
            PrefsBridgeProvisioningRepository.SESSION_PREFS_SUPABASE,
            PrefsBridgeProvisioningRepository.PREFS_FILE
        )
        assertNotEquals(
            PrefsBridgeProvisioningRepository.SESSION_PREFS_LOCAL_UI,
            PrefsBridgeProvisioningRepository.PREFS_FILE
        )
        // Contrato: a interface não expõe clear()/reset() para logout apagar config.
        val methods = BridgeProvisioningRepository::class.java.declaredMethods.map { it.name }
        assertFalse(methods.any { it.startsWith("clear") || it.startsWith("wipe") || it.startsWith("reset") })
    }

    // 9) Pausar o Bridge NÃO apaga configuração: prefs do monitor são outro arquivo.
    @Test
    fun `prefs do monitor sao isoladas da configuracao tecnica`() {
        assertEquals("bridge_monitor_prefs", PrefsBridgeProvisioningRepository.MONITOR_PREFS_FILE)
        assertNotEquals(
            PrefsBridgeProvisioningRepository.MONITOR_PREFS_FILE,
            PrefsBridgeProvisioningRepository.PREFS_FILE
        )
    }

    // 10) Nome amigável: fallback neutro quando target_system_name está vazio.
    @Test
    fun `nome amigavel tem fallback neutro quando nao configurado`() {
        assertEquals(
            TechnicalConfig.DEFAULT_SYSTEM_NAME,
            TechnicalConfig(baseUrl = "https://x.com/h", bridgeToken = "t").displayNameOrDefault()
        )
        assertEquals(
            "ERP Cliente",
            TechnicalConfig(targetSystemName = "  ERP Cliente ").displayNameOrDefault()
        )
    }
}
