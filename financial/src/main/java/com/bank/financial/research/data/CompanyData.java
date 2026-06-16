package com.bank.financial.research.data;

import java.util.List;

/**
 * Normalised company dataset assembled from the four-tier stack. Each tier is a
 * small immutable record carrying its own {@link Provenance}; a tier may be
 * {@code null} when its source was unavailable (the engine degrades gracefully
 * and records the gap rather than fabricating data).
 */
public final class CompanyData {

    private CompanyData() {
    }

    /**
     * Fundamentals from filings. {@code fcfBase} is the latest free cash flow; the
     * quant agent projects it forward with a growth assumption (assumptions are
     * not data and are not sourced here).
     */
    public record Fundamentals(
            String ticker, String company, String currency,
            double revenue, double ebitda, double eps, double fcfBase,
            double netDebt, double minorityInterest, double dilutedShares,
            double incrementalMargin, double taxRate,
            List<Double> revenueHistory,
            Provenance provenance) {

        public Fundamentals {
            revenueHistory = revenueHistory == null ? List.of() : List.copyOf(revenueHistory);
        }
    }

    /** Consensus / detailed estimates (I/B/E/S-style). */
    public record Consensus(
            String ticker, double epsMean, double epsStdev, double revenueMean,
            double priceTargetMean, int buys, int holds, int sells,
            Provenance provenance) {

        public int totalRatings() {
            return buys + holds + sells;
        }
    }

    /** Real-time market snapshot. */
    public record MarketSnapshot(
            String ticker, double price, double marketCap, double low52w, double high52w,
            Provenance provenance) {
    }

    /** Comparable peer set with (typically median) multiples. */
    public record PeerSet(
            String ticker, List<String> peers,
            double evEbitda, double evSales, double priceEarnings,
            Provenance provenance) {

        public PeerSet {
            peers = List.copyOf(peers);
        }
    }

    /** A qualitative item (transcript highlight or news), with a sentiment in [-1,1]. */
    public record TextItem(String title, String body, double sentiment, Provenance provenance) {
    }

    /** A macro / sector indicator. */
    public record MacroIndicator(String name, double value, String unit, Provenance provenance) {
    }

    /**
     * The full assembled dataset for a ticker. Any tier may be {@code null}.
     *
     * @param freshnessWarnings non-fatal staleness notes surfaced to the report
     */
    public record Dataset(
            String ticker, long asOfEpochMs,
            Fundamentals fundamentals, Consensus consensus, MarketSnapshot market, PeerSet peers,
            List<TextItem> transcriptHighlights, List<TextItem> news, List<MacroIndicator> macro,
            List<String> freshnessWarnings) {

        public Dataset {
            transcriptHighlights = transcriptHighlights == null ? List.of() : List.copyOf(transcriptHighlights);
            news = news == null ? List.of() : List.copyOf(news);
            macro = macro == null ? List.of() : List.copyOf(macro);
            freshnessWarnings = freshnessWarnings == null ? List.of() : List.copyOf(freshnessWarnings);
        }

        /** Tiers that are present, for the coverage/quality note in the report. */
        public boolean hasFundamentals() {
            return fundamentals != null;
        }

        public boolean hasConsensus() {
            return consensus != null;
        }

        public boolean hasMarket() {
            return market != null;
        }

        public boolean hasPeers() {
            return peers != null;
        }
    }
}
