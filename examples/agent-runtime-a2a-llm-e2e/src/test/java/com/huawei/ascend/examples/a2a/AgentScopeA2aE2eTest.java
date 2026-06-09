package com.huawei.ascend.examples.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.a2aproject.sdk.spec.Message;
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
        classes = OpenJiuwenA2aAgentRuntimeApplication.class)
class AgentScopeA2aE2eTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @LocalServerPort
    private int port;

    @Test
    void a2aClientCanStreamAgentScopeSdkAgentThroughAgentRuntimeOnly() throws Exception {
        assumeRealLlmConfigured("AgentScope SDK agent");

        assertAgentScopePathReturnsPong(AgentScopeE2eConfiguration.AGENT_ID);
    }

    @Test
    void a2aClientCanStreamAgentScopeHarnessAgentThroughAgentRuntimeOnly() throws Exception {
        assumeRealLlmConfigured("AgentScope Harness agent");

        assertAgentScopePathReturnsPong(AgentScopeE2eConfiguration.HARNESS_AGENT_ID);
    }

    @Test
    void a2aClientCanStreamAgentScopeRestRuntimeThroughAgentRuntimeOnly() throws Exception {
        assumeRealLlmConfigured("AgentScope REST/SSE runtime");

        assertAgentScopePathReturnsPong(AgentScopeE2eConfiguration.RUNTIME_AGENT_ID);
    }

    private void assertAgentScopePathReturnsPong(String agentId) throws Exception {
        SampleA2aClient client = new SampleA2aClient(URI.create("http://localhost:" + port), TIMEOUT);
        String sessionId = "session-" + UUID.randomUUID();
        List<StreamingEventKind> events = client.streamMessage("sample-user", agentId, sessionId, "ping");
        List<Message> messages = events.stream()
                .filter(Message.class::isInstance)
                .map(Message.class::cast)
                .toList();

        assertThat(events).isNotEmpty();
        assertThat(messages).anySatisfy(message -> assertThat(message.metadata().get("accepted"))
                .isEqualTo(Boolean.TRUE));
        assertThat(messages).anySatisfy(message -> assertThat(message.metadata().get("runStatus"))
                .isEqualTo("completed"));
        assertThat(messages).allSatisfy(message -> assertThat(message.role()).isEqualTo(Message.Role.ROLE_AGENT));
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
