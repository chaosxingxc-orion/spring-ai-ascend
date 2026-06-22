package com.bank.financial.research.data.eastmoney;

import com.bank.financial.research.data.FundData;
import com.bank.financial.research.data.FundDataSource;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.DataUnavailableException;
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
import java.util.Collections;
import java.util.List;

/**
 * Free, token-less fund NAV source backed by East Money 天天基金 public endpoints
 * (validated live):
 * <ul>
 *   <li>NAV history — {@code api.fund.eastmoney.com/f10/lsjz} (Referer-gated):
 *       cumulative NAV ({@code LJJZ}) per date;</li>
 *   <li>fund name — {@code fundgz.1234567.com.cn/js/<code>.js} (JSONP).</li>
 * </ul>
 *
 * <p>Honest limits: unofficial, rate-limited; no free benchmark series here
 * (beta/alpha come back 0). Daily NAV ⇒ periodsPerYear≈244, risk-free 2%.
 */
public final class EastMoneyFundDataSource implements FundDataSource {

    private static final String UA = "Mozilla/5.0 (research-report-engine)";
    private final HttpClient http;
    private final Duration requestTimeout;
    private final long asOfEpochMs;
    private final int pageSize;
    private final ObjectMapper json = new ObjectMapper();

    public EastMoneyFundDataSource(long asOfEpochMs) {
        this(asOfEpochMs, 244, Duration.ofSeconds(3), Duration.ofSeconds(8));
    }

    public EastMoneyFundDataSource(long asOfEpochMs, int pageSize, Duration connectTimeout, Duration requestTimeout) {
        this.asOfEpochMs = asOfEpochMs;
        this.pageSize = pageSize;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .proxy(ProxySelector.getDefault()).build();
    }

    @Override
    public String name() {
        return "eastmoney-fund";
    }

    @Override
    public FundData.Dataset load(String fundCode, long asOf) {
        String code = fundCode == null ? "" : fundCode.trim();
        if (!code.matches("\\d{6}")) {
            throw new DataUnavailableException("not a fund code: " + fundCode);
        }
        List<Double> navs = navHistory(code);
        if (navs.size() < 2) {
            throw new DataUnavailableException("insufficient NAV history for " + code);
        }
        String name = fundName(code, "基金 " + code);
        return new FundData.Dataset(
                code, name, typeOf(name), navs, List.of(), 244.0, 0.02,
                new Provenance(name(), SourceType.MARKET, asOfEpochMs, "天天基金 净值历史", 0.85),
                List.of());
    }

    // The lsjz endpoint caps results at 20 per page (a larger pageSize returns an
    // empty Data), so we paginate at 20/page up to the target point count.
    private static final int PAGE = 20;

    private List<Double> navHistory(String code) {
        int pages = Math.max(1, (pageSize + PAGE - 1) / PAGE);
        List<Double> navs = new ArrayList<>(); // accumulate newest-first across pages
        try {
            for (int page = 1; page <= pages; page++) {
                String url = "https://api.fund.eastmoney.com/f10/lsjz?fundCode=" + code
                        + "&pageIndex=" + page + "&pageSize=" + PAGE;
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url)).timeout(requestTimeout)
                        .header("User-Agent", UA).header("Referer", "https://fundf10.eastmoney.com/")
                        .header("Accept", "application/json").GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200) {
                    throw new DataUnavailableException("lsjz HTTP " + resp.statusCode());
                }
                JsonNode list = json.readTree(resp.body()).path("Data").path("LSJZList");
                if (!list.isArray() || list.isEmpty()) {
                    break; // past the last page (or no data)
                }
                int added = 0;
                for (JsonNode r : list) {
                    double v = parse(r.path("LJJZ").asText("")); // cumulative NAV
                    if (v > 0) {
                        navs.add(v);
                        added++;
                    }
                }
                if (added < PAGE) {
                    break; // last (partial) page reached
                }
            }
        } catch (DataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new DataUnavailableException("lsjz error: " + e.getClass().getSimpleName());
        }
        Collections.reverse(navs); // oldest-first
        return navs;
    }

    private String fundName(String code, String fallback) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://fundgz.1234567.com.cn/js/" + code + ".js"))
                    .timeout(requestTimeout).header("User-Agent", UA).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            String b = resp.body();
            int s = b.indexOf('{');
            int e = b.lastIndexOf('}');
            if (s >= 0 && e > s) {
                return json.readTree(b.substring(s, e + 1)).path("name").asText(fallback);
            }
        } catch (Exception ignored) {
            // name is best-effort
        }
        return fallback;
    }

    private static String typeOf(String name) {
        if (name.contains("债")) {
            return "债券型";
        }
        if (name.contains("指数") || name.contains("ETF")) {
            return "指数型";
        }
        if (name.contains("股票")) {
            return "股票型";
        }
        if (name.contains("货币")) {
            return "货币型";
        }
        return "混合型";
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
