package com.bank.financial.research.data.stub;

import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.data.MacroDataSource;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.SourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic offline macro source: a fixed, plausible snapshot of China macro
 * readings so the playground and tests run end-to-end with no network and
 * byte-identical numbers. Clearly synthetic (provenance = "宏观快照(离线)").
 */
public final class StubMacroDataSource implements MacroDataSource {

    private final long asOfEpochMs;

    public StubMacroDataSource(long asOfEpochMs) {
        this.asOfEpochMs = asOfEpochMs;
    }

    @Override
    public String name() {
        return "stub-macro";
    }

    private Provenance prov() {
        return new Provenance("宏观快照(离线)", SourceType.MACRO, asOfEpochMs, "synthetic", 0.6);
    }

    @Override
    public MacroData.Dataset load(String region, Set<MacroData.Domain> domains, long asOf) {
        List<MacroData.Indicator> all = new ArrayList<>();
        all.add(new MacroData.Indicator("gdp", "GDP 当季同比", MacroData.Domain.GROWTH,
                "最新季度", 5.0, "%", 5.0, Double.NaN, "增长围绕趋势", prov()));
        all.add(new MacroData.Indicator("cpi", "CPI 同比", MacroData.Domain.INFLATION,
                "最新月份", 0.8, "%", 0.8, -0.1, "物价低位,通胀压力小", prov()));
        all.add(new MacroData.Indicator("pmi", "制造业 PMI", MacroData.Domain.ACTIVITY,
                "最新月份", 49.8, "", Double.NaN, -0.2, "略低于荣枯线,景气偏弱", prov()));
        all.add(new MacroData.Indicator("m2", "M2 同比", MacroData.Domain.MONETARY,
                "最新月份", 7.5, "%", 7.5, 0.1, "货币投放略低于趋势", prov()));
        List<MacroData.Indicator> picked = all.stream()
                .filter(i -> domains == null || domains.isEmpty() || domains.contains(i.domain()))
                .toList();
        return new MacroData.Dataset(region == null || region.isBlank() ? "中国" : region,
                asOf, picked, List.of());
    }
}
