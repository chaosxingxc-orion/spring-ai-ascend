package com.bank.financial.research.data;

/**
 * Pluggable source of thematic inputs (macro factors + sub-sector exposures) for
 * a sector-strategy report. A stub encodes a fixed scenario for offline runs; a
 * production implementation would assemble factors from live macro/market/news
 * feeds (and could be fed real-time events). The engine depends only on this
 * interface.
 */
public interface ThematicDataSource {

    String name();

    /** Assemble the factors + sub-sectors for {@code theme} as of {@code asOfEpochMs}. */
    ThematicData.Dataset load(String theme, long asOfEpochMs);
}
