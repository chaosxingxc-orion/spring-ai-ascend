package com.huawei.ascend.service.starter;

import java.time.Duration;

/**
 * Observation seam for completed A2A gateway forwards. The starter deliberately
 * ships no telemetry store of its own — agent-runtime standardizes on
 * Micrometer, and a bespoke event-record pipeline would be a second
 * observability truth. Deployments that want per-forward records (the e2e
 * example does) register an observer bean; the default is a no-op.
 */
@FunctionalInterface
public interface A2aForwardObserver {

    void onForwardCompleted(A2aForwardCompletion completion);

    static A2aForwardObserver noop() {
        return completion -> { };
    }

    /** Outcome of one byte-level A2A forward through the gateway edge. */
    record A2aForwardCompletion(
            String tenantId,
            String sourceAgentId,
            String targetAgentId,
            String runtimeInstanceId,
            String grantId,
            String a2aMethod,
            String sessionId,
            String correlationId,
            String status,
            String errorCode,
            Duration routeResolveLatency,
            Duration firstByteLatency,
            Duration totalLatency,
            long requestBytes,
            long responseBytes) {
    }
}
