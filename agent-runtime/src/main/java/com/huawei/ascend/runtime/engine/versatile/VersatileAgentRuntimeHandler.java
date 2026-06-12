package com.huawei.ascend.runtime.engine.versatile;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.AgentCardProvider;
import com.huawei.ascend.runtime.engine.a2a.AgentCards;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent-runtime handler that proxies A2A requests to a remote versatile
 * workflow REST API and maps the SSE response back to framework-neutral
 * {@link com.huawei.ascend.runtime.engine.spi.AgentExecutionResult} objects.
 *
 * <p>Implements both {@link AgentRuntimeHandler} (execution) and
 * {@link AgentCardProvider} (A2A discovery metadata).
 */
public final class VersatileAgentRuntimeHandler implements AgentRuntimeHandler, AgentCardProvider {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileAgentRuntimeHandler.class);

    private final String agentId;
    private final String name;
    private final String description;
    private final VersatileClient client;
    private final VersatileMessageAdapter messageAdapter;
    private final VersatileStreamAdapter streamAdapter;

    public VersatileAgentRuntimeHandler(
            String agentId,
            String name,
            String description,
            VersatileClient client,
            VersatileMessageAdapter messageAdapter,
            VersatileStreamAdapter streamAdapter) {
        org.springframework.util.Assert.hasText(agentId, "agentId must not be blank");
        org.springframework.util.Assert.hasText(name, "name must not be blank");
        this.agentId = agentId;
        this.name = name;
        this.description = description != null ? description : "";
        this.client = Objects.requireNonNull(client, "client");
        this.messageAdapter = Objects.requireNonNull(messageAdapter, "messageAdapter");
        this.streamAdapter = Objects.requireNonNull(streamAdapter, "streamAdapter");
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
        VersatileHttpRequest request = messageAdapter.toRequest(context);
        LOG.info("versatile execute start agentId={} sessionId={} taskId={} url={}",
                agentId, context.getScope().sessionId(), context.getScope().taskId(), request.url());
        try {
            return client.stream(request);
        } catch (VersatileClient.VersatileClientException error) {
            LOG.warn("versatile execute failed agentId={} sessionId={} taskId={} message={}",
                    agentId, context.getScope().sessionId(), context.getScope().taskId(), error.getMessage());
            return Stream.of(Map.of(
                    "event", "exception",
                    "data", Map.of("code", "CLIENT_ERROR", "message", error.getMessage())));
        }
    }

    @Override
    public StreamAdapter resultAdapter() {
        return streamAdapter;
    }

    @Override
    public AgentCard agentCard() {
        return AgentCards.create(name, description, "0.1.0", "/a2a");
    }
}
