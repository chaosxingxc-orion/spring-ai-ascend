package com.bank.financial.research.data.eastmoney;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Free, token-less A-share data source — the zero-setup "real data" path
 * (Tushare's financial endpoints are points-gated / effectively paid). It draws
 * on two public, free endpoints, validated against live responses:
 * <ul>
 *   <li>Sina realtime quote ({@code hq.sinajs.cn}) — the live price;</li>
 *   <li>East Money datacenter F10 main indicators — revenue, EPS, net profit and
 *       gross margin per report date (annual history). Diluted shares are derived
 *       as {@code netProfit / EPS}; market cap as {@code price × shares}.</li>
 * </ul>
 *
 * <p><b>Honest limits.</b> Unofficial public endpoints: no SLA, rate-limited,
 * shapes can change. East Money/Sina expose no sell-side consensus or peer
 * multiples here (those throw → transparent gaps), and EBITDA / FCF / net debt
 * are documented proxies (EBITDA≈revenue×grossMargin, FCF≈revenue×0.12,
 * netDebt=0). For institutional accuracy use a licensed feed (Wind / 朝阳永续) via
 * a gateway. Ticker formats: {@code 600519}, {@code 600519.SH}, {@code sh600519}.
 */
public final class EastMoneyResearchDataSource implements ResearchDataSource {

    private static final String UA = "Mozilla/5.0 (research-report-engine)";
    private final HttpClient http;
    private final Duration requestTimeout;
    private final long asOfEpochMs;
    private final ObjectMapper json = new ObjectMapper();

    public EastMoneyResearchDataSource(long asOfEpochMs) {
        this(asOfEpochMs, Duration.ofSeconds(3), Duration.ofSeconds(8));
    }

    public EastMoneyResearchDataSource(long asOfEpochMs, Duration connectTimeout, Duration requestTimeout) {
        this.asOfEpochMs = asOfEpochMs;
        this.requestTimeout = requestTimeout;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .proxy(ProxySelector.getDefault()).build();
    }

    @Override
    public String name() {
        return "eastmoney";
    }

    private Provenance prov(SourceType type, String ref, double confidence) {
        return new Provenance(name(), type, asOfEpochMs, ref, confidence);
    }

    // ── ticker → exchange-qualified ids (pure, unit-tested) ─────────────────────

    public record Symbol(String code, String exchange, String secucode, String sinaCode) {
    }

    public static Symbol resolve(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            throw new ResearchDataSource.DataUnavailableException("empty ticker");
        }
        String t = ticker.trim().toUpperCase();
        String code;
        String ex;
        if (t.contains(".")) {
            int dot = t.indexOf('.');
            code = t.substring(0, dot);
            ex = t.substring(dot + 1);
        } else if (t.startsWith("SH") || t.startsWith("SZ") || t.startsWith("BJ")) {
            ex = t.substring(0, 2);
            code = t.substring(2);
        } else {
            code = t;
            ex = inferExchange(code);
        }
        if (!code.matches("\\d{6}")) {
            throw new ResearchDataSource.DataUnavailableException("not an A-share code: " + ticker);
        }
        return new Symbol(code, ex, code + "." + ex, ex.toLowerCase() + code);
    }

    private static String inferExchange(String code) {
        char c = code.charAt(0);
        if (c == '6') {
            return "SH";
        }
        if (c == '8' || c == '4') {
            return "BJ";
        }
        return "SZ"; // 0 / 3 ...
    }

    // ── live price (Sina) ───────────────────────────────────────────────────────

    private double livePrice(Symbol s) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://hq.sinajs.cn/list=" + s.sinaCode()))
                    .timeout(requestTimeout)
                    .header("User-Agent", UA)
                    .header("Referer", "https://finance.sina.com.cn")
                    .GET().build();
            // Body is GBK, but the numeric fields are ASCII — ISO-8859-1 keeps bytes intact.
            HttpResponse<String> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.ISO_8859_1));
            if (resp.statusCode() != 200) {
                throw new ResearchDataSource.DataUnavailableException("sina HTTP " + resp.statusCode());
            }
            int q1 = resp.body().indexOf('"');
            int q2 = resp.body().lastIndexOf('"');
            if (q1 < 0 || q2 <= q1 + 1) {
                throw new ResearchDataSource.DataUnavailableException("sina empty quote for " + s.sinaCode());
            }
            String[] f = resp.body().substring(q1 + 1, q2).split(",");
            double price = f.length > 3 ? parse(f[3]) : 0;       // current
            double prevClose = f.length > 2 ? parse(f[2]) : 0;   // fallback if halted
            double p = price > 0 ? price : prevClose;
            if (p <= 0) {
                throw new ResearchDataSource.DataUnavailableException("no live price for " + s.sinaCode());
            }
            return p;
        } catch (ResearchDataSource.DataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ResearchDataSource.DataUnavailableException("sina fetch error: " + e.getClass().getSimpleName());
        }
    }

    // ── financials (East Money datacenter F10 main indicators) ──────────────────

    private record Annual(String date, double revenue, double eps, double netProfit, double grossMarginPct) {
    }

    private List<Annual> annuals(Symbol s) {
        String url = "https://datacenter.eastmoney.com/securities/api/data/v1/get"
                + "?reportName=RPT_F10_FINANCE_MAINFINADATA"
                + "&columns=SECURITY_NAME_ABBR,REPORT_DATE,TOTALOPERATEREVE,PARENTNETPROFIT,EPSJB,XSMLL"
                + "&filter=(SECUCODE%3D%22" + s.secucode() + "%22)"
                + "&pageNumber=1&pageSize=24&sortColumns=REPORT_DATE&sortTypes=-1&source=HSF10&client=PC";
        JsonNode rows;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url)).timeout(requestTimeout)
                    .header("User-Agent", UA).header("Accept", "application/json").GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new ResearchDataSource.DataUnavailableException("datacenter HTTP " + resp.statusCode());
            }
            rows = json.readTree(resp.body()).path("result").path("data");
        } catch (ResearchDataSource.DataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new ResearchDataSource.DataUnavailableException("datacenter error: " + e.getClass().getSimpleName());
        }
        List<Annual> out = new ArrayList<>(); // newest-first
        for (JsonNode r : rows) {
            String date = r.path("REPORT_DATE").asText("");
            if (date.contains("-12-31")) { // annual reports only
                out.add(new Annual(date.substring(0, 10),
                        r.path("TOTALOPERATEREVE").asDouble(0), r.path("EPSJB").asDouble(0),
                        r.path("PARENTNETPROFIT").asDouble(0), r.path("XSMLL").asDouble(0)));
            }
        }
        if (out.isEmpty()) {
            throw new ResearchDataSource.DataUnavailableException("no annual financials for " + s.secucode());
        }
        return out;
    }

    @Override
    public CompanyData.Fundamentals fundamentals(String ticker) {
        Symbol s = resolve(ticker);
        List<Annual> annual = annuals(s);
        Annual latest = annual.get(0);
        if (latest.revenue() <= 0 || latest.eps() == 0) {
            throw new ResearchDataSource.DataUnavailableException("incomplete financials for " + s.secucode());
        }
        double dilutedShares = latest.netProfit() / latest.eps();  // derived (net profit / EPS)
        double grossMargin = latest.grossMarginPct() > 0 ? latest.grossMarginPct() / 100.0 : 0.30;
        double ebitda = latest.revenue() * grossMargin;            // gross-profit proxy
        double fcfBase = latest.revenue() * 0.12;                  // documented proxy

        List<Double> revenueHistory = new ArrayList<>();           // oldest-first, up to 4y
        int take = Math.min(4, annual.size());
        for (int i = take - 1; i >= 0; i--) {
            revenueHistory.add(annual.get(i).revenue());
        }
        return new CompanyData.Fundamentals(
                s.secucode(), s.secucode(), "CNY",
                latest.revenue(), ebitda, latest.eps(), fcfBase, 0, 0, dilutedShares,
                grossMargin, 0.15, revenueHistory,
                prov(SourceType.FILING, "东方财富 datacenter (F10 主要指标)", 0.75));
    }

    @Override
    public CompanyData.MarketSnapshot market(String ticker) {
        Symbol s = resolve(ticker);
        double price = livePrice(s);
        List<Annual> annual = annuals(s);
        Annual latest = annual.get(0);
        double shares = (latest.eps() != 0) ? latest.netProfit() / latest.eps() : 0;
        double marketCap = shares > 0 ? price * shares : 0;
        return new CompanyData.MarketSnapshot(
                s.secucode(), price, marketCap, price * 0.75, price * 1.30,
                prov(SourceType.MARKET, "新浪行情 + 东方财富", 0.85));
    }

    @Override
    public CompanyData.Consensus consensus(String ticker) {
        throw new ResearchDataSource.DataUnavailableException("免费接口无卖方一致预期");
    }

    @Override
    public CompanyData.PeerSet peers(String ticker) {
        throw new ResearchDataSource.DataUnavailableException("免费接口无可比公司倍数");
    }

    @Override
    public List<CompanyData.TextItem> transcriptHighlights(String ticker, int limit) {
        return List.of();
    }

    @Override
    public List<CompanyData.TextItem> news(String ticker, int limit) {
        return List.of();
    }

    @Override
    public List<CompanyData.MacroIndicator> macro(String ticker) {
        return List.of();
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
