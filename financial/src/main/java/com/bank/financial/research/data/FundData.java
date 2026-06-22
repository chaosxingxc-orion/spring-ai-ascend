package com.bank.financial.research.data;

import java.util.List;

/** Normalised inputs for a fund / FOF report. */
public final class FundData {

    private FundData() {
    }

    /**
     * @param navs           cumulative NAV series, oldest first (total-return basis)
     * @param benchmark      optional benchmark NAV series (same length) for beta/alpha; may be empty
     * @param periodsPerYear annualisation factor (≈244 daily, 12 monthly)
     * @param riskFreeRate   annual risk-free rate for Sharpe/alpha
     */
    public record Dataset(
            String code, String name, String type,
            List<Double> navs, List<Double> benchmark,
            double periodsPerYear, double riskFreeRate,
            Provenance provenance, List<String> freshnessWarnings) {

        public Dataset {
            navs = List.copyOf(navs);
            benchmark = benchmark == null ? List.of() : List.copyOf(benchmark);
            freshnessWarnings = freshnessWarnings == null ? List.of() : List.copyOf(freshnessWarnings);
        }

        public boolean hasBenchmark() {
            return benchmark.size() == navs.size() && !benchmark.isEmpty();
        }
    }
}
