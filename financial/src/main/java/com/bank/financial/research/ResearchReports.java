package com.bank.financial.research;

import com.bank.financial.kit.ModelConnection;
import com.bank.financial.research.data.DataIngestionService;
import com.bank.financial.research.data.FreshnessPolicy;
import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.http.HttpResearchDataSource;
import com.bank.financial.research.data.stub.StubResearchDataSource;
import com.bank.financial.research.engine.ResearchReportEngine;
import com.bank.financial.research.model.OpenJiuwenReportModel;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.model.ScriptedReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.obs.Slf4jMemoryObserver;
import java.time.Duration;

/**
 * Wiring factory for the research-report engine. Two presets:
 * <ul>
 *   <li>{@link #offline(long)} — stub data + scripted model: deterministic, no
 *       network, no API key. Used by tests and the {@code --mock} playground.</li>
 *   <li>{@link #fromEnv(long)} — production wiring driven by env vars: an HTTP
 *       data gateway ({@code RESEARCH_DATA_BASE_URL}) when set (else the stub),
 *       and a real LLM ({@code RESEARCH_REPORT_LIVE_MODEL=true}, via BANK_LLM_*)
 *       when enabled (else the scripted model).</li>
 * </ul>
 */
public final class ResearchReports {

    private ResearchReports() {
    }

    /** Fully offline, deterministic engine (stub data + scripted model). */
    public static ResearchReportEngine offline(long asOfEpochMs) {
        ResearchDataSource source = new StubResearchDataSource(asOfEpochMs);
        DataIngestionService ingestion = new DataIngestionService(source, FreshnessPolicy.days(90));
        return new ResearchReportEngine(
                ingestion, source.name(), new ScriptedReportModel(), null, MemoryObserver.NOOP, null);
    }

    /** Production wiring from environment variables (falls back to offline pieces). */
    public static ResearchReportEngine fromEnv(long asOfEpochMs) {
        ResearchDataSource source = dataSourceFromEnv(asOfEpochMs);
        DataIngestionService ingestion = new DataIngestionService(source, FreshnessPolicy.days(envInt("RESEARCH_FRESHNESS_DAYS", 90)));
        ReportModel model = liveModel()
                ? new OpenJiuwenReportModel(ModelConnection.forTier("smart"))
                : new ScriptedReportModel();
        return new ResearchReportEngine(
                ingestion, source.name(), model, null, new Slf4jMemoryObserver(false), null);
    }

    private static ResearchDataSource dataSourceFromEnv(long asOfEpochMs) {
        String base = System.getenv("RESEARCH_DATA_BASE_URL");
        if (base != null && !base.isBlank()) {
            return new HttpResearchDataSource(
                    base, Duration.ofSeconds(3), Duration.ofSeconds(envInt("RESEARCH_DATA_TIMEOUT_S", 8)),
                    System.getenv("RESEARCH_DATA_TOKEN"), asOfEpochMs);
        }
        return new StubResearchDataSource(asOfEpochMs);
    }

    static boolean liveModel() {
        return "true".equalsIgnoreCase(System.getenv("RESEARCH_REPORT_LIVE_MODEL"));
    }

    private static int envInt(String key, int def) {
        try {
            String v = System.getenv(key);
            return v == null || v.isBlank() ? def : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
