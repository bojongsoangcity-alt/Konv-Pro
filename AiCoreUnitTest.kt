package com.dooyappwork.konveksipro.ai

import com.dooyappwork.konveksipro.ai.config.AILimitsConfig
import com.dooyappwork.konveksipro.ai.config.DeveloperAIConfig
import com.dooyappwork.konveksipro.ai.data.AIQuotaManager
import com.dooyappwork.konveksipro.ai.data.AIUsageTracker
import com.dooyappwork.konveksipro.ai.data.InMemoryKeyValueStore
import com.dooyappwork.konveksipro.ai.local.BusinessSnapshot
import com.dooyappwork.konveksipro.ai.local.InsightPromptBuilder
import com.dooyappwork.konveksipro.ai.local.LocalInsightEngine
import com.dooyappwork.konveksipro.ai.model.AIRequest
import com.dooyappwork.konveksipro.ai.model.FailureKind
import com.dooyappwork.konveksipro.ai.model.InsightKind
import com.dooyappwork.konveksipro.ai.model.ProviderResult
import com.dooyappwork.konveksipro.ai.provider.GeminiAdapter
import com.dooyappwork.konveksipro.ai.provider.OpenAICompatibleAdapter
import com.dooyappwork.konveksipro.ai.provider.ProviderHealthChecker
import com.dooyappwork.konveksipro.ai.provider.ProviderRegistry
import com.dooyappwork.konveksipro.ai.provider.ProviderSelector
import com.dooyappwork.konveksipro.ai.security.SecretRedactor
import com.dooyappwork.konveksipro.ai.security.SensitiveDataFilter
import com.dooyappwork.konveksipro.ai.transport.HttpJsonTransport
import com.dooyappwork.konveksipro.ai.util.JsonParser
import com.dooyappwork.konveksipro.ai.util.asIntOrNull
import com.dooyappwork.konveksipro.ai.util.asStringOrNull
import com.dooyappwork.konveksipro.ai.util.path
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val SECRET = "AIzaTESTKEY_0123456789abcdefghijklmnop"

class AiCoreUnitTest {

    private fun config(): DeveloperAIConfig = DeveloperAIConfig.fromMap(
        mapOf(
            "ai.externalEnabled" to "true",
            "ai.primary" to "gemini_main",
            "ai.fallback" to "backup",
            "ai.limits.maxRequestsPerDay" to "5",
            "ai.limits.maxTokensPerDay" to "1000",
            "ai.limits.maxCostUsdPerDay" to "0",
            "ai.limits.maxFallbackAttempts" to "2",
            "ai.privacy.requireConsent" to "true",
            "ai.provider.gemini_main.type" to "GEMINI",
            "ai.provider.gemini_main.baseUrl" to "https://generativelanguage.googleapis.com/v1beta",
            "ai.provider.gemini_main.model" to "gemini-2.0-flash",
            "ai.provider.gemini_main.key" to SECRET,
            "ai.provider.backup.type" to "OPENAI_COMPATIBLE",
            "ai.provider.backup.baseUrl" to "https://openrouter.ai/api/v1",
            "ai.provider.backup.model" to "llama-free",
            "ai.provider.backup.key" to "sk-test-backup-0123456789abcdef"
        )
    )

    @Test
    fun jsonRoundTrip() {
        val node = JsonParser.parse("""{"a":{"b":[1,{"c":"hi"}]}}""")
        assertEquals("hi", node.path("a", "b", 1, "c").asStringOrNull())
        assertEquals(1, node.path("a", "b", 0).asIntOrNull())
    }

    @Test
    fun secretsNeverLeak() {
        val text = "key=$SECRET header=Bearer abcdefghijklmnopqrst"
        val redacted = SecretRedactor.redact(text)
        assertFalse(redacted.contains("TESTKEY"))
        assertFalse(redacted.contains("abcdefghijklmnop"))
        assertFalse(config().find("gemini_main")!!.describe().contains("TESTKEY"))
    }

    @Test
    fun sensitiveFilterRemovesPii() {
        val out = SensitiveDataFilter().filter("budi@mail.com 081234567890 NIK 3273012345678901")
        assertTrue(out.text.contains("[EMAIL]"))
        assertTrue(out.text.contains("[TELEPON]"))
        assertTrue(out.text.contains("[NIK]"))
    }

    @Test
    fun localEngineWorksOffline() {
        val report = LocalInsightEngine().analyze(InsightKind.BUSINESS, BusinessSnapshot.EMPTY) { "Rp$it" }
        assertTrue(report.dataReady)
        assertNotNull(report.score)
        val order = LocalInsightEngine().analyze(InsightKind.ORDER, BusinessSnapshot.EMPTY) { "Rp$it" }
        // Tahap 3-4: seluruh modul kini punya tabelnya. Data kosong = angka 0 sungguhan
        // hasil agregat database, bukan hasil yang difabrikasi.
        InsightKind.entries.forEach { kind ->
            val report = LocalInsightEngine().analyze(kind, BusinessSnapshot.EMPTY) { "Rp$it" }
            assertTrue("dataReady untuk ${kind.name}", report.dataReady)
            assertTrue("metrik untuk ${kind.name}", report.metrics.isNotEmpty())
        }
    }

    @Test
    fun quotaStopsRequests() {
        val usage = AIUsageTracker(InMemoryKeyValueStore()) { 1_700_000_000_000L }
        val quota = AIQuotaManager(usage, AILimitsConfig(maxRequestsPerDay = 1))
        assertTrue(quota.evaluate().allowed)
        usage.recordSuccess("gemini_main", 10, 10, 0.0)
        assertFalse(quota.evaluate().allowed)
    }

