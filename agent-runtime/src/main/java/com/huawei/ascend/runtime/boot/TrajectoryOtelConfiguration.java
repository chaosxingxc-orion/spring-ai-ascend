package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.a2a.OtelSpanSinkFactory;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires OpenTelemetry span export of the trajectory. Active only when
 * {@code app.trajectory.otel.enabled=true} AND the OTel SDK is on the classpath (it is an
 * optional dependency), so a default deployment pulls in nothing and behaves exactly as
 * before. The exported {@link TrajectorySinkFactory} bean is picked up by the executor and
 * added to the per-invocation sink fan-out.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(prefix = "app.trajectory.otel", name = "enabled", havingValue = "true")
class TrajectoryOtelConfiguration {

    @Bean(destroyMethod = "close")
    OpenTelemetrySdk trajectoryOpenTelemetry(TrajectoryProperties properties) {
        OtlpGrpcSpanExporter exporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint(properties.getOtel().getEndpoint())
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .setResource(Resource.getDefault().merge(Resource.create(
                        Attributes.of(AttributeKey.stringKey("service.name"), "agent-runtime-trajectory"))))
                .build();
        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();
    }

    @Bean
    Tracer trajectoryTracer(OpenTelemetrySdk trajectoryOpenTelemetry) {
        return trajectoryOpenTelemetry.getTracer("com.huawei.ascend.runtime.trajectory");
    }

    @Bean
    TrajectorySinkFactory otelTrajectorySinkFactory(Tracer trajectoryTracer) {
        return new OtelSpanSinkFactory(trajectoryTracer);
    }
}
