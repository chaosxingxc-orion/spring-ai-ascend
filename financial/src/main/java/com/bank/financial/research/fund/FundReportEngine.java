package com.bank.financial.research.fund;

import com.bank.financial.research.data.FundData;
import com.bank.financial.research.data.FundDataSource;
import com.bank.financial.research.engine.PipelineProgress;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.engine.ReportMetadata;
import com.bank.financial.research.fund.agent.FundComplianceAgent;
import com.bank.financial.research.fund.agent.FundCriticAgent;
import com.bank.financial.research.fund.agent.FundPlannerAgent;
import com.bank.financial.research.fund.agent.FundRatingManagerAgent;
import com.bank.financial.research.fund.agent.FundRiskAgent;
import com.bank.financial.research.fund.agent.FundWriterAgent;
import com.bank.financial.research.fund.agent.NavIngestionAgent;
import com.bank.financial.research.fund.agent.PerformanceAgent;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceMemoryKit;
import com.huawei.ascend.a2a.memory.experience.ExperienceStore;
import com.huawei.ascend.a2a.memory.experience.InMemoryExperienceStore;
import com.huawei.ascend.a2a.memory.hook.DefaultCollaborationMemoryHook;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import com.huawei.ascend.a2a.memory.shared.InMemorySharedMemoryStore;
import com.huawei.ascend.a2a.memory.shared.InteractionEntry;
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
 * Orchestrates the fund / FOF research desk over the shared-memory blackboard,
 * reusing the engine discipline: computed metrics ({@code FundCalc}) are the
 * spine, the model only narrates, every step is fault-isolated, budget-bounded,
 * instrumented, and distilled into cross-run experience.
 *
 * <pre>PLAN → INGEST → PERFORMANCE → RISK → RATE → WRITE → CRITIQUE → COMPLY → ASSEMBLE</pre>
 */
public final class FundReportEngine {

    private static final Logger log = LoggerFactory.getLogger("research.engine");

    private final FundDataSource source;
    private final ReportModel model;
    private final ExperienceStore experienceStore;
    private final MemoryObserver observer;
    private final LongSupplier clock;

