package com.huawei.ascend.runtime.engine.versatile;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.AgentCardProvider;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent-runtime handler that proxies A2A requests to a remote versatile
 * workflow REST API and maps the SSE response back to framework-neutral
 * {@link com.huawei.ascend.runtime.engine.spi.AgentExecutionResult} objects.
 *
 * <p>Implements both {@link AgentRuntimeHandler} (execution) and
 * {@link AgentCardProvider} (A2A discovery metadata). The card includes
 * skills that describe the versatile tool contract so parent LLM agents
 * know how to invoke it.
 */
public final class VersatileAgentRuntimeHandler implements AgentRuntimeHandler, AgentCardProvider {

    private static final Logger LOG = LoggerFactory.getLogger(VersatileAgentRuntimeHandler.class);

    private static final String SKILL_ID = "versatile-workflow-proxy";
    private static final String SKILL_NAME = "Versatile workflow proxy";
    private static final String SKILL_DESCRIPTION = """
            Call this tool to invoke a remote workflow via the Versatile engine.
            Pass a JSON object whose keys and values are the business parameters
            required by the target workflow. Every entry becomes an input field
            of the workflow request.

            Example:
            {"field_name_1": "value_1", "field_name_2": "value_2", ...}
            """;

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

    /**
     * Creates the A2A AgentCard with skills that describe the versatile
     * tool contract. The card name is used by {@code RemoteAgentCardCache}
     * to derive the tool name (the remote agent id itself).
     * The skill description tells the parent LLM how to invoke this agent
     * with JSON arguments.
     */
    @Override
    public AgentCard agentCard() {
        return AgentCard.builder()
                .name(name)
                .description(description)
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:18082"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder()
                        .id(SKILL_ID)
                        .name(SKILL_NAME)
                        .description(SKILL_DESCRIPTION)
                        .tags(List.of("versatile", "sse", "streaming", "workflow", "interruption"))
                        .build()))
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }
}
