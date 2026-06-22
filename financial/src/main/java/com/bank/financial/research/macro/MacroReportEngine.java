package com.bank.financial.research.macro;

import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.data.MacroDataSource;
import com.bank.financial.research.engine.PipelineProgress;
import com.bank.financial.research.engine.ReportMetadata;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.engine.ReportSection;
import com.bank.financial.research.macro.agent.MacroAnalysisAgent;
import com.bank.financial.research.macro.agent.MacroComplianceAgent;
import com.bank.financial.research.macro.agent.MacroCriticAgent;
import com.bank.financial.research.macro.agent.MacroDataAgent;
import com.bank.financial.research.macro.agent.MacroPlannerAgent;
import com.bank.financial.research.macro.agent.MacroStrategistAgent;
import com.bank.financial.research.macro.agent.MacroWriterAgent;
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
 * Orchestrates the macro &amp; policy desk over the shared-memory blackboard,
 * reusing the engine discipline: the macro stance is computed deterministically
 * ({@code MacroCalc}), the model only narrates, every step is fault-isolated,
 * budget-bounded, instrumented, and distilled into cross-run experience.
 *
 * <pre>PLAN → INGEST → ANALYZE → CONVERGE(strategist) → WRITE → CRITIQUE → COMPLY → ASSEMBLE</pre>
 */
public final class MacroReportEngine {

    private static final Logger log = LoggerFactory.getLogger("research.engine");

    private final MacroDataSource source;
    private final ReportModel model;
    private final ExperienceStore experienceStore;
    private final MemoryObserver observer;
    private final LongSupplier clock;
    private final Set<MacroData.Domain> domains;

    public MacroReportEngine(MacroDataSource source, ReportModel model, ExperienceStore experienceStore,
            MemoryObserver observer, LongSupplier clock) {
        this(source, model, experienceStore, observer, clock, Set.of());
    }

    public MacroReportEngine(MacroDataSource source, ReportModel model, ExperienceStore experienceStore,
            MemoryObserver observer, LongSupplier clock, Set<MacroData.Domain> domains) {
        this.source = source;
        this.model = model;
        this.experienceStore = experienceStore == null ? new InMemoryExperienceStore() : experienceStore;
        this.observer = observer == null ? MemoryObserver.NOOP : observer;
        this.clock = clock == null ? System::currentTimeMillis : clock;
        this.domains = domains == null ? Set.of() : Set.copyOf(domains);
    }

    public MacroReport generate(ReportRequest request) {
        return generate(request, PipelineProgress.NOOP);
    }

    public MacroReport generate(ReportRequest request, PipelineProgress progress) {
        if (progress == null) {
            progress = PipelineProgress.NOOP;
        }
        Timer.Sample sample = Timer.start(Metrics.globalRegistry);
        boolean ok = false;
        log.info("macro-report start run={} region={} model={}",
                request.collaborationId(), request.ticker(), model.name());
        try {
            MacroReport r = doGenerate(request, progress);
            ok = true;
            return r;
        } finally {
            sample.stop(Metrics.timer("research.report.latency", "type", "macro"));
            Metrics.counter("research.report.count", "type", "macro", "outcome", ok ? "ok" : "error").increment();
        }
    }

