package com.bank.financial.research.data;

/**
 * Pluggable source of bond terms for a fixed-income report. A stub gives a
 * deterministic offline issue; a real implementation would pull quoted price +
 * indicative terms from a bond data vendor. Implementations bound their own IO
 * and throw {@link DataUnavailableException} on failure (the
 * engine degrades transparently).
 */
public interface BondDataSource {

    String name();

    BondData.Dataset load(String bondCode, long asOfEpochMs);
}
