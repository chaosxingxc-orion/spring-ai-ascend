package com.huawei.ascend.runtime.engine.langgraph;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Hosts a remote LangGraph-served agent (LangGraph Platform / langgraph-api)
 * behind the framework-neutral runtime SPI, the LangGraph sibling of
 * {@code AgentScopeRuntimeClientHandler}.
 */
public final class LangGraphRuntimeClientHandler implements AgentRuntimeHandler {

    private final String agentId;
    private final LangGraphRuntimeClient client;
    private final LangGraphStreamAdapter streamAdapter = new LangGraphStreamAdapter();

    public LangGraphRuntimeClientHandler(String agentId, LangGraphRuntimeClient client) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        this.agentId = agentId;
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override
    public String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public Stream<?> execute(AgentExecutionContext context) {
        return client.streamEvents(context);
    }

    @Override
    public StreamAdapter resultAdapter() {
        return streamAdapter;
    }

    @Override
    public void stop() {
        client.close();
    }
}
