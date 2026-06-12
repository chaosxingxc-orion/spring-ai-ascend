package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@Tag("e2e")
@ResourceLock("real-llm")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = A2aAgentRuntimeApplication.class,
        properties = "sample.a2a.agent=agentscope")
class AgentScopeA2aE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @LocalServerPort
    private int port;

    @Test
    void a2aClientCanStreamAgentScopeSdkAgentThroughAgentRuntimeOnly() throws Exception {
        assumeRealLlmConfigured("AgentScope SDK agent");

        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:" + port), TIMEOUT);
        AgentCard card = client.agentCard();
        assertThat(card.name()).isEqualTo(AgentScopeE2eConfiguration.AGENT_ID);
        assertAgentScopePathReturnsPong(client, card.name());
    }

    private void assertAgentScopePathReturnsPong(SampleA2aClient client, String agentId) throws Exception {
        String sessionId = "session-" + UUID.randomUUID();
        List<StreamingEventKind> events = client.streamMessage("sample-user", agentId, sessionId, "ping");

        assertThat(events).isNotEmpty();
        assertThat(events).anySatisfy(event -> assertThat(SampleA2aClient.isTerminal(event)).isTrue());
        assertThat(normalizeAnswer(SampleA2aClient.textFrom(events))).isEqualTo("pong");
    }

    private static void assumeRealLlmConfigured(String sampleName) {
        assumeTrue(hasText(System.getenv("SAA_SAMPLE_LLM_API_KEY")),
                "SAA_SAMPLE_LLM_API_KEY not set; skipping real " + sampleName + " E2E sample");
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
