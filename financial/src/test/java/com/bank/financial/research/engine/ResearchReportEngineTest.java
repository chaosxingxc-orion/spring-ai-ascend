package com.bank.financial.research.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.ResearchReports;
import com.bank.financial.research.data.DataIngestionService;
import com.bank.financial.research.data.FreshnessPolicy;
import com.bank.financial.research.data.stub.StubResearchDataSource;
import com.bank.financial.research.model.ScriptedReportModel;
import com.huawei.ascend.a2a.memory.experience.CollaborationSignature;
import com.huawei.ascend.a2a.memory.experience.ExperienceMemoryKit;
import com.huawei.ascend.a2a.memory.experience.InMemoryExperienceStore;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Integration layer: the full multi-agent pipeline, offline and deterministic.
 * Asserts the things that make the report trustworthy — every section present,
 * the house view set, the computed figures actually appearing in the prose,
 * numeric consistency, transparent disclosures, and cross-run experience capture.
 */
class ResearchReportEngineTest {

    private static final long AS_OF = 1_750_000_000_000L; // fixed instant → deterministic

    private ResearchReport runDemo() {
        return ResearchReports.offline(AS_OF)
                .generate(ReportRequest.equity("DEMO", "t-test", AS_OF));
    }

    @Test
    void producesCompleteHouseView() {
        ResearchReport r = runDemo();
        assertTrue(r.company().contains("晨曦"), r.company());
        assertFalse(r.rating().isBlank());
        assertTrue(r.priceTarget() > 0, "price target should be positive");
        assertEquals(64.5, r.currentPrice(), 0.001);
    }

    @Test
    void allOutlineSectionsPresentAndNonTrivial() {
        ResearchReport r = runDemo();
        assertEquals(6, r.sections().size());
        for (ReportSection s : r.sections()) {
            assertFalse(s.body().isBlank(), "section blank: " + s.id());
            assertTrue(s.length() > 50, "section too short: " + s.id());
        }
    }

    @Test
    void reportNumbersMatchTheBlackboard_noDrift() {
        ResearchReport r = runDemo();
        // The consistency checker ran inside the pipeline and found nothing.
        assertTrue(r.metadata().consistencyFindings().isEmpty(),
                () -> "unexpected consistency findings: " + r.metadata().consistencyFindings());
        // And the headline computed figures actually appear in the rendered report.
        String md = r.toMarkdown();
        assertTrue(md.contains(Bb.fmt(r.priceTarget())), "price target missing from body");
        assertTrue(md.contains("77.65"), "DCF per share missing from body"); // computed DCF
        assertTrue(md.contains("61.75"), "comps median missing from body");  // computed comps
    }

    @Test
    void valuationConvergenceAndScenarioComputed() {
        ResearchReport r = runDemo();
        String verdict = r.metadata().convergenceVerdict();
        assertTrue(Set.of("CONVERGENT", "PARTIAL", "DIVERGENT", "SINGLE_METHOD").contains(verdict), verdict);
        // Scenario + valuation appear under the valuation/scenario sections.
        assertTrue(r.toMarkdown().contains("DCF每股") || r.toMarkdown().contains("DCF 每股")
                || r.toMarkdown().contains("DCF"));
    }

    @Test
    void disclosuresIncludeCertificationAndSignoffRequirement() {
        ResearchReport r = runDemo();
        List<String> notes = r.metadata().complianceNotes();
        assertTrue(notes.size() >= 5, "expected full disclosure set");
        assertTrue(notes.stream().anyMatch(n -> n.contains("分析师认证")));
        assertTrue(notes.stream().anyMatch(n -> n.contains("监督分析师")), "must require SA sign-off");
        assertTrue(notes.stream().anyMatch(n -> n.contains("数据来源")));
    }

    @Test
    void noDataGapsWithFullStub() {
        ResearchReport r = runDemo();
        assertTrue(r.metadata().dataGaps().isEmpty(), () -> "unexpected gaps: " + r.metadata().dataGaps());
        assertTrue(r.metadata().modelCalls() > 0);
    }

    @Test
    void deterministic_sameInputsSameReport() {
        assertEquals(runDemo().toMarkdown(), runDemo().toMarkdown());
    }

    @Test
    void crossRunExperienceIsCaptured() {
        InMemoryExperienceStore shared = new InMemoryExperienceStore();
        DataIngestionService ingestion = new DataIngestionService(
                new StubResearchDataSource(AS_OF), FreshnessPolicy.days(90));
        ResearchReportEngine engine = new ResearchReportEngine(
                ingestion, "stub", new ScriptedReportModel(), shared, MemoryObserver.NOOP, () -> AS_OF);
        engine.generate(ReportRequest.equity("DEMO", "t-test", AS_OF));

        // The run distilled its blackboard into experience under the run signature.
        CollaborationSignature sig = new CollaborationSignature(
                Set.of("planning", "data-ingestion", "financial-modeling", "valuation",
                        "sector-macro", "house-view", "writing", "review", "compliance"),
                "research-report:EQUITY");
        List<?> lessons = ExperienceMemoryKit.forTenant(shared, "t-test").recall(sig, 10);
        assertFalse(lessons.isEmpty(), "expected cross-run lessons to be recorded");
    }
}
