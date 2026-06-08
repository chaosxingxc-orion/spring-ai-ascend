package com.huawei.ascend.examples.a2a.gateway;

import com.huawei.ascend.examples.a2a.gateway.core.InMemoryAgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.model.AgentInteractionEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAgentInteractionTelemetryTest {

    @Test
    void recordsAndFiltersEventsByTenantAndCorrelation() {
        InMemoryAgentInteractionTelemetry telemetry = new InMemoryAgentInteractionTelemetry();
        AgentInteractionEvent first = event("event-1", "tenant-a", "corr-1");
        AgentInteractionEvent second = event("event-2", "tenant-a", "corr-2");
        AgentInteractionEvent third = event("event-3", "tenant-b", "corr-1");

        telemetry.record(first);
        telemetry.record(second);
        telemetry.record(third);

        assertThat(telemetry.query("tenant-a", null, 10)).containsExactly(first, second);
        assertThat(telemetry.query("tenant-a", "corr-1", 10)).containsExactly(first);
        assertThat(telemetry.query(null, "corr-1", 10)).containsExactly(first, third);
        assertThat(telemetry.query(null, null, 2)).containsExactly(first, second);
        assertThat(telemetry.count()).isEqualTo(3);
    }

    @Test
    void keepsOnlyNewestEventsWhenBounded() {
        InMemoryAgentInteractionTelemetry telemetry = new InMemoryAgentInteractionTelemetry(2);
        AgentInteractionEvent first = event("event-1", "tenant-a", "corr-1");
        AgentInteractionEvent second = event("event-2", "tenant-a", "corr-2");
        AgentInteractionEvent third = event("event-3", "tenant-a", "corr-3");

        telemetry.record(first);
        telemetry.record(second);
        telemetry.record(third);

        assertThat(telemetry.query("tenant-a", null, 10)).containsExactly(second, third);
    }

    private static AgentInteractionEvent event(String eventId, String tenantId, String correlationId) {
        return new AgentInteractionEvent(
                eventId,
                "A2A_OUTBOUND_COMPLETED",
                Instant.parse("2026-06-05T10:00:00Z"),
                tenantId,
                "runtime-a",
                "agent-a",
                "runtime-b",
                "agent-b",
                "session-1",
                "task-1",
                correlationId,
                "trace-1",
                "grant-1",
                "message/stream",
                "COMPLETED",
                1,
                2,
                3,
                10,
                20,
                null,
                "hash",
                null,
                Map.of());
    }
}
