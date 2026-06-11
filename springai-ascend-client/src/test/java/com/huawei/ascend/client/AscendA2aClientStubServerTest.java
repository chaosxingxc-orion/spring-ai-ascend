package com.huawei.ascend.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.regex.Pattern;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Wire-level contract of the facade against a stub A2A server: the auth and
 * trace headers must actually be SENT (not just configured), the server's
 * {@code traceresponse} must surface on the result, and send/stream must
 * extract the user-visible text. No telemetry is configured here, so these
 * tests also pin the unchanged default-path behavior.
 */
class AscendA2aClientStubServerTest {

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");

    private StubA2aServer server;

    @BeforeEach
    void startStubServer() throws IOException {
        server = new StubA2aServer();
    }

    @AfterEach
    void stopStubServer() {
        server.close();
    }

    @Test
    void sendTextSendsAuthAndTraceHeadersAndSurfacesTraceresponseAndText() throws Exception {
        try (AscendA2aClient client = newClient()) {
            A2aResponse response = client.sendText(
                    SendSpec.of("stub-agent", "session-1", "user-1", "ping"));

            assertThat(response.text()).isEqualTo("pong");
            assertThat(response.events()).hasSize(1);
            assertThat(response.trace().traceresponse()).isEqualTo(StubA2aServer.TRACERESPONSE);

            Map<String, String> wire = server.recordedHeaders("/a2a");
            assertThat(wire.get("authorization")).isEqualTo("Bearer token-123");
            assertThat(wire.get("x-tenant-id")).isEqualTo("tenant-1");
            assertThat(wire.get("traceparent")).matches(TRACEPARENT_PATTERN);
            // The surfaced traceparent is the one that actually crossed the wire.
            assertThat(response.trace().traceparent()).isEqualTo(wire.get("traceparent"));
        }
    }

    @Test
    void streamTextCompletesOnTerminalEventAndExcludesAcceptedAck() throws Exception {
        try (AscendA2aClient client = newClient()) {
            A2aResponse response = client.streamText(
                    SendSpec.of("stub-agent", "session-1", "user-1", "ping"));

            assertThat(response.events()).hasSize(2);
            assertThat(response.events())
                    .anySatisfy(event -> assertThat(A2aEvents.isTerminal(event)).isTrue());
            assertThat(response.text()).isEqualTo("pong");
            assertThat(response.trace().traceresponse()).isEqualTo(StubA2aServer.TRACERESPONSE);

            Map<String, String> wire = server.recordedHeaders("/a2a");
            assertThat(wire.get("authorization")).isEqualTo("Bearer token-123");
            assertThat(wire.get("x-tenant-id")).isEqualTo("tenant-1");
            assertThat(response.trace().traceparent()).isEqualTo(wire.get("traceparent"));
        }
    }

    @Test
    void agentCardIsFetchedWithAuthAndTraceHeaders() throws Exception {
        try (AscendA2aClient client = newClient()) {
            AgentCard card = client.agentCard();

            assertThat(card.name()).isEqualTo("stub-agent");
            assertThat(card.capabilities().streaming()).isTrue();

            Map<String, String> wire = server.recordedHeaders("/.well-known/agent-card.json");
            assertThat(wire.get("authorization")).isEqualTo("Bearer token-123");
            assertThat(wire.get("x-tenant-id")).isEqualTo("tenant-1");
            assertThat(wire.get("traceparent")).matches(TRACEPARENT_PATTERN);
        }
    }

    private AscendA2aClient newClient() {
        return AscendA2aClient.builder()
                .baseUrl(server.baseUrl())
                .timeout(Duration.ofSeconds(10))
                .auth(ClientAuth.jwtBearer(() -> "token-123", "tenant-1"))
                .build();
    }
}
