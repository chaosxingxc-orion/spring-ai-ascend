package com.bank.financial.research.data;

/**
 * Pluggable source of fund NAV data for a fund / FOF report. A stub gives a
 * deterministic offline series; a free implementation pulls public NAV history.
 * Implementations bound their own IO and throw {@link DataUnavailableException}
 * on failure (the engine degrades transparently).
 */
public interface FundDataSource {

    String name();

    FundData.Dataset load(String fundCode, long asOfEpochMs);
}
