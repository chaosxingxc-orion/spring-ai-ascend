package ascend.springai.service.runtime.orchestration.spi;

/**
 * Raised when a Run's envelope declares {@code engineType=X} but the runtime
 * cannot dispatch the payload to a matching engine — either no
 * {@link ExecutorAdapter} is registered under {@code X}, or the registered
 * adapter rejects the payload type.
 *
 * <p>The strongest-reading semantics (Rule 1, ADR-0072): the runtime MUST
 * reject; it MUST NOT silently reinterpret the payload as another engine's
 * configuration. The owning Run transitions to {@code RunStatus.FAILED} with
 * reason {@code engine_mismatch}.
 *
 * <p>Pure Java — no Spring imports per architecture §4.7 (orchestration.spi
 * imports only java.*).
 *
 * <p>Authority: ADR-0072; CLAUDE.md Rule 44 (Strict Engine Matching).
 */
public final class EngineMatchingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String requestedEngineType;
    private final String actualPayloadType;

    public EngineMatchingException(String requestedEngineType, String actualPayloadType, String message) {
        super(message);
        this.requestedEngineType = requestedEngineType;
        this.actualPayloadType = actualPayloadType;
    }

    public String requestedEngineType() {
        return requestedEngineType;
    }

    public String actualPayloadType() {
        return actualPayloadType;
    }
}
