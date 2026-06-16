package com.bank.financial.research.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit layer: the financial math against hand-verified expected values. These
 * pin the numbers the whole report rests on, so a regression in the calculators
 * is caught here, not in a reader's spreadsheet.
 */
class CalcEngineTest {

    private static final double EPS = 0.02;

    @Test
    void dcfGordon_matchesHandComputedValue() {
        // 5y FCF of 100, WACC 10%, g 2%, no debt/minority, 10 shares.
        DcfModel.DcfResult r = DcfModel.gordon(
                List.of(100.0, 100.0, 100.0, 100.0, 100.0), 0.10, 0.02, 0, 0, 10);
        assertEquals(1170.75, r.enterpriseValue(), 0.1);
        assertEquals(117.08, r.perShare(), EPS);
        assertEquals(0.6762, r.terminalWeight(), 0.001);
    }

    @Test
    void dcf_rejectsGrowthAboveWacc() {
        assertThrows(IllegalArgumentException.class,
                () -> DcfModel.gordon(List.of(100.0), 0.05, 0.06, 0, 0, 10));
    }

    @Test
    void dcf_appliesEnterpriseToEquityBridge() {
        DcfModel.DcfResult noDebt = DcfModel.gordon(List.of(100.0, 100.0), 0.10, 0.02, 0, 0, 10);
        DcfModel.DcfResult withDebt = DcfModel.gordon(List.of(100.0, 100.0), 0.10, 0.02, 200, 50, 10);
        // Net debt + minority of 250 reduces equity by 250 → per share by 25.
        assertEquals(noDebt.perShare() - 25.0, withDebt.perShare(), EPS);
    }

    @Test
    void comparables_medianAndBridge() {
        ComparablesModel.SubjectMetrics subject =
                new ComparablesModel.SubjectMetrics(1000, 5000, 5.0, 2000, 0, 100);
        ComparablesModel.PeerMultiples peers = new ComparablesModel.PeerMultiples(8.0, 2.0, 15.0);
        ComparablesModel.ComparablesResult r = ComparablesModel.value(subject, peers);
        // EV/EBITDA: (8*1000-2000)/100=60; EV/Sales:(2*5000-2000)/100=80; P/E:15*5=75
        assertEquals(60.0, r.impliedPerShare().get("EV/EBITDA"), EPS);
        assertEquals(80.0, r.impliedPerShare().get("EV/Sales"), EPS);
        assertEquals(75.0, r.impliedPerShare().get("P/E"), EPS);
        assertEquals(75.0, r.medianPerShare(), EPS);
        assertEquals(71.67, r.meanPerShare(), EPS);
    }

    @Test
    void convergence_detectsConvergentAndDivergent() {
        ConvergenceCheck.ConvergenceResult conv = ConvergenceCheck.of(List.of(
                new ValueRange(100, 105, 110), new ValueRange(102, 106, 112)));
        assertEquals(ConvergenceCheck.Verdict.CONVERGENT, conv.verdict());
        assertEquals(106.0, conv.blendedPerShare(), EPS); // overlap [102,110] midpoint

        ConvergenceCheck.ConvergenceResult div = ConvergenceCheck.of(List.of(
                new ValueRange(105.37, 117.08, 128.79), new ValueRange(60, 75, 80)));
        assertEquals(ConvergenceCheck.Verdict.DIVERGENT, div.verdict());
        assertTrue(div.requiresReconciliation());
    }

    @Test
    void scenario_probabilityWeightedExpectation() {
        ScenarioAnalysis.ScenarioResult r = ScenarioAnalysis.evaluate(
                ScenarioAnalysis.threePoint(100, 0.20, 0.15, 0.30, 0.50, 0.20));
        assertEquals(120.0, r.bullPerShare(), EPS);
        assertEquals(85.0, r.bearPerShare(), EPS);
        assertEquals(103.0, r.expectedPerShare(), EPS); // .3*120 + .5*100 + .2*85
    }

    @Test
    void scenario_rejectsProbabilitiesNotSummingToOne() {
        assertThrows(IllegalArgumentException.class, () -> ScenarioAnalysis.evaluate(
                List.of(new ScenarioAnalysis.Scenario("a", 100, 0.5),
                        new ScenarioAnalysis.Scenario("b", 90, 0.2))));
    }

    @Test
    void earningsSurprise_sueClassification() {
        EarningsSurprise.SurpriseResult r = EarningsSurprise.sue(3.2, 3.35, 0.18);
        assertEquals(-0.8333, r.sue(), 0.001);
        assertEquals(EarningsSurprise.Surprise.MISS, r.classification());

        assertEquals(EarningsSurprise.Surprise.BEAT,
                EarningsSurprise.sue(3.6, 3.35, 0.18).classification());
        assertEquals(EarningsSurprise.Surprise.INLINE,
                EarningsSurprise.sue(3.38, 3.35, 0.18).classification());
    }

    @Test
    void revenueImpact_driverFlowThrough() {
        RevenueImpactModel.ImpactResult r = RevenueImpactModel.analyze(
                1000, 0.40, 0.15, 100, List.of(new RevenueImpactModel.Driver("需求", 0.5, 0.10)));
        assertEquals(50.0, r.deltaRevenue(), EPS);        // 1000*0.5*0.1
        assertEquals(0.05, r.deltaRevenuePct(), 0.001);
        assertEquals(17.0, r.deltaNetIncome(), EPS);      // 50*0.4*0.85
        assertEquals(0.17, r.epsImpact(), 0.001);
    }

    @Test
    void trendForecast_multiMethodConvergence() {
        TrendForecast.ForecastResult r = TrendForecast.project(List.of(100.0, 110.0, 120.0, 130.0), 1);
        assertEquals(140.0, r.methodForecasts().get("OLS"), EPS);
        assertEquals(140.0, r.methodForecasts().get("Momentum"), EPS);
        assertEquals(141.88, r.methodForecasts().get("CAGR"), 0.1);
        assertTrue(r.convergent());
        assertEquals(140.63, r.blended(), 0.1);
    }

    @Test
    void sensitivity_marksUndefinedCellsWhenGrowthExceedsWacc() {
        SensitivityAnalysis.SensitivityGrid grid = SensitivityAnalysis.dcfGrid(
                List.of(100.0, 100.0, 100.0), 0, 0, 10,
                new double[] {0.08, 0.10}, new double[] {0.02, 0.12});
        // growth 0.12 >= wacc 0.08 and 0.10 → those cells are NaN (n/m).
        assertTrue(Double.isNaN(grid.cells()[1][0]));
        assertTrue(Double.isNaN(grid.cells()[1][1]));
        assertFalse(Double.isNaN(grid.cells()[0][0]));
    }
}
