package com.huawei.ascend.examples.financial.versatilecall;

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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single-agent configuration for the versatile-call agent.
 *
 * <p>Mirrors the reference {@code VersatileAgentConfiguration} but drops the
 * role/profile conditional (this module always creates its one agent). It
 * wires the {@link VersatileClient}/{@link VersatileMessageAdapter}/
 * {@link VersatileStreamAdapter} trio onto {@link VersatileProperties}, which
 * points at the external versatile MOCK and declares {@code result-node-type: QA}
 * so the final content is assembled from cached {@code node_type=QA} messages.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VersatileProperties.class)
public class VersatileCallConfiguration {
    static final String AGENT_ID = "versatile-call";

    private static final String DESCRIPTION = "Versatile bank proxy. Reconstructs the target REST call from "
            + "inputs, streams SSE, extracts node_type=QA results, supports interruption/resume.";

    @Bean
    AgentRuntimeHandler versatileCallHandler(VersatileProperties properties) {
        VersatileClient client = new VersatileClient(properties);
        VersatileMessageAdapter messageAdapter = new VersatileMessageAdapter(properties);
        VersatileStreamAdapter streamAdapter = new VersatileStreamAdapter(properties);

        return new VersatileAgentRuntimeHandler(AGENT_ID, AGENT_ID, DESCRIPTION, client, messageAdapter,
                streamAdapter);
    }

    /**
     * Explicit AgentCard.
     *
     * <p>Card name {@code versatile-call} normalizes to the remote-tool name
     * {@code versatile-call}. The skill description tells the plan-agent LLM
     * how to call this agent: pass the versatile request body JSON. The tool
     * name and its input schema are injected by the agent-runtime remote-tool
     * mechanism, so the skill need not name the schema field.
     */
    @Bean
    AgentCard versatileCallCard() {
        return AgentCard.builder()
                .name(AGENT_ID)
                .description(DESCRIPTION)
                .version("0.1.0")
                .provider(new AgentProvider("spring-ai-ascend", "http://localhost:18092"))
                .capabilities(AgentCapabilities.builder()
                        .streaming(true).pushNotifications(false).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(AgentSkill.builder()
                        .id("versatile-bank-proxy")
                        .name("Versatile bank proxy")
                        .description("Call this tool to execute a bank workflow (query balance / transfer). "
                                + "Pass the versatile request body JSON: "
                                + "{\"inputs\":{\"query\":\"<natural-language subtask>\","
                                + "\"intent\":\"查询账户余额|快速转账\"}}. "
                                + "query is the natural-language subtask; intent is a fixed enum.")
                        .tags(List.of("versatile", "sse", "banking", "workflow"))
                        .build()))
                .supportedInterfaces(List.of(new AgentInterface(
                        TransportProtocol.JSONRPC.asString(), "/a2a")))
                .build();
    }
}
