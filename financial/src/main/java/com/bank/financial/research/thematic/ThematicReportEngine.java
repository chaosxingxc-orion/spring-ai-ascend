package com.bank.financial.research.thematic;

import com.bank.financial.research.data.ThematicData;
import com.bank.financial.research.data.ThematicDataSource;
import com.bank.financial.research.engine.PipelineProgress;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.engine.ReportMetadata;
import com.bank.financial.research.model.ReportModel;
import com.bank.financial.research.thematic.agent.MacroIngestionAgent;
import com.bank.financial.research.thematic.agent.SectorImpactAgent;
import com.bank.financial.research.thematic.agent.StrategyManagerAgent;
import com.bank.financial.research.thematic.agent.ThematicComplianceAgent;
import com.bank.financial.research.thematic.agent.ThematicCriticAgent;
import com.bank.financial.research.thematic.agent.ThematicPlannerAgent;
import com.bank.financial.research.thematic.agent.ThematicWriterAgent;
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
 * Orchestrates the thematic / sector-strategy desk over the shared-memory
 * blackboard, reusing the equity engine's discipline: computed numbers (the
 * sector-impact scores) are the spine, the model only narrates, every step is
 * fault-isolated (degrade, never abort), the run is budget-bounded and
 * instrumented, and the blackboard is distilled into cross-run experience.
 *
 * <pre>
 *   PLAN → INGEST → IMPACT (compute scores) → CONVERGE (strategy) → WRITE
 *        → CRITIQUE (writer↔critic, bounded) → COMPLY → ASSEMBLE
 * </pre>
 */
public final class ThematicReportEngine {

    private static final Logger log = LoggerFactory.getLogger("research.engine");

    private final ThematicDataSource source;
    private final String dataSourceName;
    private final ReportModel model;
    private final ExperienceStore experienceStore;
    private final MemoryObserver observer;
    private final LongSupplier clock;

    public ThematicReportEngine(ThematicDataSource source, ReportModel model,
            ExperienceStore experienceStore, MemoryObserver observer, LongSupplier clock) {
        this.source = source;
        this.dataSourceName = source.name();
        this.model = model;
        this.experienceStore = experienceStore == null ? new InMemoryExperienceStore() : experienceStore;
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
        this.clock = clock == null ? System::currentTimeMillis : clock;
    }

    public ThematicReport generate(ReportRequest request) {
        return generate(request, PipelineProgress.NOOP);
    }

    public ThematicReport generate(ReportRequest request, PipelineProgress progress) {
        if (progress == null) {
            progress = PipelineProgress.NOOP;
        }
        Timer.Sample sample = Timer.start(Metrics.globalRegistry);
        boolean ok = false;
        log.info("thematic-report start run={} theme={} model={}",
                request.collaborationId(), request.ticker(), model.name());
        try {
            ThematicReport r = doGenerate(request, progress);
            ok = true;
            log.info("thematic-report done run={} rating={} score={} degraded={}",
                    request.collaborationId(), r.overallRating(), r.overallScore(), r.metadata().degradations().size());
            return r;
        } finally {
            sample.stop(Metrics.timer("research.report.latency", "type", "thematic"));
            Metrics.counter("research.report.count", "type", "thematic", "outcome", ok ? "ok" : "error").increment();
        }
    }

