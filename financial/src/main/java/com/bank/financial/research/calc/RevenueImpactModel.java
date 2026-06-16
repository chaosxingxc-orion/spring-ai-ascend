package com.bank.financial.research.calc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Driver-based impact analysis: quantifies how external information (a price
 * shock, FX move, demand swing, regulatory change, rate move) flows through to
 * revenue and earnings. This is the "外部信息影响下的收入/收益分析" core — it turns a
 * qualitative news item into a numeric estimate the report can defend.
 *
 * <p>Each driver carries an <em>elasticity</em> (the % change in revenue per 1%
 * change in the driver) and a <em>shock</em> (the observed/assumed % change in
 * the driver). The revenue delta is the sum of {@code base × elasticity × shock}
 * across drivers (a first-order linear decomposition), and earnings impact flows
 * through at the assumed incremental (contribution) margin.
 */
public final class RevenueImpactModel {

    private RevenueImpactModel() {
    }

    /**
     * @param baseRevenue        current/base-case revenue
     * @param incrementalMargin  contribution margin on incremental revenue (e.g. 0.4 = 40%)
     * @param taxRate            tax rate applied to incremental operating profit
     * @param dilutedShares      for the per-share earnings impact (&gt; 0)
     * @param drivers            the external drivers with their elasticity and shock
     */
    public static ImpactResult analyze(double baseRevenue, double incrementalMargin, double taxRate,
            double dilutedShares, List<Driver> drivers) {
        Calc.requirePositive("dilutedShares", dilutedShares);
        if (drivers == null || drivers.isEmpty()) {
            throw new IllegalArgumentException("need at least one driver");
        }

        Map<String, Double> contributionByDriver = new LinkedHashMap<>();
        double totalDeltaRevenue = 0.0;
        for (Driver d : drivers) {
            double contribution = baseRevenue * d.elasticity() * d.shockPct();
            contributionByDriver.put(d.name(), Calc.money(contribution));
            totalDeltaRevenue += contribution;
        }

        double projectedRevenue = baseRevenue + totalDeltaRevenue;
        double deltaRevenuePct = Calc.pctChange(baseRevenue, projectedRevenue);

        // Flow-through: incremental revenue → operating profit at the incremental
        // margin → net income after tax → per-share earnings impact.
        double deltaOperatingProfit = totalDeltaRevenue * incrementalMargin;
        double deltaNetIncome = deltaOperatingProfit * (1.0 - taxRate);
        double epsImpact = deltaNetIncome / dilutedShares;

        return new ImpactResult(
                Calc.money(baseRevenue), Calc.money(projectedRevenue), Calc.money(totalDeltaRevenue),
                Calc.rate(deltaRevenuePct), Calc.money(deltaNetIncome), Calc.money(epsImpact),
                contributionByDriver);
    }

    /** Rank drivers by absolute revenue contribution (largest impact first). */
    public static List<Map.Entry<String, Double>> rankedDrivers(ImpactResult result) {
        List<Map.Entry<String, Double>> entries = new ArrayList<>(result.contributionByDriver().entrySet());
        entries.sort((a, b) -> Double.compare(Math.abs(b.getValue()), Math.abs(a.getValue())));
        return entries;
    }

    /**
     * @param name      a label, e.g. "油价" / "美元汇率" / "监管降费"
     * @param elasticity revenue %-change per 1% change in the driver (can be negative)
     * @param shockPct  the driver's %-change (e.g. +0.10 for a 10% rise)
     */
    public record Driver(String name, double elasticity, double shockPct) {
    }

    public record ImpactResult(
            double baseRevenue,
            double projectedRevenue,
            double deltaRevenue,
            double deltaRevenuePct,
            double deltaNetIncome,
            double epsImpact,
            Map<String, Double> contributionByDriver) {

        public ImpactResult {
            contributionByDriver = Map.copyOf(contributionByDriver);
        }
    }
}
