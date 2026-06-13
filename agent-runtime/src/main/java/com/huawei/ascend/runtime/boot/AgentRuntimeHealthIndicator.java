package com.huawei.ascend.runtime.boot;

import com.huawei.ascend.runtime.engine.a2a.RemoteAgentCardCache;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Surfaces handler health on the actuator health endpoint: OUT_OF_SERVICE
 * while the readiness gate is closed (boot or drain window), DOWN when a
 * started handler degrades, UP otherwise — with one detail entry per agent so
 * operators can see which handler is the problem. When remote A2A agents are
 * configured, their catalog state (pending/available) is reported as a detail
 * only: an unreachable remote never degrades the overall status, because the
 * runtime still serves local executions.
 */
public final class AgentRuntimeHealthIndicator implements HealthIndicator {

    private final List<AgentRuntimeHandler> handlers;
    private final RuntimeReadiness readiness;
    private final RemoteAgentCardCache remoteAgentCatalog;

    public AgentRuntimeHealthIndicator(List<AgentRuntimeHandler> handlers, RuntimeReadiness readiness) {
        this(handlers, readiness, null);
    }

    public AgentRuntimeHealthIndicator(List<AgentRuntimeHandler> handlers, RuntimeReadiness readiness,
            RemoteAgentCardCache remoteAgentCatalog) {
        this.handlers = List.copyOf(Objects.requireNonNull(handlers, "handlers"));
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.remoteAgentCatalog = remoteAgentCatalog;
    }

    @Override
    public Health health() {
        Health.Builder health = readiness.isReady() ? Health.up() : Health.outOfService();
        for (AgentRuntimeHandler handler : handlers) {
            boolean healthy = handler.isHealthy();
            health.withDetail(handler.agentId(), healthy ? "healthy" : "unhealthy");
            if (readiness.isReady() && !healthy) {
                health.down();
            }
        }
        if (remoteAgentCatalog != null) {
            List<String> pendingUrls = remoteAgentCatalog.pendingUrls();
            health.withDetail("remoteAgents", Map.of(
                    "available", remoteAgentCatalog.availableToolSpecs().size(),
                    "pending", pendingUrls.size(),
                    "pendingUrls", pendingUrls));
        }
        return health.build();
    }
}
