package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The OTel export condition must gate on the classes its bean signatures actually need
 * (SDK + OTLP exporter), not on the api-only marker class that commonly rides in
 * transitively — and an explicit {@code enabled=true} must never back off silently.
 */
class TrajectoryOtelConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, TrajectoryOtelConfiguration.class);

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void attachAppender() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TrajectoryOtelConfiguration.class);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    /**
     * api-only classpath (the SDK class is missing) with an explicit opt-in: the context
     * must still boot — no bean method signature may trip over the missing classes — with
     * no OTel beans, and the back-off must be announced as a WARN.
     */
    @Test
    void apiOnlyClasspathWithExplicitEnable_backsOffLoudlyInsteadOfFailing() {
        runner.withPropertyValues("app.trajectory.otel.enabled=true")
                .withClassLoader(new FilteredClassLoader(OpenTelemetrySdk.class))
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(TrajectorySinkFactory.class);
                    assertThat(ctx).doesNotHaveBean("trajectoryOpenTelemetry");
                    assertThat(appender.list).anySatisfy(event -> {
                        assertThat(event.getLevel()).isEqualTo(Level.WARN);
                        assertThat(event.getFormattedMessage())
                                .contains("io.opentelemetry.sdk.OpenTelemetrySdk")
                                .contains("trajectory span export stays OFF");
                    });
                });
    }

    /** With the full optional OTel dependency set present, enabling wires the export beans. */
    @Test
    void fullClasspathWithExplicitEnable_createsExportBeans() {
        runner.withPropertyValues("app.trajectory.otel.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OpenTelemetrySdk.class);
                    assertThat(ctx).hasSingleBean(TrajectorySinkFactory.class);
                    assertThat(appender.list)
                            .noneSatisfy(event -> assertThat(event.getLevel()).isEqualTo(Level.WARN));
                });
    }

    /** Without the opt-in nothing registers and nothing warns, whatever the classpath. */
    @Test
    void disabled_registersNothingAndStaysQuiet() {
        runner.run(ctx -> {
            assertThat(ctx).doesNotHaveBean(TrajectorySinkFactory.class);
            assertThat(ctx).doesNotHaveBean("trajectoryOtelClasspathSignal");
            assertThat(appender.list).isEmpty();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TrajectoryProperties.class)
    static class PropertiesConfiguration {
    }
}
