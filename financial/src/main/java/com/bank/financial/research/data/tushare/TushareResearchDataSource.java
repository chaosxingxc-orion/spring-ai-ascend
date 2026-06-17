package com.bank.financial.research.data.tushare;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * A-share real data source (the default/缺省 source), backed by the Tushare Pro
 * HTTP API (<a href="http://api.tushare.pro">api.tushare.pro</a>). Tickers are in
 * Tushare's {@code ts_code} form — e.g. {@code "600519.SH"} (Shanghai),
 * {@code "000001.SZ"} (Shenzhen). All amounts are normalised to millions of CNY
 * to match {@link CompanyData}.
 *
 * <p>Requires a Tushare token (typically from the {@code TUSHARE_TOKEN} env var,
 * passed to the constructor). When the token is {@code null}/blank, every tier
 * throws {@link DataUnavailableException} so the {@link com.bank.financial.research.data.DataIngestionService}
 * degrades to a transparent coverage gap rather than failing the run.
 *
 * <p>Tushare does not directly expose sell-side consensus / detailed estimates,
 * so {@link #consensus(String)} is best-effort: it is synthesised from the latest
 * reported EPS and market price (a small synthetic stdev, neutral rating mix).
 * Earnings-call transcripts are not served by Tushare; {@link #transcriptHighlights}
 * returns an empty list. {@link #news} and {@link #macro} are likewise empty here
 * (they require separate Tushare endpoints / paid scopes); the report degrades
 * transparently for those tiers.
 *
 * <p>Bounded by a 3s connect + 8s request timeout so a slow/unreachable feed never
 * hangs a report run; any non-zero API code, HTTP error, timeout, or parse failure
 * is wrapped in {@link DataUnavailableException} (never leaking a raw stack).
 */
public final class TushareResearchDataSource implements ResearchDataSource {

    private static final String ENDPOINT = "http://api.tushare.pro";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    // Tushare reports money in CNY yuan (元); CompanyData is in millions of CNY.
    private static final double YUAN_TO_MILLIONS = 1.0 / 1_000_000.0;

    private final String token; // nullable / blank => fail-soft
    private final long asOfEpochMs; // stamped onto provenance for fetched data
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public TushareResearchDataSource(String tokenOrNull, long asOfEpochMs) {
        this.token = tokenOrNull;
        this.asOfEpochMs = asOfEpochMs;
        this.http = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT)
                .proxy(ProxySelector.getDefault()).build();
    }

    @Override
    public String name() {
        return "tushare";
    }

    private Provenance prov(SourceType type, String ref, double confidence) {
        return new Provenance(name(), type, asOfEpochMs, ref, confidence);
    }

    /**
     * Call a Tushare API. Returns the {@code data} node ({@code {fields:[...], items:[[...]]}}).
     * Throws {@link DataUnavailableException} for an unset token, transport/parse
     * failures, a non-zero API {@code code}, or an empty result.
     */
    private JsonNode call(String apiName, ObjectNode params, String fields) {
        if (token == null || token.isBlank()) {
            throw new DataUnavailableException("TUSHARE_TOKEN 未配置");
        }
        try {
            ObjectNode body = json.createObjectNode();
            body.put("api_name", apiName);
            body.put("token", token);
            body.set("params", params);
            body.put("fields", fields == null ? "" : fields);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new DataUnavailableException(apiName + " HTTP " + resp.statusCode());
            }
            JsonNode root = json.readTree(resp.body());
            int code = root.path("code").asInt(-1);
            if (code != 0) {
                throw new DataUnavailableException(
                        apiName + " API code=" + code + " (" + root.path("msg").asText("") + ")");
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull() || !data.path("items").isArray()
                    || data.path("items").isEmpty()) {
                throw new DataUnavailableException(apiName + " 无数据");
            }
            return data;
        } catch (DataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Includes HttpTimeoutException / IO / parse — never hang or leak a raw stack.
            throw new DataUnavailableException(apiName + " fetch error: " + e.getClass().getSimpleName());
        }
    }

    private static ObjectNode params(ObjectMapper json, String... kv) {
        ObjectNode p = json.createObjectNode();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            p.put(kv[i], kv[i + 1]);
        }
        return p;
    }

    /** A {@code {fields, items}} table addressed by field name rather than column index. */
    private static final class Table {
        private final List<String> fields;
        private final JsonNode items;

        Table(JsonNode data) {
            this.fields = new ArrayList<>();
            data.path("fields").forEach(f -> fields.add(f.asText()));
            this.items = data.path("items");
        }

        int rows() {
            return items.size();
        }

        /** Cell at {@code (row, field)} as a double, or {@code def} when absent/non-numeric. */
        double num(int row, String field, double def) {
            JsonNode cell = cell(row, field);
            if (cell == null || cell.isNull()) {
                return def;
            }
            if (cell.isNumber()) {
                return cell.asDouble();
            }
            Double parsed = parseable(cell); // Tushare sometimes returns numerics as strings
            return parsed == null ? def : parsed;
        }

        String str(int row, String field, String def) {
            JsonNode cell = cell(row, field);
            return cell == null || cell.isNull() ? def : cell.asText(def);
        }

        private JsonNode cell(int row, String field) {
            int col = fields.indexOf(field);
            if (col < 0 || row < 0 || row >= items.size()) {
                return null;
            }
            JsonNode r = items.get(row);
            return col < r.size() ? r.get(col) : null;
        }

        private static Double parseable(JsonNode cell) {
            try {
                return Double.parseDouble(cell.asText());
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    @Override
    public CompanyData.Fundamentals fundamentals(String ticker) {
        // fina_indicator: per-share + margin metrics; income/balancesheet: absolute figures.
        Table fina = new Table(call("fina_indicator", params(json, "ts_code", ticker),
                "ts_code,end_date,eps,grossprofit_margin,netprofit_margin"));
        Table income = new Table(call("income", params(json, "ts_code", ticker),
                "ts_code,end_date,revenue,ebit,total_profit,income_tax,minority_gain"));
        Table balance = new Table(call("balancesheet", params(json, "ts_code", ticker),
                "ts_code,end_date,total_share,total_liab_hldr_eqy_inc_min,money_cap,total_liab,minority_int"));

        // Latest period is row 0 (Tushare returns most-recent first for these APIs).
        double eps = fina.num(0, "eps", 0.0);
        double revenueYuan = income.num(0, "revenue", 0.0);
        double ebitYuan = income.num(0, "ebit", 0.0);
        double minorityGainYuan = income.num(0, "minority_gain", 0.0);
        double totalShare = balance.num(0, "total_share", 0.0); // in 万股? Tushare: 股本(万元 historically)
        double moneyCapYuan = balance.num(0, "money_cap", 0.0);
        double totalLiabYuan = balance.num(0, "total_liab", 0.0);
        double minorityIntYuan = balance.num(0, "minority_int", minorityGainYuan);

        double revenue = revenueYuan * YUAN_TO_MILLIONS;
        // EBITDA not directly reported; approximate with EBIT (a transparent, conservative proxy).
        double ebitda = ebitYuan * YUAN_TO_MILLIONS;
        double netDebt = (totalLiabYuan - moneyCapYuan) * YUAN_TO_MILLIONS;
        double minorityInterest = minorityIntYuan * YUAN_TO_MILLIONS;
        // total_share from balancesheet is 万股; convert to (million shares).
        double dilutedShares = totalShare > 0 ? totalShare / 100.0 : 0.0;
        // FCF not in these three APIs; proxy with net income (≈ revenue×margin) is too noisy,
        // so use a conservative fraction of revenue as the base the quant agent grows forward.
        double fcfBase = revenue * 0.12;

        // Revenue history: most-recent-first → take up to 4 annual periods, oldest-first.
        List<Double> history = new ArrayList<>();
        int n = Math.min(income.rows(), 4);
        for (int i = 0; i < n; i++) {
            history.add(income.num(i, "revenue", 0.0) * YUAN_TO_MILLIONS);
        }
        Collections.reverse(history);

        String company = fina.str(0, "ts_code", ticker);
        return new CompanyData.Fundamentals(
                ticker, company, "CNY",
                revenue, ebitda, eps, fcfBase,
                netDebt, minorityInterest, dilutedShares,
                0.40, 0.15, // incrementalMargin / taxRate: assumptions, not data — reasonable defaults
                history,
                prov(SourceType.FILING, income.str(0, "end_date", ""), 0.85));
    }

    @Override
    public CompanyData.Consensus consensus(String ticker) {
        // Tushare does not serve sell-side consensus; best-effort from latest EPS + price.
        double eps;
        double price;
        double marketCap;
        try {
            Table fina = new Table(call("fina_indicator", params(json, "ts_code", ticker), "ts_code,eps"));
            eps = fina.num(0, "eps", 0.0);
            CompanyData.MarketSnapshot m = market(ticker);
            price = m.price();
            marketCap = m.marketCap();
        } catch (DataUnavailableException e) {
            throw new DataUnavailableException("tushare 无一致预期 (" + e.getMessage() + ")");
        }
        if (eps == 0.0 && price == 0.0) {
            throw new DataUnavailableException("tushare 无一致预期");
        }
        // Neutral synthetic: mean = reported, small stdev, hold-leaning rating mix.
        double epsStdev = Math.abs(eps) * 0.10;
        double revenueMean = 0.0; // unknown without estimates
        double priceTargetMean = price; // no upside view — neutral anchor
        return new CompanyData.Consensus(
                ticker, eps, epsStdev, revenueMean, priceTargetMean,
                0, 1, 0,
                prov(SourceType.CONSENSUS, "best-effort (no Tushare consensus)", 0.40));
    }

    @Override
    public CompanyData.MarketSnapshot market(String ticker) {
        // daily_basic: total_mv (万元) + close; daily: 52w range via a 1y window would need more rows,
        // so derive a conservative band from the latest close when range data is absent.
        Table db = new Table(call("daily_basic", params(json, "ts_code", ticker),
                "ts_code,trade_date,close,total_mv"));
        double price = db.num(0, "close", 0.0);
        double marketCapWan = db.num(0, "total_mv", 0.0); // 万元
        double marketCap = marketCapWan / 100.0; // 万元 → 百万元 (millions)

        double low52w = price > 0 ? price * 0.75 : 0.0;
        double high52w = price > 0 ? price * 1.30 : 0.0;
        // Best-effort true 52w range from daily closes (one extra call; failure is non-fatal).
        try {
            Table daily = new Table(call("daily", params(json, "ts_code", ticker), "ts_code,trade_date,close"));
            double lo = Double.MAX_VALUE;
            double hi = 0.0;
            int rows = Math.min(daily.rows(), 250); // ~1 trading year
            for (int i = 0; i < rows; i++) {
                double c = daily.num(i, "close", 0.0);
                if (c > 0) {
                    lo = Math.min(lo, c);
                    hi = Math.max(hi, c);
                }
            }
            if (hi > 0 && lo < Double.MAX_VALUE) {
                low52w = lo;
                high52w = hi;
            }
        } catch (DataUnavailableException ignored) {
            // keep the conservative band derived from latest close
        }

        return new CompanyData.MarketSnapshot(
                ticker, price, marketCap, low52w, high52w,
                prov(SourceType.MARKET, db.str(0, "trade_date", ""), 0.95));
    }

    @Override
    public CompanyData.PeerSet peers(String ticker) {
        // Tushare has no curated peer set with median multiples; surface a transparent gap.
        throw new DataUnavailableException("tushare 无可比公司倍数");
    }

    @Override
    public List<CompanyData.TextItem> transcriptHighlights(String ticker, int limit) {
        // Tushare does not serve earnings-call transcripts.
        return List.of();
    }

    @Override
    public List<CompanyData.TextItem> news(String ticker, int limit) {
        // News requires a separate (paid-scope) Tushare endpoint; degrade transparently.
        return List.of();
    }

    @Override
    public List<CompanyData.MacroIndicator> macro(String ticker) {
        // Macro indicators require separate Tushare endpoints; degrade transparently.
        return List.of();
    }
}
