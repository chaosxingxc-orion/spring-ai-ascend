package com.bank.financial.research.data.http;

import com.bank.financial.research.data.CompanyData;
import com.bank.financial.research.data.Provenance;
import com.bank.financial.research.data.ResearchDataSource;
import com.bank.financial.research.data.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Production data-ingestion path: a thin HTTP client to a bank-hosted data
 * gateway that normalises licensed feeds (I/B/E/S, exchange, filings, news) to a
 * simple JSON contract. Bounded by connect + request timeouts so a slow feed
 * never hangs a report run; any non-200 / parse error becomes
 * {@link DataUnavailableException}, which the ingestion service turns into a
 * transparent coverage gap.
 *
 * <p>Expected endpoints (GET {base}/{tier}?ticker=XXX), JSON mirroring the
 * {@link CompanyData} records. This source is not exercised in offline tests
 * (the stub is); it exists so the seam to real feeds is concrete, not hand-wavy.
 */
public final class HttpResearchDataSource implements ResearchDataSource {

    private final String baseUrl;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final String authToken; // nullable
    private final long asOfEpochMs; // stamped onto provenance for fetched data
    private final ObjectMapper json = new ObjectMapper();

    public HttpResearchDataSource(String baseUrl, Duration connectTimeout, Duration requestTimeout,
            String authToken, long asOfEpochMs) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.requestTimeout = requestTimeout;
        this.authToken = authToken;
        this.asOfEpochMs = asOfEpochMs;
        this.http = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    @Override
    public String name() {
        return "http:" + baseUrl;
    }

    private JsonNode get(String tier, String ticker) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/" + tier + "?ticker=" + ticker))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .GET();
            if (authToken != null && !authToken.isBlank()) {
                b.header("Authorization", "Bearer " + authToken);
            }
            HttpResponse<String> resp = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new DataUnavailableException(tier + " HTTP " + resp.statusCode() + " for " + ticker);
            }
            return json.readTree(resp.body());
        } catch (DataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // Includes HttpTimeoutException / IO / parse — never let a feed hang or leak a raw stack.
            throw new DataUnavailableException(tier + " fetch error for " + ticker + ": " + e.getClass().getSimpleName());
        }
    }

    private Provenance prov(SourceType type, String ref, double confidence) {
        return new Provenance(name(), type, asOfEpochMs, ref, confidence);
    }

    @Override
    public CompanyData.Fundamentals fundamentals(String ticker) {
        JsonNode n = get("fundamentals", ticker);
        List<Double> history = new ArrayList<>();
        n.path("revenueHistory").forEach(v -> history.add(v.asDouble()));
        return new CompanyData.Fundamentals(
                ticker, n.path("company").asText(ticker), n.path("currency").asText("CNY"),
                n.path("revenue").asDouble(), n.path("ebitda").asDouble(), n.path("eps").asDouble(),
                n.path("fcfBase").asDouble(), n.path("netDebt").asDouble(),
                n.path("minorityInterest").asDouble(), n.path("dilutedShares").asDouble(),
                n.path("incrementalMargin").asDouble(0.4), n.path("taxRate").asDouble(0.15),
                history,
                prov(SourceType.FILING, n.path("reference").asText(""), n.path("confidence").asDouble(0.9)));
    }

    @Override
    public CompanyData.Consensus consensus(String ticker) {
        JsonNode n = get("consensus", ticker);
        return new CompanyData.Consensus(
                ticker, n.path("epsMean").asDouble(), n.path("epsStdev").asDouble(),
                n.path("revenueMean").asDouble(), n.path("priceTargetMean").asDouble(),
                n.path("buys").asInt(), n.path("holds").asInt(), n.path("sells").asInt(),
                prov(SourceType.CONSENSUS, n.path("reference").asText(""), n.path("confidence").asDouble(0.85)));
    }

    @Override
    public CompanyData.MarketSnapshot market(String ticker) {
        JsonNode n = get("market", ticker);
        return new CompanyData.MarketSnapshot(
                ticker, n.path("price").asDouble(), n.path("marketCap").asDouble(),
                n.path("low52w").asDouble(), n.path("high52w").asDouble(),
                prov(SourceType.MARKET, n.path("reference").asText(""), n.path("confidence").asDouble(0.99)));
    }

    @Override
    public CompanyData.PeerSet peers(String ticker) {
        JsonNode n = get("peers", ticker);
        List<String> peers = new ArrayList<>();
        n.path("peers").forEach(p -> peers.add(p.asText()));
        return new CompanyData.PeerSet(
                ticker, peers, n.path("evEbitda").asDouble(), n.path("evSales").asDouble(),
                n.path("priceEarnings").asDouble(),
                prov(SourceType.CONSENSUS, n.path("reference").asText(""), n.path("confidence").asDouble(0.8)));
    }

    @Override
    public List<CompanyData.TextItem> transcriptHighlights(String ticker, int limit) {
        return textItems("transcripts", ticker, SourceType.TRANSCRIPT, limit);
    }

    @Override
    public List<CompanyData.TextItem> news(String ticker, int limit) {
        return textItems("news", ticker, SourceType.NEWS, limit);
    }

    private List<CompanyData.TextItem> textItems(String tier, String ticker, SourceType type, int limit) {
        JsonNode arr = get(tier, ticker).path("items");
        List<CompanyData.TextItem> out = new ArrayList<>();
        for (JsonNode it : arr) {
            if (out.size() >= limit) {
                break;
            }
            out.add(new CompanyData.TextItem(
                    it.path("title").asText(""), it.path("body").asText(""), it.path("sentiment").asDouble(0),
                    prov(type, it.path("reference").asText(""), it.path("confidence").asDouble(0.7))));
        }
        return out;
    }

    @Override
    public List<CompanyData.MacroIndicator> macro(String ticker) {
        JsonNode arr = get("macro", ticker).path("items");
        List<CompanyData.MacroIndicator> out = new ArrayList<>();
        for (JsonNode it : arr) {
            out.add(new CompanyData.MacroIndicator(
                    it.path("name").asText(""), it.path("value").asDouble(), it.path("unit").asText(""),
                    prov(SourceType.MACRO, it.path("reference").asText(""), it.path("confidence").asDouble(0.8))));
        }
        return out;
    }
}
