package com.bank.financial.research.agent;

import com.bank.financial.research.calc.Calc;
import com.bank.financial.research.calc.ComparablesModel;
import com.bank.financial.research.calc.ConvergenceCheck;
import com.bank.financial.research.calc.DcfModel;
import com.bank.financial.research.calc.ScenarioAnalysis;
import com.bank.financial.research.calc.ValueRange;
import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.engine.Bb;
import com.bank.financial.research.engine.ReportContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Valuation analyst. Runs the two independent valuation methods a sell-side
 * report triangulates — intrinsic (DCF) and relative (comparable multiples) —
 * checks whether they converge, and bounds the result with a probability-weighted
 * scenario analysis. The convergence verdict is the machine signal the lead
 * manager acts on: agreement → a blended target; sharp divergence → mandatory
 * reconciliation before a target is set.
 */
public final class ValuationAgent implements ReportSubAgent {

    // Documented base assumptions (a real desk would tune per name / sector).
    private static final double WACC = 0.09;
    private static final double TERMINAL_GROWTH = 0.025;
    private static final int EXPLICIT_YEARS = 5;

    @Override
    public String role() {
        return "valuation";
    }

    @Override
    public String capability() {
        return "valuation";
    }

    @Override
    public void contribute(ReportContext ctx) {
        CompanyData.Dataset ds = ctx.dataset();
        if (!ds.hasFundamentals()) {
            return;
        }
        CompanyData.Fundamentals f = ds.fundamentals();
        double growth = ctx.latestNum(Bb.GROWTH).orElse(0.08);

        List<ValueRange> methodRanges = new ArrayList<>();

        // ── Intrinsic: DCF (Gordon terminal) ──────────────────────────────────
        List<Double> fcf = new ArrayList<>();
        for (int y = 1; y <= EXPLICIT_YEARS; y++) {
            fcf.add(f.fcfBase() * Math.pow(1 + growth, y));
        }
        DcfModel.DcfResult dcf = DcfModel.gordon(
                fcf, WACC, TERMINAL_GROWTH, f.netDebt(), f.minorityInterest(), f.dilutedShares());
        ctx.putNum(role(), Bb.DCF_PER_SHARE, dcf.perShare());
        ctx.putNum(role(), Bb.DCF_TERMINAL_WEIGHT, dcf.terminalWeight());
        ValueRange dcfRange = dcf.perShareRange(0.10);
        methodRanges.add(dcfRange);

        // ── Relative: comparable multiples ────────────────────────────────────
        ComparablesModel.ComparablesResult comps = null;
        if (ds.hasPeers()) {
            CompanyData.PeerSet p = ds.peers();
            ComparablesModel.SubjectMetrics subject = new ComparablesModel.SubjectMetrics(
                    f.ebitda(), f.revenue(), f.eps(), f.netDebt(), f.minorityInterest(), f.dilutedShares());
            comps = ComparablesModel.value(subject,
                    new ComparablesModel.PeerMultiples(p.evEbitda(), p.evSales(), p.priceEarnings()));
            ctx.putNum(role(), Bb.COMPS_MEDIAN, comps.medianPerShare());
            ctx.putNum(role(), Bb.COMPS_LOW, comps.range().low());
            ctx.putNum(role(), Bb.COMPS_HIGH, comps.range().high());
            methodRanges.add(comps.range());
        }

        // ── Triangulation convergence ─────────────────────────────────────────
        double blended;
        if (methodRanges.size() >= 2) {
            ConvergenceCheck.ConvergenceResult conv = ConvergenceCheck.of(methodRanges);
            ctx.put(role(), Bb.CONVERGENCE_VERDICT, conv.verdict().name());
            ctx.putNum(role(), Bb.CONVERGENCE_BLENDED, conv.blendedPerShare());
            ctx.putNum(role(), Bb.CONVERGENCE_DISPERSION, conv.dispersion());
            blended = conv.blendedPerShare();
        } else {
            // Only one method available — no triangulation; be explicit about it.
            ctx.put(role(), Bb.CONVERGENCE_VERDICT, "SINGLE_METHOD");
            ctx.putNum(role(), Bb.CONVERGENCE_BLENDED, dcf.perShare());
            blended = dcf.perShare();
        }

        // ── Scenario bounding (bull / base / bear) ────────────────────────────
        ScenarioAnalysis.ScenarioResult scenario = ScenarioAnalysis.evaluate(
                ScenarioAnalysis.threePoint(blended, 0.20, 0.15, 0.30, 0.50, 0.20));
        ctx.putNum(role(), Bb.SCENARIO_BULL, scenario.bullPerShare());
        ctx.putNum(role(), Bb.SCENARIO_BASE, blended);
        ctx.putNum(role(), Bb.SCENARIO_BEAR, scenario.bearPerShare());
        ctx.putNum(role(), Bb.SCENARIO_EXPECTED, scenario.expectedPerShare());

        // Keep the assumption explicit for the model & estimates section.
        ctx.putNum(role(), "valuation.wacc", Calc.rate(WACC));
        ctx.putNum(role(), "valuation.terminalGrowth", Calc.rate(TERMINAL_GROWTH));
    }
}
