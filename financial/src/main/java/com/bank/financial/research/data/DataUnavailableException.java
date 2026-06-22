package com.bank.financial.research.data;

/**
 * Thrown by a data source when a tier or instrument is unavailable (unknown code,
 * a tier the source cannot serve, or a bounded transport/parse failure). Data
 * sources wrap raw IO/parse errors in this type so callers degrade gracefully
 * rather than leaking a stack trace. Shared across the fund, bond and (any future)
 * data SPIs.
 */
public final class DataUnavailableException extends RuntimeException {
    public DataUnavailableException(String message) {
        super(message);
    }
}
