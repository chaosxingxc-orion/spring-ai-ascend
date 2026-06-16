package com.bank.financial.research.data;

import java.util.List;

/**
 * The pluggable data-ingestion SPI. One implementation = one provider (a stub
 * fixture for offline runs, an HTTP gateway to a market-data vendor, an internal
 * data lake, …). The engine depends only on this interface, so a bank plugs in
 * its own licensed feeds without touching the orchestration.
 *
 * <p>Implementations MUST bound their own IO (timeouts) and SHOULD throw
 * {@link DataUnavailableException} for an unknown ticker or a tier they cannot
 * serve; the {@link DataIngestionService} converts a missing tier into a
 * transparent gap rather than a hard failure.
 */
public interface ResearchDataSource {

    /** Short source label used in provenance/observability. */
    String name();

    CompanyData.Fundamentals fundamentals(String ticker);

    CompanyData.Consensus consensus(String ticker);

    CompanyData.MarketSnapshot market(String ticker);

    CompanyData.PeerSet peers(String ticker);

    List<CompanyData.TextItem> transcriptHighlights(String ticker, int limit);

    List<CompanyData.TextItem> news(String ticker, int limit);

    List<CompanyData.MacroIndicator> macro(String ticker);

    /** Thrown when a source cannot serve a ticker or tier. */
    final class DataUnavailableException extends RuntimeException {
        public DataUnavailableException(String message) {
            super(message);
        }
    }
}
