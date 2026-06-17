package com.bank.financial.research.engine;

import com.bank.financial.research.agent.ComplianceAgent;
import com.bank.financial.research.agent.CriticAgent;
import com.bank.financial.research.agent.DataIngestionAgent;
import com.bank.financial.research.agent.LeadManagerAgent;
import com.bank.financial.research.agent.PlannerAgent;
import com.bank.financial.research.agent.QuantModelAgent;
import com.bank.financial.research.agent.SectorMacroAgent;
import com.bank.financial.research.agent.ValuationAgent;
import com.bank.financial.research.agent.WriterAgent;
import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.data.DataIngestionService;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceMemoryKit;
import com.huawei.ascend.a2a.memory.experience.ExperienceStore;
import com.huawei.ascend.a2a.memory.experience.InMemoryExperienceStore;
import com.huawei.ascend.a2a.memory.experience.Lesson;
import com.huawei.ascend.a2a.memory.hook.DefaultCollaborationMemoryHook;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryKit;
import com.huawei.ascend.a2a.memory.shared.SharedMemoryStore;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the research-desk pipeline. It sequences the specialised
 * sub-agents over a shared-memory blackboard (the canonical single source of
 * truth for the run) through the phases a real desk follows:
 *
 * <pre>
 *   PLAN → INGEST → ANALYZE (quant → valuation → sector) → CONVERGE (manager)
 *        → WRITE → CRITIQUE (writer↔critic, bounded) → COMPLY → ASSEMBLE
 * </pre>
 *
 * <p>Shared context = the blackboard; cross-run knowledge = the experience store
 * (lessons recalled at the start and distilled at the end). Every loop is
 * budget-bounded, and the report is assembled from the blackboard so its numbers
 * are exactly what the analysis agents computed.
 */
public final class ResearchReportEngine {

    private static final Logger log = LoggerFactory.getLogger("research.engine");

    private final DataIngestionService ingestion;
    private final String dataSourceName;
    private final ReportModel model;
    private final ExperienceStore experienceStore;
    private final MemoryObserver observer;
    private final LongSupplier clock;