    public FundReportEngine(FundDataSource source, ReportModel model, ExperienceStore experienceStore,
            MemoryObserver observer, LongSupplier clock) {
        this.source = source;
        this.model = model;
        this.experienceStore = experienceStore == null ? new InMemoryExperienceStore() : experienceStore;
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public FundReport generate(ReportRequest request) {
        return generate(request, PipelineProgress.NOOP);
    }

    public FundReport generate(ReportRequest request, PipelineProgress progress) {
        if (progress == null) {
            progress = PipelineProgress.NOOP;
        }
        Timer.Sample sample = Timer.start(Metrics.globalRegistry);
        boolean ok = false;
        log.info("fund-report start run={} fund={} model={}",
                request.collaborationId(), request.ticker(), model.name());
        try {
            FundReport r = doGenerate(request, progress);
            ok = true;
            return r;
        } finally {
            sample.stop(Metrics.timer("research.report.latency", "type", "fund"));
            Metrics.counter("research.report.count", "type", "fund", "outcome", ok ? "ok" : "error").increment();
        }
    }

    private FundReport doGenerate(ReportRequest request, PipelineProgress progress) {
        FundData.Dataset dataset = source.load(request.ticker(), request.asOfEpochMs());
        SharedMemoryStore store = new InMemorySharedMemoryStore();
        FundContext ctx = new FundContext(request, dataset, model, store, observer, clock);

        CollaborationSignature signature = signature(request);
        ExperienceMemoryKit experience = ExperienceMemoryKit.forTenant(experienceStore, request.tenantId());
        try {
            var recalled = experience.recall(signature, 5);
            if (!recalled.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                recalled.forEach(l -> sb.append("- ").append(l.text()).append('\n'));
                ctx.put("planner", "experience.recalled", sb.toString());
            }
        } catch (RuntimeException e) {
            ctx.degraded("experience:recall", e.getMessage());
        }

        safe(ctx, new FundPlannerAgent(), progress, "planner", 1);
        safe(ctx, new NavIngestionAgent(), progress, "data", 2);
        ctx.memory("data").recordHandover("performance", "NAV series ingested → performance");
        safe(ctx, new PerformanceAgent(), progress, "performance", 3);
        ctx.memory("performance").recordHandover("risk", "performance metrics set → risk");
        // Dependency edge: the risk agent reads the volatility performance computed (records READ risk→performance).
        ctx.memory("risk").get(FundBb.ANN_VOL);
        safe(ctx, new FundRiskAgent(), progress, "risk", 4);
        ctx.memory("risk").recordHandover("lead-manager", "risk level set → rating");
        // Dependency edge: the manager reads the Sharpe before rating (records READ lead-manager→performance).
        ctx.memory("lead-manager").get(FundBb.SHARPE);
        safe(ctx, new FundRatingManagerAgent(), progress, "lead-manager", 5);
        ctx.memory("lead-manager").recordHandover("writer", "rating set → drafting");

        FundWriterAgent writer = new FundWriterAgent();
        FundCriticAgent critic = new FundCriticAgent();
        ctx.memory("writer").recordHandover("critic", "draft written → review");
        safe(ctx, writer, progress, "writer", 6);
        emit(progress, "critic", "running", 7);
        List<String> criticBefore = new ArrayList<>(ctx.blackboardKeys());
        List<String> findings = reviewSafe(ctx, critic);
        emit(progress, "critic", "done", 7);
        emitDone(progress, ctx, "critic", criticBefore);
        int rounds = 0;
        while (!findings.isEmpty() && rounds < request.budget().maxCriticRounds() && ctx.withinTime()) {
            rounds++;
            safeNoEmit(ctx, writer);
            findings = reviewSafe(ctx, critic);
        }

        FundComplianceAgent compliance = new FundComplianceAgent();
        emit(progress, "compliance", "running", 8);
        List<String> complianceBefore = new ArrayList<>(ctx.blackboardKeys());
        List<String> notes;
        try {
            notes = compliance.notes(ctx);
            compliance.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded("compliance", e.getMessage());
            notes = List.of("披露生成失败,需人工补充。");
        }
        emit(progress, "compliance", "done", 8);
        emitDone(progress, ctx, "compliance", complianceBefore);

        FundReport report = assemble(ctx, rounds, findings, notes);
        ctx.memory("lead-manager").recordOutcome("fund report assembled: " + report.overallRating());

        // Expose the collaboration interaction graph (handover/read/outcome) — fault-isolated.
        emitInteractions(progress, store, request);

        try {
            SharedMemoryKit blackboard = SharedMemoryKit.forCollaboration(
                    store, request.tenantId(), request.collaborationId());
            new DefaultCollaborationMemoryHook(experience, false).onCollaborationEnd(signature, blackboard);
        } catch (RuntimeException e) {
            ctx.degraded("experience:distill", e.getMessage());
        }
        return report;
    }

    private void safe(FundContext ctx, FundSubAgent agent, PipelineProgress progress, String role, int index) {
        emit(progress, role, "running", index);
        List<String> before = new ArrayList<>(ctx.blackboardKeys());
        try {
            agent.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
        emit(progress, role, "done", index);
        emitDone(progress, ctx, role, before);
    }

    /** Diff the blackboard against {@code before} and report newly-written key→value pairs. Fault-isolated. */
    private void emitDone(PipelineProgress progress, FundContext ctx, String role, List<String> before) {
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

    /** Map the collaboration's interaction record into the progress edge view. Fault-isolated. */
    private void emitInteractions(PipelineProgress progress, SharedMemoryStore store, ReportRequest request) {
        try {
            List<InteractionEntry> entries = store.interactions(request.tenantId(), request.collaborationId());
            List<java.util.Map<String, String>> edges = new ArrayList<>(entries.size());
            for (InteractionEntry e : entries) {
                java.util.Map<String, String> edge = new java.util.LinkedHashMap<>();
                edge.put("type", e.type().name());
                edge.put("actor", e.actorAgentId());
                edge.put("target", e.targetAgentId());
                edge.put("detail", e.detail());
                edges.add(edge);
            }
            progress.onInteractions(edges);
        } catch (RuntimeException ignored) {
            // interaction exposure must never affect the run
        }
    }

    private void safeNoEmit(FundContext ctx, FundSubAgent agent) {
        try {
            agent.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
    }

    private List<String> reviewSafe(FundContext ctx, FundCriticAgent critic) {
        try {
            return critic.review(ctx);
        } catch (RuntimeException e) {
            ctx.degraded("critic", e.getMessage());
            return List.of();
        }
    }

    private void emit(PipelineProgress progress, String role, String state, int index) {
        try {
            progress.onAgent(role, state, index, 8);
        } catch (RuntimeException ignored) {
            // progress reporting must never affect the run
        }
    }

    private CollaborationSignature signature(ReportRequest request) {
        return new CollaborationSignature(
                Set.of("planning", "data-ingestion", "performance-analytics", "risk-analytics",
                        "house-view", "writing", "review", "compliance"),
                "research-report:FUND");
    }

    private FundReport assemble(FundContext ctx, int criticRounds, List<String> findings, List<String> notes) {
        FundData.Dataset ds = ctx.dataset();
        String rating = FundBb.ratingLabel(ctx.latest(FundBb.OVERALL_RATING).orElse("NEUTRAL"));
        String thesis = ctx.latest(FundBb.THESIS).orElse("(观点未生成)");
        FundReport.Metrics metrics = new FundReport.Metrics(
                ctx.latestNum(FundBb.CUM_RETURN).orElse(0), ctx.latestNum(FundBb.ANN_RETURN).orElse(0),
                ctx.latestNum(FundBb.ANN_VOL).orElse(0), ctx.latestNum(FundBb.SHARPE).orElse(0),
                ctx.latestNum(FundBb.MAX_DD).orElse(0), ctx.latestNum(FundBb.CALMAR).orElse(0),
                ctx.latestNum(FundBb.BETA).orElse(0), ctx.latestNum(FundBb.ALPHA).orElse(0));

        List<ReportSection> sections = new ArrayList<>();
        String outline = ctx.latest(FundBb.OUTLINE).orElse(FundBb.OUTLINE_DEFAULT);
        int order = 0;
        for (String id : outline.split(",")) {
            id = id.trim();
            String body = ctx.latest(FundBb.SECTION_PREFIX + id).orElse("(本节未生成)");
            sections.add(new ReportSection(id, FundBb.titleOf(id), body, order++));
        }

        ReportMetadata metadata = new ReportMetadata(
                model.name(), source.name(), ctx.modelCalls(), criticRounds, "FUND-ANALYTICS",
                ds.freshnessWarnings(), notes, findings, ctx.degradations(), ctx.now());
        return new FundReport(ds.code(), ds.name(), ds.type(), rating, thesis, metrics, sections, metadata);
    }
}
