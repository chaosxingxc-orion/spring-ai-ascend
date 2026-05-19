package ascend.springai.bus.spi.s2c;

import java.util.concurrent.CompletionStage;

/**
 * Transport SPI for Server-to-Client (S2C) capability invocation.
 *
 * <p>Implementations carry the {@link S2cCallbackEnvelope} request out to the
 * client and return a {@link CompletionStage} that completes with the
 * {@link S2cCallbackResponse}. The actual wire format is implementation-defined
 * (webhook POST, SSE push, WebSocket, gRPC, etc.); only the in-memory reference
 * implementation
 * {@link ascend.springai.service.runtime.s2c.InMemoryS2cCallbackTransport}
 * ships at W2.x. Production transports land in W3.
 *
 * <p>Implementations MUST NOT block the calling thread; they MUST honor the
 * Reactive / Virtual Threads boundary of Rule 37 (Reactive External I/O).
 * At W2.x SyncOrchestrator blocks on the returned CompletionStage as part of
 * its single-threaded recursion; W2's async orchestrator will use proper
 * non-blocking composition.
 *
 * <p>Pure Java - no Spring imports.
 *
 * <p>Authority: ADR-0074; CLAUDE.md Rule 46.
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
