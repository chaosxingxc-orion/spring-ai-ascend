package com.huawei.ascend.client.telemetry;

import com.huawei.ascend.client.SendSpec;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.Objects;

/**
 * OpenTelemetry-backed {@link ClientTelemetry} over a consumer-supplied
 * {@link OpenTelemetry}: each A2A call becomes a CLIENT span named
 * {@code a2a send <agentId>} / {@code a2a stream <agentId>} carrying the
 * routing attribution ({@code a2a.*}, {@code tenant.id},
 * {@code server.address}), and the outbound {@code traceparent} is derived
 * from that span's context so wire trace and local span share one trace-id.
 *
 * <p>Message text is attached only when the {@link Posture} allows PII on
 * telemetry; in {@link Posture#PROD} the text attributes are never set.
 * Lifecycle of the supplied SDK stays with the consumer: {@link #close()}
 * here is a no-op.
 */
public final class OtelClientTelemetry implements ClientTelemetry {

    public static final String TRACER_NAME = "springai-ascend-client";

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

    private final Tracer tracer;
    private final Posture posture;

    private OtelClientTelemetry(Tracer tracer, Posture posture) {
        this.tracer = tracer;
        this.posture = posture;
    }

    public static OtelClientTelemetry create(OpenTelemetry openTelemetry, Posture posture) {
        Objects.requireNonNull(openTelemetry, "openTelemetry");
        Objects.requireNonNull(posture, "posture");
        return new OtelClientTelemetry(openTelemetry.getTracer(TRACER_NAME), posture);
    }

    @Override
    public ClientCallSpan startCall(String operation, SendSpec spec, String tenantId,
            String serverAddress) {
        SpanBuilder builder = tracer.spanBuilder("a2a " + operation + " " + spec.agentId())
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute(A2A_AGENT_ID, spec.agentId())
                .setAttribute(A2A_SESSION_ID, spec.sessionId())
                .setAttribute(A2A_USER_ID, spec.userId())
                .setAttribute(A2A_MESSAGE_ID, spec.messageId());
        if (tenantId != null) {
            builder.setAttribute(TENANT_ID, tenantId);
        }
        if (serverAddress != null) {
            builder.setAttribute(SERVER_ADDRESS, serverAddress);
        }
        if (posture.allowsPiiOnTelemetry()) {
            builder.setAttribute(A2A_REQUEST_TEXT, spec.text());
        }
        return new OtelCallSpan(builder.startSpan(), posture);
    }

    private static final class OtelCallSpan implements ClientCallSpan {

        private final Span span;
        private final Posture posture;

        private OtelCallSpan(Span span, Posture posture) {
            this.span = span;
            this.posture = posture;
        }

        @Override
        public String traceparent() {
            SpanContext context = span.getSpanContext();
            if (!context.isValid()) {
                return null;
            }
            return "00-" + context.getTraceId() + "-" + context.getSpanId()
                    + "-" + context.getTraceFlags().asHex();
        }

        @Override
        public void traceresponse(String traceresponse) {
            if (traceresponse != null) {
                span.setAttribute(A2A_TRACERESPONSE, traceresponse);
            }
        }

        @Override
        public void succeed(boolean terminal, String responseText) {
            span.setAttribute(A2A_TERMINAL, terminal);
            if (posture.allowsPiiOnTelemetry() && responseText != null && !responseText.isBlank()) {
                span.setAttribute(A2A_RESPONSE_TEXT, responseText);
            }
            span.end();
        }

        @Override
        public void fail(Throwable error) {
            span.setAttribute(A2A_TERMINAL, false);
            span.setAttribute(ERROR_TYPE, error.getClass().getName());
            span.setStatus(StatusCode.ERROR, String.valueOf(error.getMessage()));
            span.end();
        }
    }
}
