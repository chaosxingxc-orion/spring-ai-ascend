package com.bank.financial.research.model;

/**
 * Marks a model failure that a retry cannot fix, so {@link RetryReportModel}
 * rethrows it immediately instead of backing off and trying again. Two cases:
 * a hard timeout (the call already consumed its time budget — retrying it 2–3×
 * just multiplies the wall-clock past the run's budget), and pool saturation
 * (no capacity to run the call under timeout protection). Transient faults
 * (connection refused, rate-limit, 5xx) are <em>not</em> marked and stay
 * retryable.
 */
public interface NonRetryable {
}
