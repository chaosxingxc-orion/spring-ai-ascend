package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.stream.Stream;

abstract class AbstractAgentScopeRuntimeHandler extends AbstractAgentRuntimeHandler {

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
        super(agentId, name, description);
        this.messageAdapter = messageAdapter;
        this.streamAdapter = streamAdapter;
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
