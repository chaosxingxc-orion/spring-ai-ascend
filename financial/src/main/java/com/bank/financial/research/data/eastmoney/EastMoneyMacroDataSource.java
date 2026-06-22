package com.bank.financial.research.data.eastmoney;

import com.bank.financial.research.data.DataUnavailableException;
import com.bank.financial.research.data.MacroData;
import com.bank.financial.research.data.MacroDataSource;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Free, token-less China macro source backed by East Money's datacenter economic
 * series (validated live):
 * <ul>
 *   <li>GDP — {@code RPT_ECONOMY_GDP} (latest quarter YoY, {@code SUM_SAME});</li>
 *   <li>CPI — {@code RPT_ECONOMY_CPI} ({@code NATIONAL_SAME} YoY, {@code NATIONAL_SEQUENTIAL} MoM);</li>
 *   <li>PMI — {@code RPT_ECONOMY_PMI} (manufacturing {@code MAKE_INDEX});</li>
 *   <li>M2 — {@code RPT_ECONOMY_CURRENCY_SUPPLY} ({@code BASIC_CURRENCY_SAME} YoY).</li>
 * </ul>
 *
 * <p>Each indicator is fetched independently and fails soft: a tier that errors is
 * skipped with a freshness warning rather than failing the whole report. Overseas
 * (FOMC/US) and policy/regulatory text are not covered here — additional report
 * names plug in trivially. The HttpClient routes through the JVM proxy
 * ({@code -Dhttps.proxyHost}), matching the other East Money sources.
 */
public final class EastMoneyMacroDataSource implements MacroDataSource {

    private static final String BASE =
            "https://datacenter-web.eastmoney.com/api/data/v1/get?columns=ALL&pageNumber=1&pageSize=1"
            + "&sortColumns=REPORT_DATE&sortTypes=-1&reportName=";

    private final HttpClient http;
    private final Duration requestTimeout;
    private final long asOfEpochMs;
    private final ObjectMapper json = new ObjectMapper();

    public EastMoneyMacroDataSource(long asOfEpochMs) {
        this(asOfEpochMs, Duration.ofSeconds(3), Duration.ofSeconds(8));
    }

    public EastMoneyMacroDataSource(long asOfEpochMs, Duration connectTimeout, Duration requestTimeout) {
        this.asOfEpochMs = asOfEpochMs;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .proxy(ProxySelector.getDefault()).build();
    }

    @Override
    public String name() {
        return "eastmoney-macro";
    }

    @Override
    public MacroData.Dataset load(String region, Set<MacroData.Domain> domains, long asOf) {
        List<MacroData.Indicator> out = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean all = domains == null || domains.isEmpty();

        if (all || domains.contains(MacroData.Domain.GROWTH)) {
            tier(out, warnings, MacroData.Domain.GROWTH, "GDP", () -> {
                JsonNode r = fetchLatest("RPT_ECONOMY_GDP");
                return new MacroData.Indicator("gdp", "GDP 当季同比", MacroData.Domain.GROWTH,
                        text(r, "TIME"), num(r, "SUM_SAME"), "%", num(r, "SUM_SAME"), Double.NaN,
                        "国内生产总值当季同比", prov("国家统计局 / 东方财富"));
            });
        }
        if (all || domains.contains(MacroData.Domain.INFLATION)) {
            tier(out, warnings, MacroData.Domain.INFLATION, "CPI", () -> {
                JsonNode r = fetchLatest("RPT_ECONOMY_CPI");
                return new MacroData.Indicator("cpi", "CPI 同比", MacroData.Domain.INFLATION,
                        text(r, "TIME"), num(r, "NATIONAL_SAME"), "%", num(r, "NATIONAL_SAME"),
                        num(r, "NATIONAL_SEQUENTIAL"), "居民消费价格同比", prov("国家统计局 / 东方财富"));
            });
        }
        if (all || domains.contains(MacroData.Domain.ACTIVITY)) {
            tier(out, warnings, MacroData.Domain.ACTIVITY, "PMI", () -> {
                JsonNode r = fetchLatest("RPT_ECONOMY_PMI");
                return new MacroData.Indicator("pmi", "制造业 PMI", MacroData.Domain.ACTIVITY,
                        text(r, "TIME"), num(r, "MAKE_INDEX"), "", Double.NaN, Double.NaN,
                        "制造业采购经理指数(>50 扩张)", prov("国家统计局 / 东方财富"));
            });
        }
        if (all || domains.contains(MacroData.Domain.MONETARY)) {
            tier(out, warnings, MacroData.Domain.MONETARY, "M2", () -> {
                JsonNode r = fetchLatest("RPT_ECONOMY_CURRENCY_SUPPLY");
                return new MacroData.Indicator("m2", "M2 同比", MacroData.Domain.MONETARY,
                        text(r, "TIME"), num(r, "BASIC_CURRENCY_SAME"), "%", num(r, "BASIC_CURRENCY_SAME"),
                        Double.NaN, "广义货币供应同比", prov("中国人民银行 / 东方财富"));
            });
        }
        if (out.isEmpty()) {
            throw new DataUnavailableException("无可用宏观指标(全部抓取失败)");
        }
        return new MacroData.Dataset(region == null || region.isBlank() ? "中国" : region,
                asOf, out, warnings);
    }

    private interface IndicatorFetch {
        MacroData.Indicator get() throws Exception;
    }

    /** Fetch one indicator, fail-soft: on error record a warning and skip it. */
    private void tier(List<MacroData.Indicator> out, List<String> warnings,
            MacroData.Domain domain, String label, IndicatorFetch fetch) {
        try {
            MacroData.Indicator ind = fetch.get();
            if (Double.isNaN(ind.value())) {
                warnings.add(label + " 返回空值,已跳过");
            } else {
                out.add(ind);
            }
        } catch (Exception e) {
            warnings.add(label + " 抓取失败(" + e.getClass().getSimpleName() + "),已跳过");
        }
    }

    private JsonNode fetchLatest(String reportName) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + reportName))
                .header("User-Agent", "Mozilla/5.0 (research-report-engine)")
                .header("Referer", "https://data.eastmoney.com/")
                .timeout(requestTimeout).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new DataUnavailableException(reportName + " HTTP " + resp.statusCode());
        }
        JsonNode root = json.readTree(resp.body());
        JsonNode data = root.path("result").path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new DataUnavailableException(reportName + " 空数据");
        }
        return data.get(0);
    }

    private static double num(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? Double.NaN : n.asDouble();
    }

    private static String text(JsonNode row, String field) {
        JsonNode n = row.get(field);
        return (n == null || n.isNull()) ? "" : n.asText();
    }

    private Provenance prov(String source) {
        return new Provenance(source, SourceType.MACRO, asOfEpochMs, "datacenter", 0.85);
    }
}
