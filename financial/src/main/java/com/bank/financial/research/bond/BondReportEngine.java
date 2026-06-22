package com.bank.financial.research.bond;

import com.bank.financial.research.bond.agent.BondComplianceAgent;
import com.bank.financial.research.bond.agent.BondCriticAgent;
import com.bank.financial.research.bond.agent.BondDataAgent;
import com.bank.financial.research.bond.agent.BondPlannerAgent;
import com.bank.financial.research.bond.agent.BondRatingManagerAgent;
import com.bank.financial.research.bond.agent.BondWriterAgent;
import com.bank.financial.research.bond.agent.CreditAgent;
import com.bank.financial.research.bond.agent.RatesAgent;
import com.bank.financial.research.data.BondData;
import com.bank.financial.research.data.BondDataSource;
import com.bank.financial.research.engine.PipelineProgress;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.engine.ReportMetadata;
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
 * Orchestrates the bond / fixed-income research desk over the shared-memory
 * blackboard, reusing the engine discipline: computed metrics ({@code BondCalc})
 * are the spine, the model only narrates, every step is fault-isolated,
 * budget-bounded, instrumented, and distilled into cross-run experience.
 *
 * <pre>PLAN → INGEST → RATES → CREDIT → RATE → WRITE → CRITIQUE → COMPLY → ASSEMBLE</pre>
 */
public final class BondReportEngine {

    private static final Logger log = LoggerFactory.getLogger("research.engine");

    private final BondDataSource source;
    private final ReportModel model;
    private final ExperienceStore experienceStore;
    private final MemoryObserver observer;
    private final LongSupplier clock;

    public BondReportEngine(BondDataSource source, ReportModel model, ExperienceStore experienceStore,
            MemoryObserver observer, LongSupplier clock) {
        this.source = source;
        this.model = model;
        this.experienceStore = experienceStore == null ? new InMemoryExperienceStore() : experienceStore;
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public BondReport generate(ReportRequest request) {
        return generate(request, PipelineProgress.NOOP);
    }

    public BondReport generate(ReportRequest request, PipelineProgress progress) {
        if (progress == null) {
            progress = PipelineProgress.NOOP;
        }
        Timer.Sample sample = Timer.start(Metrics.globalRegistry);
        boolean ok = false;
        log.info("bond-report start run={} bond={} model={}",
                request.collaborationId(), request.ticker(), model.name());
        try {
            BondReport r = doGenerate(request, progress);
            ok = true;
            return r;
        } finally {
            sample.stop(Metrics.timer("research.report.latency", "type", "bond"));
            Metrics.counter("research.report.count", "type", "bond", "outcome", ok ? "ok" : "error").increment();
        }
    }

    private BondReport doGenerate(ReportRequest request, PipelineProgress progress) {
        BondData.Dataset dataset = source.load(request.ticker(), request.asOfEpochMs());
        SharedMemoryStore store = new InMemorySharedMemoryStore();
        BondContext ctx = new BondContext(request, dataset, model, store, observer, clock);

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

        safe(ctx, new BondPlannerAgent(), progress, "planner", 1);
        safe(ctx, new BondDataAgent(), progress, "data", 2);
        ctx.memory("data").recordHandover("rates", "债券要素已录入 → 利率分析");
        safe(ctx, new RatesAgent(), progress, "rates", 3);
        ctx.memory("rates").recordHandover("credit", "YTM/久期已算 → 信用分析");
        safe(ctx, new CreditAgent(), progress, "credit", 4);
        ctx.memory("credit").recordHandover("lead-manager", "利差/信用已评 → 定调");
        safe(ctx, new BondRatingManagerAgent(), progress, "lead-manager", 5);
        ctx.memory("lead-manager").get(BondBb.YTM); // READ edge: lead-manager → rates
        ctx.memory("lead-manager").recordHandover("writer", "配置评级已定 → 撰写");

        BondWriterAgent writer = new BondWriterAgent();
        BondCriticAgent critic = new BondCriticAgent();
        safe(ctx, writer, progress, "writer", 6);
        ctx.memory("writer").recordHandover("critic", "初稿完成 → 评审");
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

        BondComplianceAgent compliance = new BondComplianceAgent();
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

        BondReport report = assemble(ctx, rounds, findings, notes);
        ctx.memory("lead-manager").recordOutcome("bond report assembled: " + report.stance());
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

    private void safe(BondContext ctx, BondSubAgent agent, PipelineProgress progress, String role, int index) {
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
    private void emitDone(PipelineProgress progress, BondContext ctx, String role, List<String> before) {
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

    private void safeNoEmit(BondContext ctx, BondSubAgent agent) {
        try {
            agent.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
    }

    /** Expose the run's agent-interaction edges (handover/READ/outcome) to the UI. Fault-isolated. */
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

    private List<String> reviewSafe(BondContext ctx, BondCriticAgent critic) {
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
                Set.of("planning", "data-ingestion", "rates-analytics", "credit-analytics",
                        "house-view", "writing", "review", "compliance"),
                "research-report:BOND");
    }

    private BondReport assemble(BondContext ctx, int criticRounds, List<String> findings, List<String> notes) {
        BondData.Dataset ds = ctx.dataset();
        String stance = BondBb.stanceLabel(ctx.latest(BondBb.STANCE).orElse("NEUTRAL"));
        String thesis = ctx.latest(BondBb.THESIS).orElse("(观点未生成)");
        BondReport.Metrics metrics = new BondReport.Metrics(
                ctx.latestNum(BondBb.YTM).orElse(0), ctx.latestNum(BondBb.CURRENT_YIELD).orElse(0),
                ctx.latestNum(BondBb.MACAULAY).orElse(0), ctx.latestNum(BondBb.MODIFIED).orElse(0),
                ctx.latestNum(BondBb.CONVEXITY).orElse(0), ctx.latestNum(BondBb.CREDIT_SPREAD).orElse(0));

        List<ReportSection> sections = new ArrayList<>();
        String outline = ctx.latest(BondBb.OUTLINE).orElse(BondBb.OUTLINE_DEFAULT);
        int order = 0;
        for (String id : outline.split(",")) {
            id = id.trim();
            String body = ctx.latest(BondBb.SECTION_PREFIX + id).orElse("(本节未生成)");
            sections.add(new ReportSection(id, BondBb.titleOf(id), body, order++));
        }

        ReportMetadata metadata = new ReportMetadata(
                model.name(), source.name(), ctx.modelCalls(), criticRounds, "BOND-ANALYTICS",
                ds.freshnessWarnings(), notes, findings, ctx.degradations(), ctx.now());
        return new BondReport(ds.code(), ds.name(), ds.issuer(), ds.rating(), stance, thesis, metrics, sections, metadata);
    }
}
