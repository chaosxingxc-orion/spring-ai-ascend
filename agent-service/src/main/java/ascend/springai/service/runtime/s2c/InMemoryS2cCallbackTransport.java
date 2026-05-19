package ascend.springai.service.runtime.s2c;

import ascend.springai.bus.spi.s2c.S2cCallbackEnvelope;
import ascend.springai.bus.spi.s2c.S2cCallbackResponse;
import ascend.springai.bus.spi.s2c.S2cCallbackTransport;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * In-memory reference implementation of {@link S2cCallbackTransport} for
 * tests and dev posture. Production transports (webhook, SSE, WebSocket)
 * land in W3.
 *
 * <p>Construct with a function that maps an incoming
 * {@link S2cCallbackEnvelope} to an {@link S2cCallbackResponse}; the transport
 * invokes the function inline and completes the returned stage with its result.
 * Tests can inject canned responses, simulated errors, simulated timeouts.
 *
 * <p>Pure Java - no Spring imports.
 *
 * <p>Authority: ADR-0074.
 */
public final class InMemoryS2cCallbackTransport implements S2cCallbackTransport {

    private final Function<S2cCallbackEnvelope, S2cCallbackResponse> responder;

    public InMemoryS2cCallbackTransport(Function<S2cCallbackEnvelope, S2cCallbackResponse> responder) {
        this.responder = Objects.requireNonNull(responder, "responder is required");
    }

    @Override
    public CompletionStage<S2cCallbackResponse> dispatch(S2cCallbackEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope is required");
        try {
            return CompletableFuture.completedFuture(responder.apply(envelope));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
