package com.huawei.ascend.runtime.engine.a2a;

import java.util.Objects;

/**
 * Pluggable remote-invocation capability carried by {@code A2aAgentExecutor}.
 * An executor with a non-null {@code RemoteSupport} can fan out tool calls to
 * remote A2A agents; without it only local handler execution is available.
 */
public final class RemoteSupport {
    private final RemoteAgentInvocationService invocationService;

    public RemoteSupport(RemoteAgentInvocationService invocationService) {
        this.invocationService = Objects.requireNonNull(invocationService, "invocationService");
    }

    public static RemoteSupport forOutbound(RemoteAgentInvocationService.OutboundPort outboundPort) {
        return new RemoteSupport(new RemoteAgentInvocationService(outboundPort));
    }

    public RemoteAgentInvocationService invocationService() {
        return invocationService;
    }
}
