package com.huawei.ascend.examples.a2a.versatileparent;

import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.versatile.VersatileAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.versatile.VersatileClient;
import com.huawei.ascend.runtime.engine.versatile.VersatileMessageAdapter;
import com.huawei.ascend.runtime.engine.versatile.VersatileProperties;
import com.huawei.ascend.runtime.engine.versatile.VersatileStreamAdapter;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentProvider;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the versatile child agent.
 *
 * <p>Activate with {@code --spring.profiles.active=versatile}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "sample.versatile-parent.role", havingValue = "versatile")
@EnableConfigurationProperties(VersatileProperties.class)
public class VersatileAgentConfiguration {
    static final String AGENT_ID = "versatile-child";

    @Bean
    AgentRuntimeHandler versatileAgentRuntimeHandler(VersatileProperties properties) {
        VersatileClient client = new VersatileClient(properties);
        VersatileMessageAdapter messageAdapter = new VersatileMessageAdapter(properties);
        VersatileStreamAdapter streamAdapter = new VersatileStreamAdapter(properties);

        return new VersatileAgentRuntimeHandler(
                AGENT_ID,
                AGENT_ID,
                "Remote versatile workflow agent. Receives A2A requests, "
                        + "reconstructs the target REST call from structured metadata, "
                        + "streams SSE events, and supports interruption/resume.",
                client,
                messageAdapter,
                streamAdapter);
    }

    /**
     * Explicit AgentCard with skills.
     *
     * <p>Card name {@code versatile-child} normalizes to tool name
     * {@code a2a_remote_versatile_child}. The skill description tells the
     * parent LLM how to invoke this agent — it should pass a JSON object
     * whose keys and values are the business parameters for the workflow.
     */
    @Bean
    AgentCard versatileChildCard() {
        return AgentCard.builder()
                .name(AGENT_ID)
                .description("Remote versatile workflow agent. Receives A2A requests, "
                        + "reconstructs the target REST call from structured metadata, "
                        + "streams SSE events, and supports interruption/resume.")
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:18082"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true).pushNotifications(false).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder()
                        .id("versatile-workflow-proxy")
                        .name("Versatile workflow proxy")
                        .description("Call this tool to invoke a remote workflow via the "
                                + "Versatile engine. Pass a JSON object whose keys and "
                                + "values are the business parameters required by the "
                                + "target workflow. Every entry becomes an input field of "
                                + "the workflow request. Example: "
                                + "{\"field_name_1\":\"value_1\",\"field_name_2\":\"value_2\"}")
                        .tags(List.of("versatile", "sse", "streaming", "workflow"))
                        .build()))
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }
}
