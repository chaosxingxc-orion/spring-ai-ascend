package com.huawei.ascend.client.telemetry;

/**
 * Client-side mirror of the platform's observability posture: head-sampling
 * ratio plus whether message text (PII) may ride on telemetry at all. The SDK
 * keeps its own enum so customer applications need no platform module on the
 * classpath; the ratios match the platform's posture contract.
 */
public enum Posture {

    /** Everything sampled, message text allowed — local development. */
    DEV(1.0, true),

    /** 10% head sampling, message text allowed — evaluation/research. */
    RESEARCH(0.10, true),

    /**
     * 1% head sampling and message text NEVER attached: production telemetry
     * must not carry end-user PII, structurally — the attributes are not set,
     * rather than set and filtered later.
     */
    PROD(0.01, false);

    private final double samplingRatio;
    private final boolean piiOnTelemetry;

    Posture(double samplingRatio, boolean piiOnTelemetry) {
        this.samplingRatio = samplingRatio;
        this.piiOnTelemetry = piiOnTelemetry;
    }

    /** Head-sampling ratio applied to root spans this SDK originates. */
    public double samplingRatio() {
        return samplingRatio;
    }

    /** Whether request/response text may be attached as span attributes. */
    public boolean allowsPiiOnTelemetry() {
        return piiOnTelemetry;
    }
}
