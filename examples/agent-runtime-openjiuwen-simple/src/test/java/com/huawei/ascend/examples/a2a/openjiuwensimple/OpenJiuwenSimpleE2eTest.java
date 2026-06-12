package com.huawei.ascend.examples.a2a.openjiuwensimple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Manual end-to-end verification: A2A client → agent-runtime → openJiuwen ReActAgent.
 *
 * <p>Requires a real LLM endpoint. Set {@code SAA_SAMPLE_LLM_API_KEY} and optionally
 * {@code SAA_SAMPLE_OPENJIUWEN_API_BASE} / {@code SAA_SAMPLE_LLM_MODEL} before running.
 */
@Tag("manual")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenJiuwenSimpleApplication.class)
class OpenJiuwenSimpleE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final String AGENT_ID = OpenJiuwenSimpleAgentConfiguration.AGENT_ID;

    @LocalServerPort
    private int port;

    @Test
    void agentCardIsDiscoverable() throws Exception {
        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:" + port), TIMEOUT);

        AgentCard agentCard = client.agentCard();
        assertThat(agentCard.name()).isEqualTo(AGENT_ID);
        assertThat(agentCard.description()).contains("openJiuwen ReAct agent");
        assertThat(agentCard.capabilities().streaming()).isTrue();
        assertThat(agentCard.supportedInterfaces())
                .extracting(AgentInterface::protocolBinding)
                .contains(TransportProtocol.JSONRPC.asString());
    }

    @Test
    void a2aClientCanStreamOpenJiuwenAgent() throws Exception {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real LLM E2E test");

        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:" + port), TIMEOUT);

        String sessionId = "session-" + UUID.randomUUID();
        List<StreamingEventKind> events = client.streamMessage(
                "sample-user", AGENT_ID, sessionId, "Hello, please introduce yourself in one sentence.");

        assertThat(events).isNotEmpty();
        assertThat(events).anySatisfy(event ->
                assertThat(SampleA2aClient.isTerminal(event)).isTrue());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
