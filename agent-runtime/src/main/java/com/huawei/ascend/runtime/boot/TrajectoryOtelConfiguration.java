package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.otel.OtelSpanSinkFactory;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

/**
 * Wires OpenTelemetry span export of the trajectory. Export activates only when
 * {@code app.trajectory.otel.enabled=true} AND the OTel SDK + OTLP exporter are on the
 * classpath (both optional dependencies), so a default deployment pulls in nothing and
 * behaves exactly as before. The condition checks the SDK/exporter classes — not the
 * api-only {@code OpenTelemetry} class, which commonly rides in transitively without the
 * SDK and would let Spring trip over the unresolvable {@code @Bean} signatures below.
 * The exported {@link TrajectorySinkFactory} bean is picked up by the executor and added
 * to the per-invocation sink fan-out.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.trajectory.otel", name = "enabled", havingValue = "true")
class TrajectoryOtelConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(TrajectoryOtelConfiguration.class);

    static final String OTEL_SDK_CLASS = "io.opentelemetry.sdk.OpenTelemetrySdk";
    static final String OTEL_EXPORTER_CLASS = "io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter";

    /**
     * An explicit opt-in must never back off silently: when the SDK/exporter classes are
     * missing, the nested export configuration cannot load at all, so the signal lives on
     * this property-only-conditioned shell and probes the classpath itself.
     */
    @Bean
    InitializingBean trajectoryOtelClasspathSignal(ResourceLoader resourceLoader) {
        return () -> {
            ClassLoader classLoader = resourceLoader.getClassLoader();
            List<String> missing = Stream.of(OTEL_SDK_CLASS, OTEL_EXPORTER_CLASS)
                    .filter(name -> !ClassUtils.isPresent(name, classLoader))
                    .toList();
            if (!missing.isEmpty()) {
                LOG.warn("app.trajectory.otel.enabled=true but {} missing from the classpath;"
                                + " trajectory span export stays OFF (add the optional"
                                + " io.opentelemetry:opentelemetry-sdk and"
                                + " io.opentelemetry:opentelemetry-exporter-otlp dependencies)",
                        missing);
            }
        };
    }

    /**
     * Isolated in a nested class because the {@code @Bean} method signatures reference
     * SDK/exporter types: introspecting them on a host without those classes would throw
     * NoClassDefFoundError before any condition could help. The conditions are repeated
     * here (not inherited) because classpath scanning registers nested configuration
     * classes independently of the enclosing class — the outer guard does not cascade.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "app.trajectory.otel", name = "enabled", havingValue = "true")
    @ConditionalOnClass(name = {OTEL_SDK_CLASS, OTEL_EXPORTER_CLASS})
    static class OtelSpanExportConfiguration {

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
}