    public ResearchReportEngine(DataIngestionService ingestion, String dataSourceName, ReportModel model,
            ExperienceStore experienceStore, MemoryObserver observer, LongSupplier clock) {
        this.ingestion = ingestion;
        this.dataSourceName = dataSourceName;
        this.model = model;
        this.experienceStore = experienceStore == null ? new InMemoryExperienceStore() : experienceStore;
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    /** Run the full pipeline and return the assembled report. */
    public ResearchReport generate(ReportRequest request) {
        return generate(request, PipelineProgress.NOOP);
    }

    /**
     * Run the full pipeline, emitting per-agent progress ("running"/"done") to {@code progress}.
     * Progress callbacks are fault-isolated: a callback failure never affects the run.
     */
    public ResearchReport generate(ReportRequest request, PipelineProgress progress) {
        if (progress == null) {
            progress = PipelineProgress.NOOP;
        }
        Timer.Sample sample = Timer.start(Metrics.globalRegistry);
        boolean ok = false;
        log.info("research-report start run={} ticker={} type={} model={}",
                request.collaborationId(), request.ticker(), request.reportType(), model.name());
        try {
            ResearchReport report = doGenerate(request, progress);
            ok = true;
            log.info("research-report done run={} rating={} target={} calls={} degraded={}",
                    request.collaborationId(), report.rating(), report.priceTarget(),
                    report.metadata().modelCalls(), report.metadata().degradations().size());
            return report;
        } finally {
            sample.stop(Metrics.timer("research.report.latency", "type", request.reportType()));
            Metrics.counter("research.report.count", "type", request.reportType(),
                    "outcome", ok ? "ok" : "error").increment();
        }
    }

    private ResearchReport doGenerate(ReportRequest request, PipelineProgress progress) {
        long now = request.asOfEpochMs();
        CompanyData.Dataset dataset = ingestion.assemble(request.ticker(), 5, now);

        SharedMemoryStore store = new InMemorySharedMemoryStore();
        ReportContext ctx = new ReportContext(request, dataset, model, store, observer, clock);

        CollaborationSignature signature = signature(request);
        ExperienceMemoryKit experience = ExperienceMemoryKit.forTenant(experienceStore, request.tenantId());

        // Cross-run knowledge: recall lessons from prior similar reports (fail-soft).
        try {
            List<Lesson> recalled = experience.recall(signature, 5);
            if (!recalled.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Lesson l : recalled) {
                    sb.append("- ").append(l.text()).append('\n');
                }
                ctx.put("planner", "experience.recalled", sb.toString());
            }
        } catch (RuntimeException e) {
            ctx.degraded("experience:recall", e.getMessage());
        }

        // ── PLAN → INGEST → ANALYZE ───────────────────────────────────────────
        safe(ctx, new PlannerAgent(), progress, "planner", 1);
        safe(ctx, new DataIngestionAgent(), progress, "data", 2);
        safe(ctx, new QuantModelAgent(), progress, "quant-model", 3);
        safe(ctx, new ValuationAgent(), progress, "valuation", 4);
        safe(ctx, new SectorMacroAgent(), progress, "sector-macro", 5);

        // ── CONVERGE: the manager forms the house view (sole decision-maker) ──
        safe(ctx, new LeadManagerAgent(), progress, "lead-manager", 6);
        ctx.memory("lead-manager").recordHandover("writer", "house view set (rating + target)");

        // ── WRITE → CRITIQUE (bounded writer↔critic revision loop) ────────────
        WriterAgent writer = new WriterAgent();
        CriticAgent critic = new CriticAgent();
        // The revision loop re-runs writer/critic; progress is emitted once for the
        // first pass (index 7/8) and not re-incremented across rounds.
        safe(ctx, writer, progress, "writer", 7);
        emit(progress, "critic", "running", 8);
        List<String> criticBefore = new ArrayList<>(ctx.blackboardKeys());
        List<String> findings = reviewSafe(ctx, critic);
        emit(progress, "critic", "done", 8);
        emitDone(progress, ctx, "critic", criticBefore);
        int rounds = 0;
        while (!findings.isEmpty() && rounds < request.budget().maxCriticRounds() && ctx.withinTime()) {
            rounds++;
            log.debug("research-report revise run={} round={} findings={}",
                    request.collaborationId(), rounds, findings.size());
            safe(ctx, writer); // appends revised section versions (audit-logged)
            findings = reviewSafe(ctx, critic);
        }

        // ── COMPLY ────────────────────────────────────────────────────────────
        ComplianceAgent compliance = new ComplianceAgent();
        List<String> complianceNotes;
        List<String> dataGaps;
        emit(progress, "compliance", "running", 9);
        List<String> complianceBefore = new ArrayList<>(ctx.blackboardKeys());
        try {
            complianceNotes = compliance.notes(ctx);
            dataGaps = compliance.dataGaps(ctx);
            compliance.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded("compliance", e.getMessage());
            complianceNotes = List.of("披露生成失败,需人工补充。");
            dataGaps = List.of();
        }
        emit(progress, "compliance", "done", 9);
        emitDone(progress, ctx, "compliance", complianceBefore);

        // ── ASSEMBLE from the blackboard ──────────────────────────────────────
        ResearchReport report = assemble(ctx, rounds, findings, complianceNotes, dataGaps);
        ctx.memory("lead-manager").recordOutcome("report assembled: " + report.rating());

        // Distill this run's blackboard into cross-run experience (PII-stripped, fail-soft).
        try {
            SharedMemoryKit blackboard = SharedMemoryKit.forCollaboration(
                    store, request.tenantId(), request.collaborationId());
            new DefaultCollaborationMemoryHook(experience, false).onCollaborationEnd(signature, blackboard);
        } catch (RuntimeException e) {
            ctx.degraded("experience:distill", e.getMessage());
        }

        return report;
    }