    @Test
    fun selectorHonoursDeveloperOrder() {
        val registry = ProviderRegistry.default(HttpJsonTransport())
        val chain = ProviderSelector(registry).chain(config())
        assertEquals(listOf("gemini_main", "backup"), chain.map { it.id })
        assertEquals(emptyList<String>(), ProviderSelector(registry).chain(DeveloperAIConfig.EMPTY).map { it.id })
    }

    @Test
    fun geminiAdapterMatchesOfficialContract() {
        val adapter = GeminiAdapter(HttpJsonTransport())
        val cfg = config().find("gemini_main")!!
        val prepared = adapter.buildRequest(cfg, AIRequest(InsightKind.BUSINESS, "halo"))
        assertTrue(prepared.url.endsWith("/models/gemini-2.0-flash:generateContent"))
        assertEquals(SECRET, prepared.headers["x-goog-api-key"])
        assertFalse("kunci tidak boleh di URL", prepared.url.contains(SECRET))
        val parsed = adapter.parseResponse(
            cfg,
            """{"candidates":[{"content":{"parts":[{"text":"ok"}]}}],"usageMetadata":{"promptTokenCount":3,"candidatesTokenCount":4}}"""
        )
        assertEquals("ok", parsed?.text)
    }

    @Test
    fun openAiAdapterMatchesOfficialContract() {
        val adapter = OpenAICompatibleAdapter(HttpJsonTransport())
        val cfg = config().find("backup")!!
        val prepared = adapter.buildRequest(cfg, AIRequest(InsightKind.BUSINESS, "halo"))
        assertEquals("https://openrouter.ai/api/v1/chat/completions", prepared.url)
        assertTrue(prepared.headers["Authorization"]!!.startsWith("Bearer "))
        val parsed = adapter.parseResponse(cfg, """{"choices":[{"message":{"content":"narasi"}}]}""")
        assertEquals("narasi", parsed?.text)
    }

    @Test
    fun promptCarriesAggregatesOnly() {
        val report = LocalInsightEngine().analyze(InsightKind.BUSINESS, BusinessSnapshot.EMPTY) { "Rp$it" }
        val prompt = InsightPromptBuilder().build(report)
        assertTrue(prompt.contains("Pelanggan aktif"))
        assertFalse("prompt tidak boleh memuat email pelanggan", prompt.contains("@mail"))
    }

    @Test
    fun requestManagerFallsBackLocally() = runBlocking {
        val registry = ProviderRegistry.default(HttpJsonTransport())
        val usage = AIUsageTracker(InMemoryKeyValueStore()) { 1_700_000_000_000L }
        val cfg = config()
        val manager = AIRequestManager(
            configProvider = { cfg },
            registry = registry,
            selector = ProviderSelector(registry),
            health = ProviderHealthChecker(),
            filter = SensitiveDataFilter(),
            quota = AIQuotaManager(usage, cfg.limits),
            usage = usage,
            invoker = { _, _ -> ProviderResult.Failure(FailureKind.SERVER_ERROR, "HTTP 503") }
        )
        val report = LocalInsightEngine().analyze(InsightKind.BUSINESS, BusinessSnapshot.EMPTY) { "Rp$it" }
        val outcome = manager.analyze(report, consentGiven = true)
        assertTrue(outcome is AIOutcome.LocalOnly)
        assertEquals(FailureKind.SERVER_ERROR, (outcome as AIOutcome.LocalOnly).reason)
    }

    @Test
    fun requestManagerUsesFallbackProvider() = runBlocking {
        val registry = ProviderRegistry.default(HttpJsonTransport())
        val usage = AIUsageTracker(InMemoryKeyValueStore()) { 1_700_000_000_000L }
        val cfg = config()
        val manager = AIRequestManager(
            configProvider = { cfg },
            registry = registry,
            selector = ProviderSelector(registry),
            health = ProviderHealthChecker(),
            filter = SensitiveDataFilter(),
            quota = AIQuotaManager(usage, cfg.limits),
            usage = usage,
            invoker = { provider, _ ->
                if (provider.id == "gemini_main") ProviderResult.Failure(FailureKind.SERVER_ERROR, "503")
                else ProviderResult.Success("cadangan", 3, 3, 1)
            }
        )
        val report = LocalInsightEngine().analyze(InsightKind.BUSINESS, BusinessSnapshot.EMPTY) { "Rp$it" }
        val outcome = manager.analyze(report, consentGiven = true)
        assertTrue(outcome is AIOutcome.External)
        assertEquals("backup", (outcome as AIOutcome.External).providerId)
    }

    @Test
    fun consentBlocksExternalCall() = runBlocking {
        val registry = ProviderRegistry.default(HttpJsonTransport())
        val usage = AIUsageTracker(InMemoryKeyValueStore()) { 1_700_000_000_000L }
        val cfg = config()
        val manager = AIRequestManager(
            configProvider = { cfg }, registry = registry, selector = ProviderSelector(registry),
            health = ProviderHealthChecker(), filter = SensitiveDataFilter(),
            quota = AIQuotaManager(usage, cfg.limits), usage = usage,
            invoker = { _, _ -> ProviderResult.Success("tidak boleh", 1, 1, 0) }
        )
        val report = LocalInsightEngine().analyze(InsightKind.BUSINESS, BusinessSnapshot.EMPTY) { "Rp$it" }
        assertTrue(manager.analyze(report, consentGiven = false) is AIOutcome.LocalOnly)
    }
}
