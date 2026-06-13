package com.huawei.ascend.runtime.boot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.huawei.ascend.runtime.engine.a2a.RemoteAgentCardCache;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.RemoteAgentToolSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

/**
 * First real consumer of {@link AgentRuntimeHandler#isHealthy()}: the runtime
 * health surface is UP only when the lifecycle gate is open AND every handler
 * reports healthy; a closed gate is OUT_OF_SERVICE (boot/drain window) while an
 * unhealthy handler is DOWN (it accepted the gate, then degraded).
 */
class AgentRuntimeHealthIndicatorTest {

    private final AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
    private final RuntimeReadiness readiness = new RuntimeReadiness();

    @Test
    void upWhenReadyAndEveryHandlerHealthy() {
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.isHealthy()).thenReturn(true);
        readiness.markReady();

        Health health = new AgentRuntimeHealthIndicator(List.of(handler), readiness).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("agent-x", "healthy");
    }

    @Test
    void outOfServiceWhileReadinessGateClosed() {
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.isHealthy()).thenReturn(true);

        Health health = new AgentRuntimeHealthIndicator(List.of(handler), readiness).health();

        assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void downWhenAnyHandlerReportsUnhealthy() {
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.isHealthy()).thenReturn(false);
        readiness.markReady();

        Health health = new AgentRuntimeHealthIndicator(List.of(handler), readiness).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("agent-x", "unhealthy");
    }

    @Test
    void remoteAgentCatalogStateIsReportedAsDetail() {
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.isHealthy()).thenReturn(true);
        readiness.markReady();
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-pending")) {
            @Override
            public List<RemoteAgentToolSpec> availableToolSpecs() {
                return List.of(new RemoteAgentToolSpec(
                        "remote-b", "a2a_remote_remote_b", "Remote B", Map.of()));
            }
        };

        Health health = new AgentRuntimeHealthIndicator(List.of(handler), readiness, catalog).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("remoteAgents", Map.of(
                "available", 1,
                "pending", 1,
                "pendingUrls", List.of("http://remote-pending")));
    }

    /**
     * An unreachable remote dependency must not flip the runtime DOWN: the runtime
     * still serves local executions, so all-pending remotes stay a detail only.
     */
    @Test
    void allPendingRemoteAgentsDoNotDegradeOverallStatus() {
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.isHealthy()).thenReturn(true);
        readiness.markReady();
        RemoteAgentCardCache catalog = new RemoteAgentCardCache(List.of("http://remote-pending"));

        Health health = new AgentRuntimeHealthIndicator(List.of(handler), readiness, catalog).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("remoteAgents", Map.of(
                "available", 0,
                "pending", 1,
                "pendingUrls", List.of("http://remote-pending")));
    }
}
