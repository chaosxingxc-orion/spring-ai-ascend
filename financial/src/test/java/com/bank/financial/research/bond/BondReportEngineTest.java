package com.bank.financial.research.bond;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.ResearchReports;
import com.bank.financial.research.data.stub.StubBondDataSource;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import org.junit.jupiter.api.Test;

/** Integration layer: the bond pipeline, offline and deterministic. */
class BondReportEngineTest {

    private static final long AS_OF = 1_750_000_000_000L;

    private BondReport run() {
        return ResearchReports.bondOffline(AS_OF).generate(ReportRequest.of("DEMOBOND", "t-test", AS_OF));
    }

    @Test
    void computesMetricsAndStance() {
        BondReport r = run();
        assertTrue(r.name().contains("晨曦"), r.name());
        assertEquals("AA+", r.rating());
        assertFalse(r.stance().isBlank());
        assertTrue(r.metrics().ytm() > 0.045, "discount issue yields above coupon");
        assertTrue(r.metrics().creditSpread() > 0, "positive credit spread");
    }

    @Test
    void sectionsPresentAndConsistent() {
        BondReport r = run();
        assertTrue(r.sections().size() >= 4);
        r.sections().forEach(s -> assertFalse(s.body().isBlank(), "blank: " + s.id()));
        assertTrue(r.metadata().consistencyFindings().isEmpty(),
                () -> "unexpected findings: " + r.metadata().consistencyFindings());
    }

    @Test
    void disclosuresAndNoDegradation() {
        BondReport r = run();
        assertTrue(r.metadata().complianceNotes().size() >= 4);
        assertTrue(r.metadata().degradations().isEmpty());
    }

    @Test
    void deterministic() {
        assertEquals(run().toMarkdown(), run().toMarkdown());
    }

    @Test
    void modelOutageDegradesButKeepsComputedMetrics() {
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
        BondReport r = new BondReportEngine(
                new StubBondDataSource(AS_OF), throwing, null, MemoryObserver.NOOP, () -> AS_OF)
                .generate(ReportRequest.of("DEMOBOND", "t-test", AS_OF));
        assertTrue(r.sections().size() >= 4);
        assertTrue(r.metrics().ytm() > 0, "computed metrics survive a model outage");
        assertFalse(r.metadata().degradations().isEmpty());
    }
}
