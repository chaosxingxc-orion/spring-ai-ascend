package com.huawei.ascend.examples.a2a.versatileparent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * End-to-end test: Main parent (OpenJiuwen LLM) calls versatile child agent
 * via A2A remote tool, with interruption detection and resume.
 *
 * <p>Uses a {@link VersatileMockService} (WireMock) to simulate the Versatile
 * REST API, so tests run offline without an external service dependency.
 *
 * <p>The real-LLM tests require {@code SAA_SAMPLE_LLM_API_KEY} and are skipped
 * otherwise.
 */
@Tag("e2e")
@ResourceLock("real-llm")
class VersatileParentA2eE2eTest {

    private static final Duration CLIENT_TIMEOUT = Duration.ofSeconds(90);

    private VersatileMockService versatileMock;

    @BeforeEach
    void setUp() {
        versatileMock = new VersatileMockService();
        versatileMock.start();
    }

    @AfterEach
    void tearDown() {
        if (versatileMock != null) {
            versatileMock.stop();
        }
    }

    // ── Card discovery (no LLM needed) ──

    @Test
    void agentCardIsDiscoverable() throws Exception {
        try (ConfigurableApplicationContext versatile = startRuntime("versatile")) {
            VersatileParentA2aClient client = new VersatileParentA2aClient(
                    URI.create("http://localhost:" + port(versatile)), CLIENT_TIMEOUT);
            AgentCard card = client.agentCard();
            assertThat(card.name()).isEqualTo("versatile-child");
            assertThat(card.description()).contains("interruption");
            assertThat(card.capabilities().streaming()).isTrue();
            assertThat(card.supportedInterfaces())
                    .extracting(AgentInterface::protocolBinding)
                    .contains(TransportProtocol.JSONRPC.asString());
        }
    }

    @Test
    void mainAgentCardIsDiscoverable() throws Exception {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real-LLM test");

        try (ConfigurableApplicationContext main = startRuntime("main")) {
            VersatileParentA2aClient client = new VersatileParentA2aClient(
                    URI.create("http://localhost:" + port(main)), CLIENT_TIMEOUT);
            AgentCard card = client.agentCard();
            assertThat(card.name()).isEqualTo(MainAgentConfiguration.AGENT_ID);
            assertThat(card.capabilities().streaming()).isTrue();
        }
    }

    // ── LLM-driven tests (use mock versatile API) ──

    @Test
    void llmParentInvokesVersatileChildViaA2aTool() throws Exception {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real-LLM test");

        versatileMock.stubBookingFlow();

        try (ConfigurableApplicationContext child = startRuntime("versatile")) {
            int childPort = port(child);

            try (ConfigurableApplicationContext main = startRuntime("main",
                    "agent-runtime.remote-agents[0].url=http://localhost:" + childPort)) {

                VersatileParentA2aClient client = new VersatileParentA2aClient(
                        URI.create("http://localhost:" + port(main)), CLIENT_TIMEOUT);
                AgentCard card = client.agentCard();
                assertThat(card.name()).isEqualTo(MainAgentConfiguration.AGENT_ID);

                List<StreamingEventKind> events = client.streamMessage(
                        "sample-user",
                        MainAgentConfiguration.AGENT_ID,
                        "ctx-versatile-parent-e2e",
                        null,
                        "Please call the versatile child agent to process my request.");

                assertThat(events).isNotEmpty();
                assertThat(events).anySatisfy(event ->
                        assertThat(VersatileParentA2aClient.isTerminal(event)).isTrue());

                String answer = VersatileParentA2aClient.textFrom(events);
                assertThat(answer).isNotBlank();
            }
        }
    }

    @Test
    void versatileInterruptionAndResume() throws Exception {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real-LLM test");

        versatileMock.stubInterruptFlow();

        try (ConfigurableApplicationContext child = startRuntime("versatile")) {
            int childPort = port(child);

            try (ConfigurableApplicationContext main = startRuntime("main",
                    "agent-runtime.remote-agents[0].url=http://localhost:" + childPort)) {

                VersatileParentA2aClient client = new VersatileParentA2aClient(
                        URI.create("http://localhost:" + port(main)), CLIENT_TIMEOUT);

                String sessionId = "ctx-interrupt-e2e-" + System.currentTimeMillis();

                List<StreamingEventKind> firstEvents = client.streamMessage(
                        "sample-user",
                        MainAgentConfiguration.AGENT_ID,
                        sessionId,
                        null,
                        "Please call the versatile child agent with a task that may be interrupted.");

                assertThat(firstEvents).isNotEmpty();

                if (VersatileParentA2aClient.isInputRequired(firstEvents)) {
                    String taskId = VersatileParentA2aClient.firstTaskId(firstEvents);
                    assertThat(taskId).isNotBlank();

                    List<StreamingEventKind> resumeEvents = client.streamMessage(
                            "sample-user",
                            MainAgentConfiguration.AGENT_ID,
                            sessionId,
                            taskId,
                            "Here is the follow-up input for the interrupted versatile task.");

                    assertThat(resumeEvents).isNotEmpty();
                    assertThat(resumeEvents).anySatisfy(event ->
                            assertThat(VersatileParentA2aClient.isTerminal(event)).isTrue());
                }
            }
        }
    }

    // ── Helpers ──

    private ConfigurableApplicationContext startRuntime(String profile, String... extraProperties) {
        java.util.List<String> args = new java.util.ArrayList<>();
        args.add("--server.port=0");
        args.add("--spring.profiles.active=" + profile);
        // Point the versatile child to the per-test WireMock server (random port)
        // and disable the embedded mock that would clash with it.
        args.add("--versatile.mock.embedded=false");
        args.add("--versatile.url=http://localhost:" + versatileMock.port()
                + "/v1/mock_project_id/agents/mock_agent/conversations/{conversation_id}");
        for (String property : extraProperties) {
            args.add(property.startsWith("--") ? property : "--" + property);
        }
        return new SpringApplicationBuilder(VersatileParentA2aApplication.class)
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
