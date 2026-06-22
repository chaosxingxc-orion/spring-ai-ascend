package com.bank.financial.research.calc;

import com.bank.financial.research.data.MacroData;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Deterministic macro assessment. Turns the raw indicator readings (GDP YoY, CPI
 * YoY, manufacturing PMI, M2 YoY) into four signed sub-scores — growth, inflation,
 * activity, liquidity — a composite macro score, and an asset tilt (bonds-favoured
 * / equity-favoured / neutral). The mapping is an explicit, documented rule set (a
 * desk would calibrate the thresholds), so the conclusion is computed and
 * reproducible; the writer only narrates it. All scores are in [-1, 1].
 *
 * <p>Sign convention: growth/activity/liquidity positive = supportive of risk
 * assets; inflation score positive = benign (low) for policy room, negative =
 * hot. The asset tilt favours bonds when growth/activity are weak and policy has
 * room (low inflation, easy money); favours equity when growth + activity are firm.
 */
public final class MacroCalc {

    private MacroCalc() {
    }

    public enum AssetTilt { EQUITY_FAVOURED, BONDS_FAVOURED, NEUTRAL }

    public record MacroStance(
            double growth, double inflation, double activity, double liquidity,
            double composite, AssetTilt tilt) {
    }

    // ── reference anchors (documented heuristics) ──────────────────────────────
    private static final double GDP_TREND = 5.0;   // around-trend growth
    private static final double CPI_TARGET = 2.0;   // comfortable inflation
    private static final double PMI_NEUTRAL = 50.0; // expansion threshold
    private static final double M2_TREND = 8.0;     // around-trend money growth

    /** Compute the macro stance from the dataset's indicators. */
    public static MacroStance assess(MacroData.Dataset ds) {
        double gdp = latest(ds, MacroData.Domain.GROWTH);
        double cpi = latest(ds, MacroData.Domain.INFLATION);
        double pmi = latest(ds, MacroData.Domain.ACTIVITY);
        double m2 = latest(ds, MacroData.Domain.MONETARY);

        // Growth: GDP vs trend, ±2pp → ±1.
        double growth = clamp((gdp - GDP_TREND) / 2.0);
        // Inflation: benign (positive) when near/below target with no deflation; hot (negative) when high.
        double inflation = Double.isNaN(cpi) ? 0 : clamp((CPI_TARGET - cpi) / 2.0);
        // Activity: PMI vs 50, ±2 points → ±1.
        double activity = Double.isNaN(pmi) ? 0 : clamp((pmi - PMI_NEUTRAL) / 2.0);
        // Liquidity: M2 vs trend, ±3pp → ±1 (more money = more supportive).
        double liquidity = Double.isNaN(m2) ? 0 : clamp((m2 - M2_TREND) / 3.0);

        double composite = Calc.rate((growth + activity + liquidity + inflation) / 4.0);

        // Asset tilt: equity when growth+activity firm; bonds when growth/activity weak
        // and policy has room (benign inflation, ample liquidity).
        AssetTilt tilt;
        double risk = growth + activity;
        if (risk >= 0.4) {
            tilt = AssetTilt.EQUITY_FAVOURED;
        } else if (risk <= -0.2 && (inflation >= 0 || liquidity >= 0)) {
            tilt = AssetTilt.BONDS_FAVOURED;
        } else {
            tilt = AssetTilt.NEUTRAL;
        }
        return new MacroStance(
                Calc.rate(growth), Calc.rate(inflation), Calc.rate(activity), Calc.rate(liquidity),
                composite, tilt);
    }

    /** Latest headline reading for a domain (NaN when the domain is absent). */
    private static double latest(MacroData.Dataset ds, MacroData.Domain d) {
        List<MacroData.Indicator> in = ds.inDomain(d);
        OptionalDouble v = in.stream().mapToDouble(MacroData.Indicator::value).findFirst();
        return v.isPresent() ? v.getAsDouble() : Double.NaN;
    }

    private static double clamp(double v) {
        return Math.max(-1.0, Math.min(1.0, v));
    }
}
