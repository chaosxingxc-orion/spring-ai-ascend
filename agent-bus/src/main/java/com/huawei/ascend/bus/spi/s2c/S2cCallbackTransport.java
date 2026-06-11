package com.huawei.ascend.bus.spi.s2c;

import java.util.concurrent.CompletionStage;

/**
 * Transport SPI for Server-to-Client (S2C) capability invocation.
 *
 * <p>Implementations carry the {@link S2cCallbackEnvelope} request out to the
 * client and return a {@link CompletionStage} that completes with the
 * {@link S2cCallbackResponse}. The actual wire format is implementation-defined
 * (webhook POST, SSE push, WebSocket, gRPC, etc.). This SPI is currently
 * design-only: no implementation ships in this repository yet — consult
 * {@code docs/contracts/s2c-callback.v1.yaml} for the contract status before
 * designing against it.
 *
 * <p>Implementations MUST NOT block the calling thread; they MUST honor the
 * Reactive External I/O rule (Rule R-G).
 *
 * <p>Pure Java - no Spring imports.
 *
 * <p>Authority: ADR-0074.
 */
public interface S2cCallbackTransport {

    /**
     * Send the request envelope to the client and return a stage that completes
     * with the response.
     *
     * <p>Implementations should NOT throw synchronously for transport-layer
     * failures; instead they should complete the stage exceptionally so the
     * orchestrator can map the failure to Run.FAILED with reason
     * {@code s2c_transport_failure} via a single code path.
     */
    CompletionStage<S2cCallbackResponse> dispatch(S2cCallbackEnvelope envelope);
}
