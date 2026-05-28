package com.huawei.ascend.service.runtime.executor.spi;

import java.util.concurrent.Flow;

/**
 * Executor Adapter SPI. Unifies the execute contract across Native,
 * Third-party, and Remote Agent forms.
 *
 * <p>Authority: ADR-0155. Status: design_only at this commit.</p>
 */
public interface ExecutorAdapter {

    /**
     * The wiring mode this adapter uses for resource-call interception.
     */
    InjectionMode injectionMode();

    /**
     * Execute the given request and produce a stream of AgentEvent values.
     *
     * <p>The publisher is non-blocking; the adapter must not perform
     * blocking I/O in the subscription thread. Resource calls
     * (model / tool / memory / RAG / client-hosted skill) MUST go
     * through the M6 intercept SPIs ({@code PlatformChatClient},
     * {@code PlatformToolCallback}, etc.); the adapter MUST NOT call
     * vendor SDKs directly.</p>
     *
     * @param request immutable execution request descriptor; the schema
     *                lives at {@code docs/contracts/execution-request.v1.yaml}
     *                and the carrier record is currently a Java placeholder
     *                (W2 lands the concrete record).
     * @return a non-null publisher of agent events; the schema lives at
     *                {@code docs/contracts/agent-event.v1.yaml}.
     */
    Flow.Publisher<Object> execute(Object request);
}
