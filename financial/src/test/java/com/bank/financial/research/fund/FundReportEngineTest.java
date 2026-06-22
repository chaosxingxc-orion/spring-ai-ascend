package com.bank.financial.research.fund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bank.financial.research.ResearchReports;
import com.bank.financial.research.data.stub.StubFundDataSource;
import com.bank.financial.research.engine.ReportRequest;
import com.bank.financial.research.model.ReportModel;
import com.huawei.ascend.a2a.memory.obs.MemoryObserver;
import org.junit.jupiter.api.Test;

/** Integration layer: the fund pipeline, offline and deterministic. */
class FundReportEngineTest {

    private static final long AS_OF = 1_750_000_000_000L;

    private FundReport run() {
        return ResearchReports.fundOffline(AS_OF).generate(ReportRequest.of("DEMOFUND", "t-test", AS_OF));
    }

    @Test
    void computesMetricsAndRating() {
        FundReport r = run();
        assertTrue(r.name().contains("晨曦"), r.name());
        assertFalse(r.overallRating().isBlank());
        assertTrue(r.metrics().cumReturn() > 0, "stub fund grows over 3y");
        assertTrue(r.metrics().maxDrawdown() < 0, "must have some drawdown");
        assertTrue(r.metrics().beta() > 0, "stub has a benchmark → beta computed");
    }

    @Test
    void allSectionsPresentAndConsistent() {
        FundReport r = run();
        assertEquals(4, r.sections().size());
        r.sections().forEach(s -> assertFalse(s.body().isBlank(), "blank: " + s.id()));
        assertTrue(r.metadata().consistencyFindings().isEmpty(),
                () -> "unexpected findings: " + r.metadata().consistencyFindings());
        // The computed Sharpe appears in the rendered report.
        assertTrue(r.toMarkdown().contains(com.bank.financial.research.engine.Bb.fmt(r.metrics().sharpe())));
    }

    @Test
    void disclosuresAndNoDegradation() {
        FundReport r = run();
        assertTrue(r.metadata().complianceNotes().size() >= 4);
        assertTrue(r.metadata().complianceNotes().stream().anyMatch(n -> n.contains("过往业绩")));
        assertTrue(r.metadata().complianceNotes().stream().anyMatch(n -> n.contains("复核签发")));
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
        FundReportEngine engine = new FundReportEngine(
                new StubFundDataSource(AS_OF), throwing, null, MemoryObserver.NOOP, () -> AS_OF);
        FundReport r = engine.generate(ReportRequest.of("DEMOFUND", "t-test", AS_OF));
        assertEquals(4, r.sections().size());
        assertTrue(r.metrics().cumReturn() > 0, "computed metrics survive a model outage");
        assertFalse(r.metadata().degradations().isEmpty());
    }
}
