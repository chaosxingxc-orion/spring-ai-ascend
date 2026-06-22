package com.bank.financial.research.data.stub;

import com.bank.financial.research.data.FundData;
import com.bank.financial.research.data.FundDataSource;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.SourceType;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic offline fund: a 36-month NAV series built from a fixed monthly
 * return pattern (so it has real ups, downs and a drawdown) plus a tamer
 * benchmark. Byte-identical every run — used by tests and the offline demo.
 */
public final class StubFundDataSource implements FundDataSource {

    // A fixed 12-month return pattern, cycled 3× → 36 returns, 37 NAV points.
    private static final double[] PATTERN = {
        0.03, 0.02, -0.04, 0.025, 0.01, -0.02, 0.03, -0.05, 0.04, 0.015, 0.02, -0.01
    };

    private final long asOfEpochMs;

    public StubFundDataSource(long asOfEpochMs) {
        this.asOfEpochMs = asOfEpochMs;
    }

    @Override
    public String name() {
        return "stub-fund";
    }

    @Override
    public FundData.Dataset load(String fundCode, long asOf) {
        List<Double> navs = new ArrayList<>();
        List<Double> bench = new ArrayList<>();
        double nav = 1.0;
        double bm = 1.0;
        navs.add(nav);
        bench.add(bm);
        for (int cycle = 0; cycle < 3; cycle++) {
            for (double r : PATTERN) {
                nav *= (1.0 + r);
                bm *= (1.0 + r * 0.7); // benchmark: 70% of the fund's moves
                navs.add(nav);
                bench.add(bm);
            }
        }
        String code = (fundCode == null || fundCode.isBlank()) ? "DEMOFUND" : fundCode;
        return new FundData.Dataset(
                code, "晨曦灵活配置混合 (DEMO)", "混合型",
                navs, bench, 12.0, 0.02,
                new Provenance(name(), SourceType.MARKET, asOfEpochMs, "synthetic NAV", 0.7),
                List.of());
    }
}
