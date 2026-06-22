package com.bank.financial.research.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Retries transient model failures with exponential backoff + jitter. Live LLM
 * endpoints intermittently refuse connections or rate-limit under rapid
 * sequential calls (a report fires many in a row); a bounded backoff lets the
 * endpoint recover instead of degrading the section on the first blip. Backoff is
 * exponential with a cap and jitter so retries don't synchronise. Exhausting the
 * attempts rethrows, so the sub-agent's degrade path still applies as a last
 * resort.
 */
public final class RetryReportModel implements ReportModel {

    private final ReportModel delegate;
    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long capBackoffMs;

    public RetryReportModel(ReportModel delegate, int maxAttempts, long baseBackoffMs) {
        this(delegate, maxAttempts, baseBackoffMs, 8000L);
    }

    public RetryReportModel(ReportModel delegate, int maxAttempts, long baseBackoffMs, long capBackoffMs) {
        this.delegate = delegate;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoffMs = baseBackoffMs;
        this.capBackoffMs = capBackoffMs;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String generate(ModelTask task) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return delegate.generate(task);
            } catch (RuntimeException e) {
                last = e;
                // A NonRetryable failure (timeout, pool saturation) can't be fixed by
                // retrying — backing off would only multiply the wall-clock. Rethrow now.
                if (e instanceof NonRetryable || attempt == maxAttempts) {
                    break;
                }
                sleep(backoffMs(attempt));
            }
        }
        throw last;
    }

    /** Exponential backoff (base·2^(n-1)) capped, with full jitter. */
    long backoffMs(int attempt) {
        long exp = baseBackoffMs * (1L << (attempt - 1));
        long capped = Math.min(exp, capBackoffMs);
        return ThreadLocalRandom.current().nextLong(0, capped + 1);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("retry interrupted", e);
        }
    }
}
