package com.bank.financial.research.data;

import java.util.Set;

/**
 * Pluggable source of macro & policy indicators. A stub encodes a fixed snapshot
 * for offline runs; a production implementation assembles readings from live
 * statistical / central-bank / overseas feeds. The engine depends only on this
 * interface. Implementations throw {@link DataUnavailableException} on failure so
 * the ingestion path degrades gracefully.
 */
public interface MacroDataSource {

    String name();

    /**
     * Assemble the requested macro domains for {@code region} as of {@code asOf}.
     * An empty {@code domains} set means "all available".
     */
    MacroData.Dataset load(String region, Set<MacroData.Domain> domains, long asOfEpochMs);
}