    private ThematicReport doGenerate(ReportRequest request, PipelineProgress progress) {
        ThematicData.Dataset dataset = source.load(request.ticker(), request.asOfEpochMs());
        SharedMemoryStore store = new InMemorySharedMemoryStore();
        ThematicContext ctx = new ThematicContext(request, dataset, model, store, observer, clock);

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

        safe(ctx, new ThematicPlannerAgent(), progress, 1);
        safe(ctx, new MacroIngestionAgent(), progress, 2);
        ctx.memory("data").recordHandover("sector-impact", "macro factors ingested → scoring");
        // Dependency edge: the scorer reads the macro factors it scores against (records READ sector-impact→data).
        ctx.memory("sector-impact").get(ThematicBb.FACTORS_SUMMARY);
        safe(ctx, new SectorImpactAgent(), progress, 3);
        ctx.memory("sector-impact").recordHandover("lead-manager", "sub-sector scores set → strategy");
        // Dependency edge: the strategist reads the overall score before forming the view (records READ lead-manager→sector-impact).
        ctx.memory("lead-manager").get(ThematicBb.OVERALL_SCORE);
        safe(ctx, new StrategyManagerAgent(), progress, 4);
        ctx.memory("lead-manager").recordHandover("writer", "sector view set");

        ThematicWriterAgent writer = new ThematicWriterAgent();
        ThematicCriticAgent critic = new ThematicCriticAgent();
        ctx.memory("writer").recordHandover("critic", "draft written → review");
        safe(ctx, writer, progress, 5);
        emit(progress, "critic", "running", 6);
        List<String> criticBefore = new ArrayList<>(ctx.blackboardKeys());
        List<String> findings = reviewSafe(ctx, critic);
        emit(progress, "critic", "done", 6);
        emitDone(progress, ctx, "critic", criticBefore);
        int rounds = 0;
        while (!findings.isEmpty() && rounds < request.budget().maxCriticRounds() && ctx.withinTime()) {
            rounds++;
            safeNoEmit(ctx, writer);
            findings = reviewSafe(ctx, critic);
        }

        ThematicComplianceAgent compliance = new ThematicComplianceAgent();
        List<String> notes;
        List<String> gaps;
        emit(progress, "compliance", "running", 7);
        List<String> complianceBefore = new ArrayList<>(ctx.blackboardKeys());
        try {
            notes = compliance.notes(ctx);
            gaps = compliance.dataGaps(ctx);
            compliance.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded("compliance", e.getMessage());
            notes = List.of("披露生成失败,需人工补充。");
            gaps = List.of();
        }
        emit(progress, "compliance", "done", 7);
        emitDone(progress, ctx, "compliance", complianceBefore);

        ThematicReport report = assemble(ctx, rounds, findings, notes, gaps);
        ctx.memory("lead-manager").recordOutcome("thematic report assembled: " + report.overallRating());

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

    private void safe(ThematicContext ctx, ThematicSubAgent agent, PipelineProgress progress, int index) {
        emit(progress, agent.role(), "running", index);
        long t0 = ctx.now();
        List<String> before = new ArrayList<>(ctx.blackboardKeys());
        try {
            agent.contribute(ctx);
            log.debug("thematic-report phase ok run={} role={} ms={}",
                    ctx.request().collaborationId(), agent.role(), ctx.now() - t0);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
        emit(progress, agent.role(), "done", index);
        emitDone(progress, ctx, agent.role(), before);
    }

    /** Re-run an agent (revision loop) without re-emitting the chip transition. */
    private void safeNoEmit(ThematicContext ctx, ThematicSubAgent agent) {
        try {
            agent.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
    }

    /** Emit one progress transition (running/done). Fault-isolated. */
    private void emit(PipelineProgress progress, String role, String state, int index) {
        try {
            progress.onAgent(role, state, index, 7);
        } catch (RuntimeException ignored) {
            // progress reporting must never affect the run
        }
    }

    /** Diff the blackboard against {@code before} and report newly-written key→value pairs. Fault-isolated. */
    private void emitDone(PipelineProgress progress, ThematicContext ctx, String role, List<String> before) {
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

    private List<String> reviewSafe(ThematicContext ctx, ThematicCriticAgent critic) {
        try {
            return critic.review(ctx);
        } catch (RuntimeException e) {
            ctx.degraded("critic", e.getMessage());
            return List.of();
        }
    }

    private CollaborationSignature signature(ReportRequest request) {
        return new CollaborationSignature(
                Set.of("planning", "data-ingestion", "sector-scoring", "house-view",
                        "writing", "review", "compliance"),
                "research-report:THEMATIC");
    }

    private ThematicReport assemble(ThematicContext ctx, int criticRounds, List<String> findings,
            List<String> notes, List<String> gaps) {
        ThematicData.Dataset ds = ctx.dataset();
        String theme = ctx.latest(ThematicBb.THEME).orElse(ds.theme());
        String overallRating = ThematicBb.ratingLabel(ctx.latest(ThematicBb.OVERALL_RATING).orElse("NEUTRAL"));
        double overallScore = ctx.latestNum(ThematicBb.OVERALL_SCORE).orElse(0.0);
        String thesis = ctx.latest(ThematicBb.THESIS).orElse("(观点未生成)");

        List<ThematicReport.SubSectorView> views = new ArrayList<>();
        for (ThematicData.SubSector s : ds.subSectors()) {
            double score = ctx.latestNum(ThematicBb.sectorScoreKey(s.name())).orElse(0.0);
            String rating = ThematicBb.ratingLabel(ctx.latest(ThematicBb.sectorRatingKey(s.name())).orElse("NEUTRAL"));
            views.add(new ThematicReport.SubSectorView(s.name(), score, rating));
        }

        List<ReportSection> sections = new ArrayList<>();
        String outline = ctx.latest(ThematicBb.OUTLINE).orElse(ThematicBb.OUTLINE_DEFAULT);
        int order = 0;
        for (String id : outline.split(",")) {
            id = id.trim();
            String body = ctx.latest(ThematicBb.SECTION_PREFIX + id).orElse("(本节未生成)");
            sections.add(new ReportSection(id, ThematicBb.titleOf(id), body, order++));
        }

        ReportMetadata metadata = new ReportMetadata(
                model.name(), dataSourceName, ctx.modelCalls(), criticRounds, "THEMATIC-SCORING",
                gaps, notes, findings, ctx.degradations(), ctx.now());
        return new ThematicReport(theme, overallRating, overallScore, thesis, views, sections, metadata);
    }
}
