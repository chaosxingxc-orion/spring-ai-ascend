package com.bank.financial.research.macro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.ResearchReports;
import com.bank.financial.research.data.stub.StubMacroDataSource;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import org.junit.jupiter.api.Test;

/** Integration layer: the macro pipeline, offline and deterministic. */
class MacroReportEngineTest {

    private static final long AS_OF = 1_750_000_000_000L;

    private MacroReport run() {
        return ResearchReports.macroOffline(AS_OF).generate(ReportRequest.of("中国", "t-test", AS_OF));
    }

    @Test
    void producesTiltAndSections() {
        MacroReport r = run();
        assertFalse(r.assetTilt().isBlank());
        assertTrue(r.sections().size() >= 6);
        r.sections().forEach(s -> assertFalse(s.body().isBlank(), "blank: " + s.id()));
        assertTrue(r.indicators().size() >= 4, "GDP/CPI/PMI/M2 present");
    }

    @Test
    void numbersMatchBlackboard_noDrift() {
        MacroReport r = run();
        assertTrue(r.metadata().consistencyFindings().isEmpty(),
                () -> "unexpected findings: " + r.metadata().consistencyFindings());
    }

    @Test
    void disclosuresAndNoDegradation() {
        MacroReport r = run();
        assertTrue(r.metadata().complianceNotes().size() >= 4);
        assertTrue(r.metadata().degradations().isEmpty());
    }

    @Test
    void deterministic() {
        assertEquals(run().toMarkdown(), run().toMarkdown());
    }

    @Test
    void modelOutageDegradesButKeepsComputedStance() {
        ReportModel throwing = new ReportModel() {
            @Override
            public String name() {
                return "throwing";
            }

            @Override
            public String generate(ModelTask task) {
                throw new RuntimeException("simulated outage");
            }
        };
        MacroReport r = new MacroReportEngine(
                new StubMacroDataSource(AS_OF), throwing, null, MemoryObserver.NOOP, () -> AS_OF)
                .generate(ReportRequest.of("中国", "t-test", AS_OF));
        assertTrue(r.sections().size() >= 6);
        assertFalse(r.assetTilt().isBlank(), "computed tilt survives a model outage");
        assertFalse(r.metadata().degradations().isEmpty());
    }
}
