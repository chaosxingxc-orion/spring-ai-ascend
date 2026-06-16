package com.bank.financial.research.data;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Assembles a normalised {@link CompanyData.Dataset} from a {@link ResearchDataSource},
 * applying freshness checks and degrading gracefully: if one tier's source call
 * fails or is unavailable, that tier becomes {@code null} and a warning is
 * recorded — the report is produced with a transparent coverage gap rather than
 * failing the whole run. This is the resilience seam for the (often flaky,
 * rate-limited) external feeds a real desk depends on.
 */
public final class DataIngestionService {

    private final ResearchDataSource source;
    private final FreshnessPolicy freshness;

    public DataIngestionService(ResearchDataSource source, FreshnessPolicy freshness) {
        this.source = source;
        this.freshness = freshness;
    }

    /**
     * Pull every tier for {@code ticker} as of {@code nowMs}. Each tier is fetched
     * defensively; failures are collected as warnings, not thrown.
     */
    public CompanyData.Dataset assemble(String ticker, int newsLimit, long nowMs) {
        List<String> warnings = new ArrayList<>();

        CompanyData.Fundamentals fundamentals = soft("fundamentals", warnings, () -> source.fundamentals(ticker));
        CompanyData.Consensus consensus = soft("consensus", warnings, () -> source.consensus(ticker));
        CompanyData.MarketSnapshot market = soft("market", warnings, () -> source.market(ticker));
        CompanyData.PeerSet peers = soft("peers", warnings, () -> source.peers(ticker));
        List<CompanyData.TextItem> transcripts =
                softList("transcripts", warnings, () -> source.transcriptHighlights(ticker, newsLimit));
        List<CompanyData.TextItem> news = softList("news", warnings, () -> source.news(ticker, newsLimit));
        List<CompanyData.MacroIndicator> macro = softList("macro", warnings, () -> source.macro(ticker));

        // Freshness: flag (don't drop) stale tiers so the report can disclose it.
        checkFresh("fundamentals", fundamentals == null ? null : fundamentals.provenance(), nowMs, warnings);
        checkFresh("consensus", consensus == null ? null : consensus.provenance(), nowMs, warnings);
        checkFresh("market", market == null ? null : market.provenance(), nowMs, warnings);
        checkFresh("peers", peers == null ? null : peers.provenance(), nowMs, warnings);

        return new CompanyData.Dataset(
                ticker, nowMs, fundamentals, consensus, market, peers,
                transcripts, news, macro, warnings);
    }

    private <T> T soft(String tier, List<String> warnings, Supplier<T> call) {
        try {
            return call.get();
        } catch (ResearchDataSource.DataUnavailableException e) {
            warnings.add(tier + " unavailable: " + e.getMessage());
            return null;
        } catch (RuntimeException e) {
            warnings.add(tier + " fetch failed (" + source.name() + "): " + e.getMessage());
            return null;
        }
    }

    private <T> List<T> softList(String tier, List<String> warnings, Supplier<List<T>> call) {
        List<T> v = soft(tier, warnings, call);
        return v == null ? List.of() : v;
    }

    private void checkFresh(String tier, Provenance p, long nowMs, List<String> warnings) {
        if (p != null && !freshness.isFresh(p, nowMs)) {
            long ageDays = p.ageMs(nowMs) / (24L * 60 * 60 * 1000);
            warnings.add(tier + " is stale (" + ageDays + "d old, source=" + p.source()
                    + ", policy=" + freshness.maxAgeMs() / (24L * 60 * 60 * 1000) + "d)");
        }
    }
}
