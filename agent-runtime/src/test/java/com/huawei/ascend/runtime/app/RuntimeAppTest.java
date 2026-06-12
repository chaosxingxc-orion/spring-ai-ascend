package com.huawei.ascend.runtime.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Verifies the framework-neutral {@link RuntimeApp} contract: it hands the handler to the
 * {@link RuntimeHost} as {@link RuntimeComponents} and returns the host's {@link RunningRuntime}.
 * This is the Spring-free seam that keeps the runtime core embeddable; the real Spring boot through
 * {@link LocalA2aRuntimeHost} is exercised by the {@code agent-runtime-a2a-llm-e2e} example.
 */
class RuntimeAppTest {

    @Test
    void runHandsHandlerToHostAndReturnsItsRuntime() {
        AgentRuntimeHandler handler = new StubHandler();
        AtomicReference<RuntimeComponents> seen = new AtomicReference<>();
        RunningRuntime started = new RunningRuntime() {
            @Override
            public int port() {
                return 4242;
            }

            @Override
            public void close() {
            }
        };
        RuntimeHost host = components -> {
            seen.set(components);
            return started;
        };

        RunningRuntime result = RuntimeApp.create(handler).run(host);

        assertThat(result).isSameAs(started);
        assertThat(result.port()).isEqualTo(4242);
        assertThat(seen.get().handler()).isSameAs(handler);
    }

    @Test
    void localHostPortZeroOverridesConfiguredServerPort() throws IOException {
        try (ServerSocket configuredPortSocket = new ServerSocket(0)) {
            int configuredPort = configuredPortSocket.getLocalPort();
            LocalA2aRuntimeHost host = new LocalA2aRuntimeHost(0, Map.of("server.port", configuredPort));

            try (RunningRuntime runtime = RuntimeApp.create(new StubHandler()).run(host)) {
                assertThat(runtime.port()).isPositive();
                assertThat(runtime.port()).isNotEqualTo(configuredPort);
            }
        }
    }

    /**
     * The host-registered handler (a manual singleton, not a component-scanned
     * bean) must still ride the runtime lifecycle: started before the runtime
     * serves and stopped when it closes.
     */
    @Test
    void localHostDrivesHandlerLifecycleAroundServing() {
        LifecycleRecordingHandler handler = new LifecycleRecordingHandler();
        LocalA2aRuntimeHost host = new LocalA2aRuntimeHost(0, Map.of());

        try (RunningRuntime runtime = RuntimeApp.create(handler).run(host)) {
            assertThat(runtime.port()).isPositive();
            assertThat(handler.started).isTrue();
            assertThat(handler.stopped).isFalse();
        }
        assertThat(handler.stopped).isTrue();
    }

    /**
     * The host defaults {@code server.forward-headers-strategy=framework}, so a
     * runtime behind a reverse proxy publishes card URLs from X-Forwarded-* instead
     * of its local bind address. Proven end to end: a card request carrying the
     * forwarded headers must come back with the proxy's base URL.
     */
    @Test
    void localHostHonorsForwardedHeadersOnTheCardEndpointByDefault() throws IOException, InterruptedException {
        LocalA2aRuntimeHost host = new LocalA2aRuntimeHost(0, Map.of());

        try (RunningRuntime runtime = RuntimeApp.create(new StubHandler()).run(host)) {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + runtime.port() + "/.well-known/agent-card.json"))
                    .header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Host", "edge.example.com")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("https://edge.example.com/a2a");
            assertThat(response.body()).doesNotContain("localhost:" + runtime.port());
        }
    }

    private static final class LifecycleRecordingHandler extends StubHandler {
        private volatile boolean started;
        private volatile boolean stopped;

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private static class StubHandler implements AgentRuntimeHandler {
        @Override
        public String agentId() {
            return "stub";
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            return Stream.of(Map.of("result_type", "answer", "output", "ok"));
        }

        @Override
        public StreamAdapter resultAdapter() {
            return rawResults -> rawResults.map(raw -> AgentExecutionResult.completed("ok"));
        }
    }
}
