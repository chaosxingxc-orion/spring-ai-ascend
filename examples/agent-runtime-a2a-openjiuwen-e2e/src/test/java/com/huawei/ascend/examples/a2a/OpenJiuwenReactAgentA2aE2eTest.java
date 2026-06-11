package com.huawei.ascend.examples.a2a;

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
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@Tag("e2e")
@ResourceLock("real-llm")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OpenJiuwenA2aAgentRuntimeApplication.class)
class OpenJiuwenReactAgentA2aE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(45);
    private static final String AGENT_ID = OpenJiuwenReactAgentConfiguration.AGENT_ID;

    @LocalServerPort
    private int port;

    @Test
    void a2aClientCanStreamOpenJiuwenReactAgentThroughAgentRuntimeOnly() throws Exception {
        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:" + port), TIMEOUT);

        AgentCard agentCard = client.agentCard();
        assertThat(agentCard.name()).isEqualTo(AGENT_ID);
        assertThat(agentCard.description()).contains("openJiuwen ReAct agent");
        assertThat(agentCard.capabilities().streaming()).isTrue();
        assertThat(agentCard.supportedInterfaces())
                .extracting(AgentInterface::protocolBinding)
                .contains(TransportProtocol.JSONRPC.asString());

        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real openJiuwen ReAct agent E2E sample");

        String sessionId = "session-" + UUID.randomUUID();
        List<StreamingEventKind> events = client.streamMessage("sample-user", AGENT_ID, sessionId, "ping");

        assertThat(events).isNotEmpty();
        assertThat(events).anySatisfy(event -> assertThat(SampleA2aClient.isTerminal(event)).isTrue());
        assertThat(normalizeAnswer(SampleA2aClient.textFrom(events))).isEqualTo("pong");
    }

    private static String normalizeAnswer(String answer) {
        return answer.strip()
                .toLowerCase(Locale.ROOT)
                .replaceFirst("[\\p{Punct}\\s]+$", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