    private MacroReport doGenerate(ReportRequest request, PipelineProgress progress) {
        MacroData.Dataset dataset = source.load(request.ticker(), domains, request.asOfEpochMs());
        SharedMemoryStore store = new InMemorySharedMemoryStore();
        MacroContext ctx = new MacroContext(request, dataset, model, store, observer, clock);

        CollaborationSignature signature = signature();
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

        safe(ctx, new MacroPlannerAgent(), progress, 1);
        safe(ctx, new MacroDataAgent(), progress, 2);
        ctx.memory("data").recordHandover("analysis", "指标录入 → 量化打分");
        safe(ctx, new MacroAnalysisAgent(), progress, 3);
        ctx.memory("analysis").recordHandover("lead-manager", "宏观打分完成 → 定调");
        ctx.memory("lead-manager").get(MacroBb.COMPOSITE); // READ edge
        safe(ctx, new MacroStrategistAgent(), progress, 4);
        ctx.memory("lead-manager").recordHandover("writer", "资产配置倾向已定 → 撰写");

        MacroWriterAgent writer = new MacroWriterAgent();
        MacroCriticAgent critic = new MacroCriticAgent();
        safe(ctx, writer, progress, 5);
        ctx.memory("writer").recordHandover("critic", "初稿完成 → 评审");
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

        MacroComplianceAgent compliance = new MacroComplianceAgent();
        emit(progress, "compliance", "running", 7);
        List<String> complianceBefore = new ArrayList<>(ctx.blackboardKeys());
        List<String> notes;
        List<String> gaps;
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

        MacroReport report = assemble(ctx, rounds, findings, notes, gaps);
        ctx.memory("lead-manager").recordOutcome("macro report assembled: " + report.assetTilt());
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

    private void safe(MacroContext ctx, MacroSubAgent agent, PipelineProgress progress, int index) {
        emit(progress, agent.role(), "running", index);
        List<String> before = new ArrayList<>(ctx.blackboardKeys());
        try {
            agent.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
        emit(progress, agent.role(), "done", index);
        emitDone(progress, ctx, agent.role(), before);
    }

    private void safeNoEmit(MacroContext ctx, MacroSubAgent agent) {
        try {
            agent.contribute(ctx);
        } catch (RuntimeException e) {
            ctx.degraded(agent.role(), e.getMessage());
        }
    }

    private void emitDone(PipelineProgress progress, MacroContext ctx, String role, List<String> before) {
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

    private void emit(PipelineProgress progress, String role, String state, int index) {
        try {
            progress.onAgent(role, state, index, 7);
        } catch (RuntimeException ignored) {
            // progress reporting must never affect the run
        }
    }

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

    private List<String> reviewSafe(MacroContext ctx, MacroCriticAgent critic) {
        try {
            return critic.review(ctx);
        } catch (RuntimeException e) {
            ctx.degraded("critic", e.getMessage());
            return List.of();
        }
    }

    private CollaborationSignature signature() {
        return new CollaborationSignature(
                Set.of("planning", "data-ingestion", "macro-analytics", "house-view",
                        "writing", "review", "compliance"),
                "research-report:MACRO");
    }

    private MacroReport assemble(MacroContext ctx, int criticRounds, List<String> findings,
            List<String> notes, List<String> gaps) {
        MacroData.Dataset ds = ctx.dataset();
        String region = ctx.latest(MacroBb.REGION).orElse(ds.region());
        String tilt = MacroBb.tiltLabel(ctx.latest(MacroBb.ASSET_TILT).orElse("NEUTRAL"));
        double composite = ctx.latestNum(MacroBb.COMPOSITE).orElse(0.0);
        String thesis = ctx.latest(MacroBb.THESIS).orElse("(观点未生成)");

        List<MacroReport.IndicatorView> views = new ArrayList<>();
        for (MacroData.Indicator i : ds.indicators()) {
            views.add(new MacroReport.IndicatorView(i.label(), i.value(), i.unit(), i.period()));
        }

        List<ReportSection> sections = new ArrayList<>();
        String outline = ctx.latest(MacroBb.OUTLINE).orElse(MacroBb.OUTLINE_DEFAULT);
        int order = 0;
        for (String id : outline.split(",")) {
            id = id.trim();
            String body = ctx.latest(MacroBb.SECTION_PREFIX + id).orElse("(本节未生成)");
            sections.add(new ReportSection(id, MacroBb.titleOf(id), body, order++));
        }

        ReportMetadata metadata = new ReportMetadata(
                model.name(), source.name(), ctx.modelCalls(), criticRounds, "MACRO-SCORING",
                gaps, notes, findings, ctx.degradations(), ctx.now());
        return new MacroReport(region, tilt, composite, thesis, views, sections, metadata);
    }
}
