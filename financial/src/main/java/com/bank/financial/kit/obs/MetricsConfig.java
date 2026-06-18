package com.bank.financial.kit.obs;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;

/**
 * Binds Spring's application {@link MeterRegistry} into Micrometer's static
 * {@link Metrics#globalRegistry}, so {@code ObservabilityRail} (a plain object,
 * not a Spring bean) can record metrics via the static API and have them surface
 * at {@code /actuator/prometheus}. In the playground (no Spring) the global
 * registry has no backend, so the same calls are harmless no-ops.
 */
@Configuration(proxyBeanMethods = false)
public class MetricsConfig {

    public MetricsConfig(ObjectProvider<MeterRegistry> registry) {
        registry.ifAvailable(Metrics.globalRegistry::add);
    }
}
