package com.huawei.ascend.runtime.engine.a2a;

import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

/**
 * Stable, client-facing classification for an uncaught runtime failure. The A2A client
 * branches on the {@code name()} (a small closed set) and on {@link #retryable()} rather than
 * parsing a free-text message. Adapter-supplied error codes are passed through unchanged and are
 * NOT routed through this enum; this only classifies exceptions that escape to the executor.
 */
public enum RuntimeErrorCode {

    /** Caller sent something the agent cannot act on. Not retryable as-is. */
    INVALID_INPUT(false),
    /** The work did not finish in time. Retrying may succeed. */
    TIMEOUT(true),
    /** A dependency (LLM, tool, downstream service) was unreachable. Retrying may succeed. */
    UPSTREAM_UNAVAILABLE(true),
    /** The task was cancelled. Not an error to retry. */
    CANCELLED(false),
    /** Anything else — an unexpected internal failure. */
    INTERNAL(false);

    private final boolean retryable;

    RuntimeErrorCode(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }

    /**
     * Walks the cause chain (async failures wrap the real cause, e.g. {@code CompletionException})
     * and maps the first recognised exception type to a stable code; defaults to {@link #INTERNAL}.
     */
    public static RuntimeErrorCode classify(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException) {
                return TIMEOUT;
            }
            if (cause instanceof CancellationException) {
                return CANCELLED;
            }
            if (cause instanceof IllegalArgumentException) {
                return INVALID_INPUT;
            }
            if (cause instanceof IOException) {
                return UPSTREAM_UNAVAILABLE;
            }
            if (cause == cause.getCause()) {
                break;
            }
        }
        return INTERNAL;
    }
}