    /** As {@link #safe(ReportContext, com.bank.financial.research.agent.ReportSubAgent)}, plus
     *  fault-isolated running/done progress around the agent. */
    private void safe(ReportContext ctx, com.bank.financial.research.agent.ReportSubAgent agent,
            PipelineProgress progress, String role, int index) {
        emit(progress, role, "running", index);
        List<String> before = new ArrayList<>(ctx.blackboardKeys());
        safe(ctx, agent);
        emit(progress, role, "done", index);
        emitDone(progress, ctx, role, before);
    }

    /** Diff the blackboard against {@code before} and report newly-written key→value pairs. Fault-isolated. */
    private void emitDone(PipelineProgress progress, ReportContext ctx, String role, List<String> before) {
        try {
            java.util.Map<String, String> wrote = new java.util.LinkedHashMap<>();
            Set<String> seen = new java.util.HashSet<>(before);
            for (String key : ctx.blackboardKeys()) {
                if (!seen.contains(key)) {
                    wrote.put(key, ctx.latest(key).orElse(""));
                }
            }
            progress.onAgentDone(role, wrote);
        } catch (RuntimeException ignored) {
            // progress reporting must never affect the run
        }
    }

    /** Emit one progress event. Fault-isolated: a callback failure never affects the run. */
    private void emit(PipelineProgress progress, String role, String state, int index) {
        try {
            progress.onAgent(role, state, index, 9);
        } catch (RuntimeException e) {
            log.debug("research-report progress callback failed role={} state={} err={}",
                    role, state, e.getMessage());
        }
    }

    /** Run one sub-agent with timing + fault isolation: a failure degrades, never aborts. */
    private void safe(ReportContext ctx, com.bank.financial.research.agent.ReportSubAgent agent) {
        long t0 = ctx.now();
        try {
            agent.contribute(ctx);
            log.debug("research-report phase ok run={} role={} ms={}",
                    ctx.request().collaborationId(), agent.role(), ctx.now() - t0);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
    }

    private List<String> reviewSafe(ReportContext ctx, CriticAgent critic) {
        try {
            return critic.review(ctx);
        } catch (RuntimeException e) {
            ctx.degraded("critic", e.getMessage());
            return List.of();
        }
    }

    private CollaborationSignature signature(ReportRequest request) {
        return new CollaborationSignature(
                Set.of("planning", "data-ingestion", "financial-modeling", "valuation",
                        "sector-macro", "house-view", "writing", "review", "compliance"),
                "research-report:" + request.reportType());
    }

    private ResearchReport assemble(ReportContext ctx, int criticRounds, List<String> findings,
            List<String> complianceNotes, List<String> dataGaps) {
        ReportRequest request = ctx.request();
        String company = ctx.latest(Bb.COMPANY).orElse(request.ticker());
        String currency = ctx.latest(Bb.CURRENCY).orElse("CNY");
        String rating = ctx.latest(Bb.RATING).orElse("中性 (Neutral)");
        double priceTarget = ctx.latestNum(Bb.PRICE_TARGET).orElse(0.0);
        double currentPrice = ctx.latestNum(Bb.CURRENT_PRICE).orElse(0.0);
        double upside = ctx.latestNum(Bb.UPSIDE_PCT).orElse(0.0);
        String thesis = ctx.latest(Bb.THESIS).orElse("(论点未生成)");
        String verdict = ctx.latest(Bb.CONVERGENCE_VERDICT).orElse("n/a");

        List<ReportSection> sections = new ArrayList<>();
        String outline = ctx.latest(Bb.OUTLINE).orElse(PlannerAgent.OUTLINE);
        int order = 0;
        for (String id : outline.split(",")) {
            id = id.trim();
            String body = ctx.latest(Bb.SECTION_PREFIX + id).orElse("(本节未生成)");
            sections.add(new ReportSection(id, WriterAgent.titleOf(id), body, order++));
        }

        ResearchReport.Metadata metadata = new ResearchReport.Metadata(
                model.name(), dataSourceName, ctx.modelCalls(), criticRounds, verdict,
                dataGaps, complianceNotes, findings, ctx.degradations(), ctx.now());

        return new ResearchReport(
                request.ticker(), company, currency, rating, priceTarget, currentPrice, upside,
                thesis, sections, metadata);
    }
}
