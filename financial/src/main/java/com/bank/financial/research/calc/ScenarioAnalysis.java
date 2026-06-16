package com.bank.financial.research.calc;

import java.util.ArrayList;
import java.util.List;

/**
 * Probability-weighted scenario (bull / base / bear) analysis. A research report
 * states a base-case target but defends it by bounding the upside and downside;
 * the probability-weighted expected value is the risk-adjusted figure.
 */
public final class ScenarioAnalysis {

    private ScenarioAnalysis() {
    }

    /** Weighted expected value across scenarios. Probabilities must sum to ~1.0. */
    public static ScenarioResult evaluate(List<Scenario> scenarios) {
        if (scenarios == null || scenarios.isEmpty()) {
            throw new IllegalArgumentException("need at least one scenario");
        }
        double pSum = scenarios.stream().mapToDouble(Scenario::probability).sum();
        if (Math.abs(pSum - 1.0) > 0.001) {
            throw new IllegalArgumentException("scenario probabilities must sum to 1.0 (was " + pSum + ")");
        }

        double expected = 0.0;
        double bull = Double.NEGATIVE_INFINITY;
        double bear = Double.POSITIVE_INFINITY;
        for (Scenario s : scenarios) {
            expected += s.probability() * s.perShare();
            bull = Math.max(bull, s.perShare());
            bear = Math.min(bear, s.perShare());
        }
        return new ScenarioResult(Calc.money(expected), Calc.money(bull), Calc.money(bear), List.copyOf(scenarios));
    }

    /** Build the conventional three-scenario set from a base value and up/down deltas. */
    public static List<Scenario> threePoint(double base, double bullPct, double bearPct,
            double pBull, double pBase, double pBear) {
        List<Scenario> list = new ArrayList<>();
        list.add(new Scenario("bull", Calc.money(base * (1 + bullPct)), pBull));
        list.add(new Scenario("base", Calc.money(base), pBase));
        list.add(new Scenario("bear", Calc.money(base * (1 - bearPct)), pBear));
        return list;
    }

    public record Scenario(String name, double perShare, double probability) {
    }

    public record ScenarioResult(double expectedPerShare, double bullPerShare, double bearPerShare,
            List<Scenario> scenarios) {

        public ValueRange range() {
            return new ValueRange(bearPerShare, expectedPerShare, bullPerShare);
        }
    }
}
