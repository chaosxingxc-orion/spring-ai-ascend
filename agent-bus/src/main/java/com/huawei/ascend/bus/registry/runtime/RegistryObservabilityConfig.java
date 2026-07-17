package com.huawei.ascend.bus.registry.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Audit + metrics facade for the agent registry MVP (ADR-0160 + design doc
 * §9.2 "审计与可观测字段").
 *
 * <p>Centralises two concerns so the controller / scheduler / discovery
 * service stay thin:
 * <ul>
 *   <li><b>Audit</b> — structured SLF4J log lines on the {@code registry.audit}
 *       logger, 11 fields per line: {@code traceId}, {@code tenantId},
 *       {@code agentId}, {@code serviceId}, {@code capability},
 *       {@code contractVersion}, {@code capabilityVersion}, {@code health},
 *       {@code routeHandleId}, {@code outcome}, {@code latencyMs}. The
 *       {@code registryOp} tag (register / deregister / probe / discover /
 *       resolve) prefixes each line. Unavailable fields are emitted as
 *       {@code "-"} so log parsers can rely on a stable 11-field shape.</li>
 *   <li><b>Metrics</b> — Micrometer {@link Counter} ({@code agent_bus_registry_op_total},
 *       tags {@code op} + {@code outcome}) and {@link Timer}
 *       ({@code agent_bus_registry_op_duration_ms}, tag {@code op}) per
 *       registry operation. {@link MeterRegistry} is constructor-injected;
 *       the {@code @Bean} fallback that supplies a
 *       {@link io.micrometer.core.instrument.simple.SimpleMeterRegistry} when
 *       no actuator is on the classpath lives in
 *       {@link RegistryPersistenceConfig#meterRegistry()} (ADR-0160 decision
 *       7, PR #389 review issue #6 — agent-bus is a runnable Spring Boot
 *       application, so it bundles micrometer-core directly instead of
 *       relying on a runtime consumer's actuator dependency).</li>
 * </ul>
 *
 * <p>Each {@code observeXxx} method emits BOTH the audit line and the
 * metric update for one operation, so callers make a single call per op
 * (no risk of audit-without-metric or vice versa).
 *
 * <p>Authority: ADR-0160 + design doc §9.2 + NFR-4 (audit) / NFR-5 (metrics).
 */
@Configuration
public class RegistryObservabilityConfig {

    private static final Logger AUDIT = LoggerFactory.getLogger("registry.audit");

    private static final String PLACEHOLDER = "-";

    private final MeterRegistry meterRegistry;

    public RegistryObservabilityConfig(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ---- audit + metrics per operation ----

    public void observeRegister(String traceId, String tenantId, String agentId, String serviceId,
                                String capability, String contractVersion, String capabilityVersion,
                                String health, String routeHandleId, String outcome, long latencyMs) {
        audit("register", traceId, tenantId, agentId, serviceId, capability,
                contractVersion, capabilityVersion, health, routeHandleId, outcome, latencyMs);
        recordMetrics("register", outcome, latencyMs);
    }

    public void observeDeregister(String traceId, String tenantId, String agentId,
                                  String outcome, long latencyMs) {
        audit("deregister", traceId, tenantId, agentId, PLACEHOLDER, PLACEHOLDER,
                PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, outcome, latencyMs);
        recordMetrics("deregister", outcome, latencyMs);
    }

    public void observeProbe(String traceId, String tenantId, String agentId,
                             String health, String outcome, long latencyMs) {
        audit("probe", traceId, tenantId, agentId, PLACEHOLDER, PLACEHOLDER,
                PLACEHOLDER, PLACEHOLDER, health, PLACEHOLDER, outcome, latencyMs);
        recordMetrics("probe", outcome, latencyMs);
    }

    public void observeDiscover(String traceId, String tenantId, String capability,
                                String contractVersion, String outcome, int resultCount, long latencyMs) {
        AUDIT.info("registryOp=discover traceId={} tenantId={} agentId={} serviceId={} capability={} "
                        + "contractVersion={} capabilityVersion={} health={} routeHandleId={} outcome={} "
                        + "latencyMs={} resultCount={}",
                traceId, tenantId, PLACEHOLDER, PLACEHOLDER,
                capability == null ? PLACEHOLDER : capability,
                contractVersion == null ? PLACEHOLDER : contractVersion,
                PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, outcome, latencyMs,
                resultCount < 0 ? PLACEHOLDER : resultCount);
        recordMetrics("discover", outcome, latencyMs);
    }

    public void observeResolve(String traceId, String tenantId, String routeHandleId,
                               String outcome, long latencyMs) {
        audit("resolve", traceId, tenantId, PLACEHOLDER, PLACEHOLDER, PLACEHOLDER,
                PLACEHOLDER, PLACEHOLDER, PLACEHOLDER, routeHandleId, outcome, latencyMs);
        recordMetrics("resolve", outcome, latencyMs);
    }

    // ---- internals ----

    private void audit(String op, String traceId, String tenantId, String agentId, String serviceId,
                       String capability, String contractVersion, String capabilityVersion,
                       String health, String routeHandleId, String outcome, long latencyMs) {
        AUDIT.info("registryOp={} traceId={} tenantId={} agentId={} serviceId={} capability={} "
                        + "contractVersion={} capabilityVersion={} health={} routeHandleId={} outcome={} "
                        + "latencyMs={}",
                op, traceId, tenantId, agentId, serviceId, capability,
                contractVersion, capabilityVersion, health, routeHandleId, outcome, latencyMs);
    }

    private void recordMetrics(String op, String outcome, long latencyMs) {
        Counter.builder("agent_bus_registry_op_total")
                .tag("op", op)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
        Timer.builder("agent_bus_registry_op_duration_ms")
                .tag("op", op)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }
}
