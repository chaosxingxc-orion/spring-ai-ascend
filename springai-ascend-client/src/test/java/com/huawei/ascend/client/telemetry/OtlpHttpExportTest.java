package com.huawei.ascend.client.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.client.SendSpec;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Honesty test for the self-contained OTLP/HTTP pipeline: a span recorded
 * through {@link ClientTelemetry#otlpHttp} must actually arrive at the
 * collector endpoint as a protobuf POST once the telemetry is closed
 * (close = flush + shutdown), with the configured extra headers attached.
 */
class OtlpHttpExportTest {

    private HttpServer collector;
    private final List<String> contentTypes = new CopyOnWriteArrayList<>();
    private final List<String> testHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startCollector() throws IOException {
        collector = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        collector.createContext("/v1/traces", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                contentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
                testHeaders.add(exchange.getRequestHeaders().getFirst("X-Collector-Auth"));
            }
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        collector.start();
    }

    @AfterEach
    void stopCollector() {
        collector.stop(0);
    }

    @Test
    void closedPipelineHasExportedProtobufSpansToTheCollector() {
        String endpoint = "http://localhost:" + collector.getAddress().getPort() + "/v1/traces";
        ClientTelemetry telemetry = ClientTelemetry.otlpHttp(
                endpoint, Posture.DEV, Map.of("X-Collector-Auth", "secret-1"));

        ClientCallSpan span = telemetry.startCall(
                "send", SendSpec.of("agent-1", "session-1", "user-1", "hello"),
                "tenant-1", "localhost");
        assertThat(span.traceparent()).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01");
        span.succeed(true, "world");
        telemetry.close();

        assertThat(contentTypes).isNotEmpty();
        assertThat(contentTypes)
                .allSatisfy(contentType -> assertThat(contentType)
                        .startsWith("application/x-protobuf"));
        assertThat(testHeaders).contains("secret-1");
    }
}
