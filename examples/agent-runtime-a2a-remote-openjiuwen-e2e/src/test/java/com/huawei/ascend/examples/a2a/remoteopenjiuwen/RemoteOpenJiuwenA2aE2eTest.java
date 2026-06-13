package com.huawei.ascend.examples.a2a.remoteopenjiuwen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * End-to-end test: Agent A (LLM) calls Agent B (mock) as a remote A2A tool.
 *
 * <p>Agent B starts first on a random port. Agent A starts second with Agent B's URL
 * configured as a remote agent. The runtime discovers Agent B's A2A card, injects it as
 * a tool into Agent A's OpenJiuwen ReActAgent, and the LLM chooses to invoke it.
 *
 * <p>The real-LLM test requires {@code SAA_SAMPLE_LLM_API_KEY} and is skipped otherwise.
 */
@Tag("e2e")
@ResourceLock("real-llm")
class RemoteOpenJiuwenA2aE2eTest {

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(90);

    @Test
    void llmAgentInvokesRemoteAgentViaA2aTool() throws Exception {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real-LLM remote A2A e2e test");

        // Start Agent B (mock, no LLM) with agent-b profile
        try (ConfigurableApplicationContext agentB = startRuntime("agent-b")) {
            int agentBPort = port(agentB);

            // Start Agent A (LLM-driven) with agent-a profile, pointing to Agent B
            try (ConfigurableApplicationContext agentA = startRuntime("agent-a",
                    "agent-runtime.remote-agents[0].url=http://localhost:" + agentBPort)) {

                // Verify Agent A card
                SampleA2aClient client = new SampleA2aClient(
                        URI.create("http://localhost:" + port(agentA)), CLIENT_TIMEOUT);
                AgentCard card = client.agentCard();
                assertThat(card.name()).isEqualTo(AgentAConfiguration.AGENT_ID);
                assertThat(card.description()).contains("LLM-driven");
                assertThat(card.capabilities().streaming()).isTrue();
                assertThat(card.supportedInterfaces())
                        .extracting(AgentInterface::protocolBinding)
                        .contains(TransportProtocol.JSONRPC.asString());

                // Ask Agent A to call remote Agent B
                List<StreamingEventKind> events = client.streamMessage(
                        "sample-user",
                        AgentAConfiguration.AGENT_ID,
                        "ctx-remote-e2e-llm",
                        "Please call remote AgentB to run the streaming input-required demo.");

                assertThat(events).isNotEmpty();
                assertThat(events).anySatisfy(event ->
                        assertThat(SampleA2aClient.isTerminal(event)).isTrue());

                String answer = SampleA2aClient.textFrom(events);
                assertThat(answer).isNotBlank();
            }
        }
    }

    private static ConfigurableApplicationContext startRuntime(String profile, String... extraProperties) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("--server.port=0");
        args.add("--spring.profiles.active=" + profile);
        for (String property : extraProperties) {
            args.add(property.startsWith("--") ? property : "--" + property);
        }
        return new SpringApplicationBuilder(RemoteOpenJiuwenA2aApplication.class)
                .run(args.toArray(String[]::new));
    }

    private static int port(ConfigurableApplicationContext context) {
        Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
        if (port == null) {
            throw new IllegalStateException("local.server.port is not available");
        }
        return port;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
