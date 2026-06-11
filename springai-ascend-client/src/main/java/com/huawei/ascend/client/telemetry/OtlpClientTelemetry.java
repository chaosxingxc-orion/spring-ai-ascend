package com.huawei.ascend.client.telemetry;

import com.huawei.ascend.client.SendSpec;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * The pipeline behind {@link ClientTelemetry#otlpHttp}: an
 * {@link OpenTelemetrySdk} this class OWNS — parent-based head sampling at
 * the posture ratio, batch processor, OTLP/HTTP protobuf exporter, resource
 * {@code service.name=springai-ascend-client}. Span semantics are delegated
 * to {@link OtelClientTelemetry}; {@link #close()} flushes and shuts the
 * owned SDK down (unlike a consumer-supplied SDK, which the client must
 * never shut down).
 */
final class OtlpClientTelemetry implements ClientTelemetry {

    static final String SERVICE_NAME = "springai-ascend-client";

    private static final long CLOSE_FLUSH_TIMEOUT_SECONDS = 10;

    private final OpenTelemetrySdk sdk;
    private final OtelClientTelemetry delegate;

    private OtlpClientTelemetry(OpenTelemetrySdk sdk, OtelClientTelemetry delegate) {
        this.sdk = sdk;
        this.delegate = delegate;
    }

    static ClientTelemetry create(String endpoint, Posture posture, Map<String, String> headers) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be null or blank");
        }
        Objects.requireNonNull(posture, "posture");
        OtlpHttpSpanExporterBuilder exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(endpoint);
        if (headers != null) {
            headers.forEach(exporter::addHeader);
        }
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setSampler(sampler(posture))
                .setResource(Resource.getDefault().toBuilder()
                        .put(AttributeKey.stringKey("service.name"), SERVICE_NAME)
                        .build())
                .addSpanProcessor(BatchSpanProcessor.builder(exporter.build()).build())
                .build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
        return new OtlpClientTelemetry(sdk, OtelClientTelemetry.create(sdk, posture));
    }

    /** Parent-based ratio sampling: the posture decides root spans only. */
    static Sampler sampler(Posture posture) {
        return Sampler.parentBased(Sampler.traceIdRatioBased(posture.samplingRatio()));
    }

    @Override
    public ClientCallSpan startCall(String operation, SendSpec spec, String tenantId,
            String serverAddress) {
        return delegate.startCall(operation, spec, tenantId, serverAddress);
    }

    @Override
    public void close() {
        sdk.getSdkTracerProvider().forceFlush()
                .join(CLOSE_FLUSH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        sdk.close();
    }
}
