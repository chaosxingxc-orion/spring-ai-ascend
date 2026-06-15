package com.huawei.ascend.runtime.app;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.Objects;

/**
 * Pure-Java developer entry point for the agent-runtime — the analogue of AgentScope
 * Runtime Java's {@code AgentApp.run(...)}: hand the runtime an {@link AgentRuntimeHandler}
 * and a {@link RuntimeHost}, and the host assembles the layers and starts serving.
 *
 * <p>{@code RuntimeApp} and {@link RuntimeHost} are framework-neutral and carry NO Spring Boot
 * dependency; a concrete host such as {@link LocalA2aRuntimeHost} chooses the HTTP stack. This
 * keeps the runtime core embeddable: the only bootable concern that depends on Spring Boot is the
 * host implementation.
 *
 * <pre>{@code
 * try (RunningRuntime runtime = RuntimeApp.create(handler).run(LocalA2aRuntimeHost.port(8080))) {
 *     // serving A2A on runtime.port()
 * }
 * }</pre>
 */
public final class RuntimeApp {

    private final AgentRuntimeHandler handler;

    private RuntimeApp(AgentRuntimeHandler handler) {
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /** Start an application around a single {@link AgentRuntimeHandler}. */
    public static RuntimeApp create(AgentRuntimeHandler handler) {
        return new RuntimeApp(handler);
    }

    /** Assemble and start the runtime on the given host; the returned handle stops it on close. */
    public RunningRuntime run(RuntimeHost host) {
        Objects.requireNonNull(host, "host");
        return host.start(new RuntimeComponents(handler));
    }
}
