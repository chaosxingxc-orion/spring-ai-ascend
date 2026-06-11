package com.huawei.ascend.client.telemetry;

import com.huawei.ascend.client.SendSpec;
import java.util.Map;

/**
 * Observer seam for client-side business spans: one {@link ClientCallSpan}
 * per A2A send/stream. The default is {@link #noop()} — no spans, no OTel
 * class touched — so an unconfigured client behaves exactly as before.
 *
 * <p>No method signature on this interface references an OpenTelemetry type:
 * the always-loaded client surface must stay usable on a classpath without
 * the optional OTel dependencies. OTel types live behind
 * {@link OtelClientTelemetry} and the class-presence-guarded
 * {@link #otlpHttp} factory.
 */
public interface ClientTelemetry extends AutoCloseable {

    /**
     * Opens the span for one call.
     *
     * @param operation     {@code "send"} or {@code "stream"}; span names are
     *                      {@code a2a <operation> <agentId>}
     * @param spec          the outgoing message (routing ids plus, posture
     *                      permitting, the request text)
     * @param tenantId      the {@code X-Tenant-Id} the call authenticates as;
     *                      null when auth carries none
     * @param serverAddress host of the client's base URL; null when unknown
     */
    ClientCallSpan startCall(String operation, SendSpec spec, String tenantId,
            String serverAddress);

    /** Releases telemetry resources this instance owns; no-op by default. */
    @Override
    default void close() {
    }

    /** The do-nothing default: no spans, and no trace-context origination. */
    static ClientTelemetry noop() {
        return NoopClientTelemetry.INSTANCE;
    }

    /**
     * Self-contained OTLP/HTTP pipeline owned by the returned telemetry:
     * parent-based head sampling at the posture's ratio, batched export of
     * protobuf spans to {@code endpoint}, resource
     * {@code service.name=springai-ascend-client}. {@link #close()} flushes
     * pending spans and shuts the pipeline down — wire it to one client and
     * let {@code AscendA2aClient.close()} do it.
     *
     * <p>Requires the optional {@code opentelemetry-sdk} and
     * {@code opentelemetry-exporter-otlp} dependencies; without them this
     * factory throws, and nothing else in the SDK is affected.
     *
     * @param endpoint full OTLP/HTTP traces endpoint, e.g.
     *                 {@code http://collector:4318/v1/traces}
     * @param posture  sampling ratio + PII rule for the pipeline
     * @param headers  extra headers on every export request (e.g. collector
     *                 auth); null or empty for none
     */
    static ClientTelemetry otlpHttp(String endpoint, Posture posture, Map<String, String> headers) {
        requireOnClasspath("io.opentelemetry.sdk.OpenTelemetrySdk", "opentelemetry-sdk");
        requireOnClasspath("io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter",
                "opentelemetry-exporter-otlp");
        return OtlpClientTelemetry.create(endpoint, posture, headers);
    }

    private static void requireOnClasspath(String className, String artifactId) {
        try {
            Class.forName(className, false, ClientTelemetry.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("ClientTelemetry.otlpHttp requires the optional "
                    + "dependency io.opentelemetry:" + artifactId
                    + " on the classpath (missing class: " + className + ")", e);
        }
    }
}
