package com.huawei.ascend.runtime.app;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.Objects;

/**
 * The runtime pieces {@link RuntimeApp} hands to a {@link RuntimeHost} to assemble and serve.
 * Currently just the primary {@link AgentRuntimeHandler}; service/provider injection
 * (tool/memory/state/sandbox) is added here as the runtime grows.
 */
public record RuntimeComponents(AgentRuntimeHandler handler) {

    public RuntimeComponents {
        Objects.requireNonNull(handler, "handler");
    }
}
