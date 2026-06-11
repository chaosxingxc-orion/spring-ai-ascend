package com.huawei.ascend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.huawei.ascend.client.telemetry.ClientCallSpan;
import com.huawei.ascend.client.telemetry.ClientTelemetry;
import com.huawei.ascend.client.telemetry.OtelClientTelemetry;
import com.huawei.ascend.client.telemetry.Posture;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The observability contract of the client with an OTel-backed telemetry:
 * every call is one local CLIENT span with the business attribution, the
 * OUTBOUND traceparent header carries that span's trace-id (wire/span
 * coherence), the server's traceresponse lands both on the result and as a
 * span attribute, and message text obeys the posture's PII rule structurally.
 */
class OtelClientTelemetryTest {

    private static final AttributeKey<String> A2A_AGENT_ID =
            AttributeKey.stringKey("a2a.agent_id");
    private static final AttributeKey<String> A2A_SESSION_ID =
            AttributeKey.stringKey("a2a.session_id");
    private static final AttributeKey<String> A2A_USER_ID =
            AttributeKey.stringKey("a2a.user_id");
    private static final AttributeKey<String> A2A_MESSAGE_ID =
            AttributeKey.stringKey("a2a.message_id");
    private static final AttributeKey<String> TENANT_ID =
            AttributeKey.stringKey("tenant.id");
    private static final AttributeKey<String> SERVER_ADDRESS =
            AttributeKey.stringKey("server.address");
    private static final AttributeKey<String> A2A_REQUEST_TEXT =
            AttributeKey.stringKey("a2a.request.text");
    private static final AttributeKey<String> A2A_RESPONSE_TEXT =
            AttributeKey.stringKey("a2a.response.text");
    private static final AttributeKey<Boolean> A2A_TERMINAL =
            AttributeKey.booleanKey("a2a.terminal");
    private static final AttributeKey<String> A2A_TRACERESPONSE =
            AttributeKey.stringKey("a2a.traceresponse");
    private static final AttributeKey<String> ERROR_TYPE =
            AttributeKey.stringKey("error.type");

