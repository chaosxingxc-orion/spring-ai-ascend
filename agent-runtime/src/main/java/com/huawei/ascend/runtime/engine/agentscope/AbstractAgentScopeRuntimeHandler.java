package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.common.Guards;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.Objects;
import java.util.stream.Stream;

abstract class AbstractAgentScopeRuntimeHandler implements AgentRuntimeHandler {

    private final String agentId;
    private final AgentScopeMessageAdapter messageAdapter;
    private final AgentScopeStreamAdapter streamAdapter;

    AbstractAgentScopeRuntimeHandler(String agentId, String name, String description) {
        this(agentId, name, description, new AgentScopeMessageAdapter(), new AgentScopeStreamAdapter());
    }

    AbstractAgentScopeRuntimeHandler(
            String agentId,
            String name,
            String description,
            AgentScopeMessageAdapter messageAdapter,
            AgentScopeStreamAdapter streamAdapter) {
        this.agentId = Guards.requireNonBlank(agentId, "agentId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        this.messageAdapter = messageAdapter;
        this.streamAdapter = streamAdapter;
    }

    @Override
    public final String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public final Stream<?> execute(AgentExecutionContext context) {
        return streamAgentScopeEvents(messageAdapter.toInvocation(context));
    }

    @Override
    public final StreamAdapter resultAdapter() {
        return streamAdapter;
    }

    protected abstract Stream<?> streamAgentScopeEvents(AgentScopeInvocation invocation);
}
