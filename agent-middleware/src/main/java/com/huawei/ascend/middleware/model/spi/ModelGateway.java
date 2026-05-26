package com.huawei.ascend.middleware.model.spi;

import java.util.Objects;
import java.util.stream.Stream;

/**
 * Tenant-scoped LLM invocation boundary.
 *
 * <p>Authority: ADR-0121. The reference adapter
 * {@code SpringAiChatModelGateway} wraps Spring AI's {@code ChatModel}
 * (ADR-0125).
 *
 * <p>Implementations:
 * <ul>
 *   <li>MUST validate {@link ModelInvocation#tenantId()} non-blank
 *       (Rule R-C.c).</li>
 *   <li>SHOULD route through {@code HookDispatcher.fire(BEFORE_LLM,
 *       invocation)} before the underlying provider call and
 *       {@code AFTER_LLM} after; hook outcomes (ADR-0073).</li>
 *   <li>MAY consult {@code ResilienceContract.resolve(tenant,
 *       "model:" + invocation.modelId())} for per-model capacity
 *       arbitration.</li>
 *   <li>MUST be thread-safe; the runtime invokes from virtual
 *       threads.</li>
 * </ul>
 *
 * <p>SPI purity per Rule R-D: imports only {@code java.*} +
 * same-package siblings.
 */
public interface ModelGateway {

    /**
     * Invoke the model with the given invocation envelope.
     *
     * @param invocation tenant-scoped invocation; never null.
     * @return the model's response; never null.
     */
    ModelResponse invoke(ModelInvocation invocation);

    /**
     * Stream the model response as a sequence of chunks. The default
     * implementation throws {@link UnsupportedOperationException};
     * implementations override when the underlying provider supports
     * streaming (server-sent events / WebSocket / chunked HTTP). The
     * terminal {@link ModelResponseChunk.Complete} chunk carries the
     * assembled {@link ModelResponse}.
     *
     * <p>Authority: ADR-0129, schema at
     * {@code docs/contracts/model-streaming.v1.yaml}.
     *
     * <p>Hook binding: {@code HookPoint.BEFORE_LLM} fires once with
     * {@link ModelInvocation} before stream open;
     * {@code HookPoint.AFTER_LLM} fires once with the terminal
     * {@code Complete.finalResponse()}. Per-chunk hooks are not
     * declared at L0.
     *
     * <p>SPI purity per Rule R-D: the return type is
     * {@link java.util.stream.Stream} — Reactor {@code Flux} is
     * deliberately not in the SPI surface; the adapter implementation
     * bridges provider-native reactive types into a virtual-thread
     * friendly iterator behind this signature.
     *
     * @param invocation tenant-scoped invocation; never null.
     * @return a finite ordered stream of chunks; never null. A successful
     *         stream MUST contain exactly one
     *         {@link ModelResponseChunk.Complete} element which MUST be the
     *         last. A cancelled stream may close before Complete, and
     *         provider/runtime errors surface as exceptions from the stream.
     */
    default Stream<ModelResponseChunk> stream(ModelInvocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
        throw new UnsupportedOperationException(
                getClass().getSimpleName()
                        + ": streaming is design-only at L0; "
                        + "the LLM gateway implementation wires Spring AI ChatModel.stream(...) "
                        + "behind virtual-thread isolation.");
    }

    /**
     * Stable id identifying this gateway instance for registry
     * lookup and hook attribution. Default returns the
     * implementation class name; impls SHOULD override with a
     * meaningful stable id.
     */
    default String gatewayId() {
        return Objects.requireNonNullElse(getClass().getSimpleName(), "model-gateway");
    }
}