    private StubA2aServer server;
    private InMemorySpanExporter exporter;
    private OpenTelemetrySdk otel;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubA2aServer();
        exporter = InMemorySpanExporter.create();
        otel = OpenTelemetrySdk.builder()
                .setTracerProvider(SdkTracerProvider.builder()
                        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                        .build())
                .build();
    }

    @AfterEach
    void tearDown() {
        server.close();
        otel.close();
    }

    @Test
    void sendTextCapturesClientSpanAndPropagatesItsTraceContextOnTheWire() throws Exception {
        SendSpec spec = SendSpec.of("stub-agent", "session-1", "user-1", "ping");
        A2aResponse response;
        try (AscendA2aClient client = newClient(Posture.DEV)) {
            response = client.sendText(spec);
        }

        SpanData span = singleSpan("a2a send stub-agent");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getInstrumentationScopeInfo().getName())
                .isEqualTo(OtelClientTelemetry.TRACER_NAME);
        assertThat(span.getAttributes().get(A2A_AGENT_ID)).isEqualTo("stub-agent");
        assertThat(span.getAttributes().get(A2A_SESSION_ID)).isEqualTo("session-1");
        assertThat(span.getAttributes().get(A2A_USER_ID)).isEqualTo("user-1");
        assertThat(span.getAttributes().get(A2A_MESSAGE_ID)).isEqualTo(spec.messageId());
        assertThat(span.getAttributes().get(TENANT_ID)).isEqualTo("tenant-1");
        assertThat(span.getAttributes().get(SERVER_ADDRESS)).isEqualTo("localhost");
        assertThat(span.getAttributes().get(A2A_TERMINAL)).isTrue();

        // Wire/span coherence: the traceparent header that crossed the wire is
        // derived from THIS span's context, not an unrelated random mint.
        String expectedTraceparent =
                "00-" + span.getTraceId() + "-" + span.getSpanId() + "-01";
        assertThat(server.recordedHeaders("/a2a").get("traceparent"))
                .isEqualTo(expectedTraceparent);
        assertThat(response.trace().traceparent()).isEqualTo(expectedTraceparent);

        // traceresponse correlation, surfaced both ways.
        assertThat(response.trace().traceresponse()).isEqualTo(StubA2aServer.TRACERESPONSE);
        assertThat(span.getAttributes().get(A2A_TRACERESPONSE))
                .isEqualTo(StubA2aServer.TRACERESPONSE);
    }

    @Test
    void streamTextCapturesStreamSpanWithCoherentTraceparent() throws Exception {
        SendSpec spec = SendSpec.of("stub-agent", "session-1", "user-1", "ping");
        A2aResponse response;
        try (AscendA2aClient client = newClient(Posture.DEV)) {
            response = client.streamText(spec);
        }

        SpanData span = singleSpan("a2a stream stub-agent");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(A2A_TERMINAL)).isTrue();
        assertThat(span.getAttributes().get(A2A_TRACERESPONSE))
                .isEqualTo(StubA2aServer.TRACERESPONSE);
        assertThat(response.trace().traceparent())
                .isEqualTo("00-" + span.getTraceId() + "-" + span.getSpanId() + "-01");
        assertThat(server.recordedHeaders("/a2a").get("traceparent"))
                .isEqualTo(response.trace().traceparent());
    }

    @Test
    void devPostureAttachesRequestAndResponseText() throws Exception {
        try (AscendA2aClient client = newClient(Posture.DEV)) {
            client.sendText(SendSpec.of("stub-agent", "session-1", "user-1", "ping"));
        }

        SpanData span = singleSpan("a2a send stub-agent");
        assertThat(span.getAttributes().get(A2A_REQUEST_TEXT)).isEqualTo("ping");
        assertThat(span.getAttributes().get(A2A_RESPONSE_TEXT)).isEqualTo("pong");
    }

    @Test
    void prodPostureNeverAttachesMessageText() throws Exception {
        try (AscendA2aClient client = newClient(Posture.PROD)) {
            client.sendText(SendSpec.of("stub-agent", "session-1", "user-1", "ping"));
        }

        SpanData span = singleSpan("a2a send stub-agent");
        // Structural redaction: the PII attributes are never set in PROD,
        // while the non-PII attribution stays intact.
        assertThat(span.getAttributes().get(A2A_REQUEST_TEXT)).isNull();
        assertThat(span.getAttributes().get(A2A_RESPONSE_TEXT)).isNull();
        assertThat(span.getAttributes().get(A2A_AGENT_ID)).isEqualTo("stub-agent");
        assertThat(span.getAttributes().get(A2A_TERMINAL)).isTrue();
    }

    @Test
    void failedSendEndsSpanWithErrorTypeAndHonestTerminalFlag() throws Exception {
        server.failJsonRpcWithHttp500();
        Throwable thrown;
        try (AscendA2aClient client = newClient(Posture.DEV)) {
            thrown = catchThrowable(() ->
                    client.sendText(SendSpec.of("stub-agent", "session-1", "user-1", "ping")));
        }

        assertThat(thrown).isNotNull();
        SpanData span = singleSpan("a2a send stub-agent");
        assertThat(span.getAttributes().get(ERROR_TYPE))
                .isEqualTo(thrown.getClass().getName());
        assertThat(span.getAttributes().get(A2A_TERMINAL)).isFalse();
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    }

    @Test
    void noopTelemetryOriginatesNoTraceContextAndIsInert() {
        ClientCallSpan span = ClientTelemetry.noop()
                .startCall("send", SendSpec.of("a", "s", "u", "t"), null, null);
        assertThat(span.traceparent()).isNull();
        span.traceresponse(StubA2aServer.TRACERESPONSE);
        span.succeed(true, "pong");
        span.fail(new IllegalStateException("ignored"));
    }

    private AscendA2aClient newClient(Posture posture) {
        return AscendA2aClient.builder()
                .baseUrl(server.baseUrl())
                .timeout(Duration.ofSeconds(10))
                .auth(ClientAuth.jwtBearer(() -> "token-123", "tenant-1"))
                .telemetry(OtelClientTelemetry.create(otel, posture))
                .build();
    }

    /** The one business span of the test's single call. */
    private SpanData singleSpan(String name) {
        return exporter.getFinishedSpanItems().stream()
                .filter(span -> span.getName().equals(name))
                .reduce((first, second) -> {
                    throw new AssertionError("more than one span named " + name);
                })
                .orElseThrow(() -> new AssertionError(
                        "no span named " + name + " in " + exporter.getFinishedSpanItems()));
    }
}
