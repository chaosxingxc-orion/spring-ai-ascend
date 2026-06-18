package com.huawei.ascend.examples.a2a.remoteagenttool;

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
 * End-to-end test: local OpenJiuwen Agent calls a remote A2A Agent as a tool.
 *
 * <p>The remote A2A Agent starts first on a random port. The local OpenJiuwen Agent starts second
 * with the remote Agent Card URL configured. The runtime discovers the remote card, injects it as
 * a tool into the local OpenJiuwen ReActAgent, and the LLM chooses to invoke it.
 *
 * <p>The real-LLM test requires {@code SAA_SAMPLE_LLM_API_KEY} and is skipped otherwise.
 */
@Tag("e2e")
@ResourceLock("real-llm")
class RemoteA2aToolInvocationE2eTest {

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(90);

    @Test
    void llmAgentInvokesRemoteAgentViaA2aTool() throws Exception {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real-LLM remote A2A e2e test");

        // Start the remote A2A agent (mock, no LLM).
        try (ConfigurableApplicationContext agentB = startRuntime("remote-agent")) {
            int agentBPort = port(agentB);

            // Start the local OpenJiuwen agent, pointing to the remote A2A agent.
            try (ConfigurableApplicationContext agentA = startRuntime("local-agent",
                    "agent-runtime.remote-agents[0].url=http://localhost:" + agentBPort)) {

                // Verify the local agent card.
                A2aStreamingTestClient client = new A2aStreamingTestClient(
                        URI.create("http://localhost:" + port(agentA)), CLIENT_TIMEOUT);
                AgentCard card = client.agentCard();
                assertThat(card.name()).isEqualTo(LocalOpenJiuwenAgentConfiguration.AGENT_ID);
                assertThat(card.description()).contains("LLM-driven");
                assertThat(card.capabilities().streaming()).isTrue();
                assertThat(card.supportedInterfaces())
                        .extracting(AgentInterface::protocolBinding)
                        .contains(TransportProtocol.JSONRPC.asString());

                // Ask the local agent to call the remote A2A agent.
                List<StreamingEventKind> events = client.streamMessage(
                        "sample-user",
                        LocalOpenJiuwenAgentConfiguration.AGENT_ID,
                        "ctx-remote-e2e-llm",
                        "Please call the remote A2A agent to run the streaming input-required demo.");

                assertThat(events).isNotEmpty();
                assertThat(events).anySatisfy(event ->
                        assertThat(A2aStreamingTestClient.isTerminal(event)).isTrue());

                String answer = A2aStreamingTestClient.textFrom(events);
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
        return new SpringApplicationBuilder(RemoteA2aToolInvocationApplication.class)
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
