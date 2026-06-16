package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * The NDJSON structured-log rail registers its sink factory only under an explicit
 * {@code app.trajectory.log.enabled=true}; with no opt-in nothing is wired.
 */
class TrajectoryLogConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, TrajectoryLogConfiguration.class);

    @Test
    void enabledRegistersTheNdjsonSinkFactory() {
        runner.withPropertyValues("app.trajectory.log.enabled=true")
                .run(ctx -> assertThat(ctx).hasSingleBean(TrajectorySinkFactory.class));
    }

    @Test
    void disabledRegistersNothing() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(TrajectorySinkFactory.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TrajectoryProperties.class)
    static class PropertiesConfiguration {
    }
}
