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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

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
        long now = request.asOfEpochMs();
        CompanyData.Dataset dataset = ingestion.assemble(request.ticker(), 5, now);

        SharedMemoryStore store = new InMemorySharedMemoryStore();
        ReportContext ctx = new ReportContext(request, dataset, model, store, observer, clock);

        CollaborationSignature signature = signature(request);
        ExperienceMemoryKit experience = ExperienceMemoryKit.forTenant(experienceStore, request.tenantId());

        // Cross-run knowledge: recall lessons from prior similar reports.
        List<Lesson> recalled = experience.recall(signature, 5);
        if (!recalled.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Lesson l : recalled) {
                sb.append("- ").append(l.text()).append('\n');
            }
            ctx.put("planner", "experience.recalled", sb.toString());
        }

        // ── PLAN → INGEST → ANALYZE ───────────────────────────────────────────
        new PlannerAgent().contribute(ctx);
        new DataIngestionAgent().contribute(ctx);
        new QuantModelAgent().contribute(ctx);
        new ValuationAgent().contribute(ctx);
        new SectorMacroAgent().contribute(ctx);

        // ── CONVERGE: the manager forms the house view (sole decision-maker) ──
        LeadManagerAgent manager = new LeadManagerAgent();
        manager.contribute(ctx);
        ctx.memory("lead-manager").recordHandover("writer", "house view set (rating + target)");

        // ── WRITE → CRITIQUE (bounded writer↔critic revision loop) ────────────
        WriterAgent writer = new WriterAgent();
        CriticAgent critic = new CriticAgent();
        writer.contribute(ctx);
        List<String> findings = critic.review(ctx);
        int rounds = 0;
        while (!findings.isEmpty() && rounds < request.budget().maxCriticRounds() && ctx.withinTime()) {
            rounds++;
            writer.contribute(ctx); // appends revised section versions (audit-logged)
            findings = critic.review(ctx);
        }

        // ── COMPLY ────────────────────────────────────────────────────────────
        ComplianceAgent compliance = new ComplianceAgent();
        List<String> complianceNotes = compliance.notes(ctx);
        compliance.contribute(ctx);

        // ── ASSEMBLE from the blackboard ──────────────────────────────────────
        ResearchReport report = assemble(ctx, rounds, findings, complianceNotes, compliance.dataGaps(ctx));
        ctx.memory("lead-manager").recordOutcome("report assembled: " + report.rating());

        // Distill this run's blackboard into cross-run experience (PII-stripped).
        SharedMemoryKit blackboard = SharedMemoryKit.forCollaboration(
                store, request.tenantId(), request.collaborationId());
        new DefaultCollaborationMemoryHook(experience, false).onCollaborationEnd(signature, blackboard);

        return report;
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
                dataGaps, complianceNotes, findings, ctx.now());

        return new ResearchReport(
                request.ticker(), company, currency, rating, priceTarget, currentPrice, upside,
                thesis, sections, metadata);
    }
}
