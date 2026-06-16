package com.bank.financial.research.data;

/**
 * The four-tier research data stack (plus macro), mirroring how a sell-side desk
 * sources inputs: detailed/consensus estimates, company filings, earnings-call
 * transcripts, and real-time market data. Every {@link Provenance} carries one
 * so the compliance agent can attribute each figure and the critic can reason
 * about source quality.
 */
public enum SourceType {
    /** Analyst estimates / consensus (I/B/E/S-style). */
    CONSENSUS,
    /** Company filings & financial statements (10-K/10-Q/8-K). */
    FILING,
    /** Earnings-call transcripts (qualitative signal). */
    TRANSCRIPT,
    /** Real-time market data (price, market cap). */
    MARKET,
    /** Real-time news / external information. */
    NEWS,
    /** Macro / sector indicators. */
    MACRO
}
