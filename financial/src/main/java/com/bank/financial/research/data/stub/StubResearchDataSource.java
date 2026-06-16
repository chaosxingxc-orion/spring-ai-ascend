package com.bank.financial.research.data.stub;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.SourceType;
import java.util.List;

/**
 * Deterministic offline data source. Serves one fully-specified fictional
 * company ("DEMO" — 晨曦科技, a made-up name so nothing implies real coverage) plus
 * a seeded-but-plausible fallback for any other ticker, so the playground and the
 * tests run end-to-end with no network and byte-identical numbers every time.
 *
 * <p>All figures are in millions of CNY unless noted. The as-of timestamp is
 * injectable so freshness behaviour is testable.
 */
public final class StubResearchDataSource implements ResearchDataSource {

    private final long asOfEpochMs;

    /** Use a fixed, recent-ish as-of so default runs are "fresh". */
    public StubResearchDataSource(long asOfEpochMs) {
        this.asOfEpochMs = asOfEpochMs;
    }

    @Override
    public String name() {
        return "stub-fixture";
    }

    private Provenance prov(SourceType type, String ref, double confidence) {
        return new Provenance(name(), type, asOfEpochMs, ref, confidence);
    }

    private int seed(String ticker) {
        // Stable per-ticker variation so non-DEMO tickers differ but are reproducible.
        int h = Math.abs(ticker == null ? 0 : ticker.toUpperCase().hashCode());
        return h % 20; // 0..19
    }

    @Override
    public CompanyData.Fundamentals fundamentals(String ticker) {
        if (isDemo(ticker)) {
            return new CompanyData.Fundamentals(
                    "DEMO", "晨曦科技 (Chenxi Technology)", "CNY",
                    50000, 12000, 3.20, 8000,
                    6000, 500, 2000,
                    0.45, 0.15,
                    List.of(38000.0, 42000.0, 45500.0, 50000.0),
                    prov(SourceType.FILING, "10-K FY24", 0.95));
        }
        int s = seed(ticker);
        double rev = 30000 + s * 1500;
        return new CompanyData.Fundamentals(
                ticker.toUpperCase(), ticker.toUpperCase() + " Corp", "CNY",
                rev, rev * 0.24, 2.0 + s * 0.1, rev * 0.16,
                4000 + s * 200, 300, 1500 + s * 50,
                0.40, 0.15,
                List.of(rev * 0.78, rev * 0.86, rev * 0.93, rev),
                prov(SourceType.FILING, "10-K (synthetic)", 0.7));
    }

    @Override
    public CompanyData.Consensus consensus(String ticker) {
        if (isDemo(ticker)) {
            return new CompanyData.Consensus(
                    "DEMO", 3.35, 0.18, 52000, 78.0, 18, 7, 2,
                    prov(SourceType.CONSENSUS, "I/B/E/S (synthetic)", 0.85));
        }
        int s = seed(ticker);
        return new CompanyData.Consensus(
                ticker.toUpperCase(), 2.1 + s * 0.1, 0.15 + s * 0.01, (30000 + s * 1500) * 1.04,
                40.0 + s * 2, 10 + s % 8, 5, 1 + s % 3,
                prov(SourceType.CONSENSUS, "I/B/E/S (synthetic)", 0.7));
    }

    @Override
    public CompanyData.MarketSnapshot market(String ticker) {
        if (isDemo(ticker)) {
            return new CompanyData.MarketSnapshot(
                    "DEMO", 64.50, 129000, 48.0, 82.0,
                    prov(SourceType.MARKET, "exchange feed", 0.99));
        }
        int s = seed(ticker);
        double price = 35.0 + s * 2.5;
        return new CompanyData.MarketSnapshot(
                ticker.toUpperCase(), price, price * (1500 + s * 50), price * 0.75, price * 1.3,
                prov(SourceType.MARKET, "exchange feed", 0.95));
    }

    @Override
    public CompanyData.PeerSet peers(String ticker) {
        if (isDemo(ticker)) {
            return new CompanyData.PeerSet(
                    "DEMO", List.of("PEER-A", "PEER-B", "PEER-C"),
                    9.5, 2.6, 22.0,
                    prov(SourceType.CONSENSUS, "peer median", 0.8));
        }
        int s = seed(ticker);
        return new CompanyData.PeerSet(
                ticker.toUpperCase(), List.of("PEER-A", "PEER-B"),
                7.0 + s * 0.2, 1.8 + s * 0.05, 15.0 + s * 0.5,
                prov(SourceType.CONSENSUS, "peer median (synthetic)", 0.7));
    }

    @Override
    public List<CompanyData.TextItem> transcriptHighlights(String ticker, int limit) {
        Provenance p = prov(SourceType.TRANSCRIPT, "Q4 FY24 earnings call", 0.9);
        return cap(List.of(
                new CompanyData.TextItem("管理层指引",
                        "管理层将下一财年收入增速指引上调至 8%-10%,主因新产品线放量与海外渠道扩张。", 0.6, p),
                new CompanyData.TextItem("毛利率",
                        "受供应链成本回落影响,毛利率环比改善约 120bps;管理层预计趋势可持续。", 0.4, p),
                new CompanyData.TextItem("资本开支",
                        "全年资本开支指引维持不变;研发投入占收入比例小幅提升。", 0.1, p)),
                limit);
    }

    @Override
    public List<CompanyData.TextItem> news(String ticker, int limit) {
        long t = asOfEpochMs;
        return cap(List.of(
                new CompanyData.TextItem("行业需求",
                        "第三方数据显示该公司所在细分行业季度出货量同比增长 12%,优于市场预期。",
                        0.7, new Provenance("行业资讯", SourceType.NEWS, t, "industry-tracker", 0.7)),
                new CompanyData.TextItem("原材料价格",
                        "关键原材料现货价格近月上涨约 6%,可能对下季度毛利率形成压力。",
                        -0.5, new Provenance("大宗商品资讯", SourceType.NEWS, t, "commodity-wire", 0.75)),
                new CompanyData.TextItem("汇率",
                        "本币兑美元升值约 3%,公司海外收入占比约 30%,折算后承压。",
                        -0.3, new Provenance("外汇资讯", SourceType.NEWS, t, "fx-wire", 0.8))),
                limit);
    }

    @Override
    public List<CompanyData.MacroIndicator> macro(String ticker) {
        long t = asOfEpochMs;
        return List.of(
                new CompanyData.MacroIndicator("GDP 同比", 5.0, "%",
                        new Provenance("统计局", SourceType.MACRO, t, "quarterly", 0.9)),
                new CompanyData.MacroIndicator("一年期 LPR", 3.1, "%",
                        new Provenance("央行", SourceType.MACRO, t, "monthly", 0.95)),
                new CompanyData.MacroIndicator("行业景气指数", 52.3, "PMI",
                        new Provenance("行业协会", SourceType.MACRO, t, "monthly", 0.7)));
    }

    private static boolean isDemo(String ticker) {
        return ticker != null && ticker.trim().equalsIgnoreCase("DEMO");
    }

    private static <T> List<T> cap(List<T> list, int limit) {
        return limit >= list.size() ? list : List.copyOf(list.subList(0, Math.max(0, limit)));
    }
}
