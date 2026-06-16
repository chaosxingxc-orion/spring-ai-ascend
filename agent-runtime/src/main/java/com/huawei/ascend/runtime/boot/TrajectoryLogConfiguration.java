package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.log.NdjsonTrajectorySink;
import com.huawei.ascend.runtime.engine.log.NdjsonTrajectorySinkFactory;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the NDJSON structured-log rail of the trajectory. Activates only when
 * {@code app.trajectory.log.enabled=true}. Unlike the OTel rail this needs no classpath probe:
 * Jackson and SLF4J are always present, so the bean signature can never trip a host without an
 * optional dependency. The exported {@link TrajectorySinkFactory} is collected by the executor
 * and added to the per-invocation sink fan-out.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.trajectory.log", name = "enabled", havingValue = "true")
class TrajectoryLogConfiguration {

    @Bean
    TrajectorySinkFactory ndjsonTrajectorySinkFactory() {
        return new NdjsonTrajectorySinkFactory(NdjsonTrajectorySink.defaultMapper());
    }
}
