package com.bank.financial.research.data.stub;

import com.bank.financial.research.data.BondData;
import com.bank.financial.research.data.BondDataSource;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.SourceType;
import java.util.List;

/**
 * Deterministic offline bond: a fictional 5-year AA+ corporate credit issue with
 * fixed par, coupon, horizon and a slightly-below-par market price (so the solved
 * YTM sits above the coupon and carries a clear credit spread over the risk-free
 * benchmark). Byte-identical every run — used by tests and the offline demo.
 *
 * <p>Free real-time bond data is hard to source openly (unlike A-share equity/fund
 * NAV), so this is a transparent synthetic stub; a vendor-backed {@link BondDataSource}
 * would replace it without touching the engine.
 */
public final class StubBondDataSource implements BondDataSource {

    private final long asOfEpochMs;

    public StubBondDataSource(long asOfEpochMs) {
        this.asOfEpochMs = asOfEpochMs;
    }

    @Override
    public String name() {
        return "stub-bond";
    }

    @Override
    public BondData.Dataset load(String bondCode, long asOf) {
        String code = (bondCode == null || bondCode.isBlank()) ? "DEMOBOND" : bondCode;
        // Face 100, 4.5% annual coupon, 5 periods left, priced at 97.5 → YTM ≈ 5.08%;
        // benchmark (risk-free) 2.8% → credit spread ≈ 228 bps. Wide spread, ~4.5y
        // modified duration → stance EXTEND under the documented thresholds.
        return new BondData.Dataset(
                code, "晨曦实业 2031 到期信用债 (DEMO)", "晨曦实业集团有限公司 (DEMO)",
                100.0, 0.045, 5, 97.5, 0.028, "AA+",
                new Provenance(name(), SourceType.MARKET, asOf, "synthetic terms", 0.7),
                List.of());
    }
}
