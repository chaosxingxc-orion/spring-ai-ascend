package com.huawei.ascend.client;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Outbound W3C Trace Context (version 00) origination, dependency-free by
 * design: this is the no-telemetry fallback, generating trace ids with JDK
 * primitives so the default client path never needs OpenTelemetry. Each call
 * gets a fresh {@code traceparent} (new trace-id + span-id); the server edge
 * parses it and answers a {@code traceresponse} header that
 * {@link TraceCorrelation} surfaces back to the caller. With a
 * {@code ClientTelemetry} configured, the outbound header is derived from the
 * call's span instead and this mint is not consulted.
 */
public final class TracePropagation {

    private static final int TRACE_ID_HEX_CHARS = 32;
    private static final int SPAN_ID_HEX_CHARS = 16;

    private final boolean sampled;

    private TracePropagation(boolean sampled) {
        this.sampled = sampled;
    }

    /** Default: trace-flags 01 — ask the server side to record the trace. */
    public static TracePropagation sampled() {
        return new TracePropagation(true);
    }

    /** Trace-flags 00 — propagate correlation ids without requesting sampling. */
    public static TracePropagation notSampled() {
        return new TracePropagation(false);
    }

    /** A fresh version-00 traceparent for one call. */
    String newTraceparent() {
        return "00-" + randomLowerHex(TRACE_ID_HEX_CHARS)
                + "-" + randomLowerHex(SPAN_ID_HEX_CHARS)
                + "-" + (sampled ? "01" : "00");
    }

    /**
     * Random lowercase hex, never all-zero: W3C reserves the all-zero
     * trace/span id as invalid and the platform edge rejects it.
     */
    private static String randomLowerHex(int hexChars) {
        byte[] bytes = new byte[hexChars / 2];
        do {
            ThreadLocalRandom.current().nextBytes(bytes);
        } while (isAllZero(bytes));
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean isAllZero(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }
}
