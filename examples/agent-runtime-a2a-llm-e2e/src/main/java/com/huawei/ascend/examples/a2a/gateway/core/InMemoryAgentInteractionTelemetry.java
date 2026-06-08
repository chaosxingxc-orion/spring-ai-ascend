package com.huawei.ascend.examples.a2a.gateway.core;

import com.huawei.ascend.examples.a2a.gateway.api.AgentInteractionTelemetry;
import com.huawei.ascend.examples.a2a.gateway.model.AgentInteractionEvent;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryAgentInteractionTelemetry implements AgentInteractionTelemetry {

    private static final int DEFAULT_MAX_EVENTS = 10_000;

    private final int maxEvents;
    private final AtomicInteger eventCount = new AtomicInteger();
    private final ConcurrentLinkedDeque<AgentInteractionEvent> events = new ConcurrentLinkedDeque<>();

    public InMemoryAgentInteractionTelemetry() {
        this(DEFAULT_MAX_EVENTS);
    }

    public InMemoryAgentInteractionTelemetry(int maxEvents) {
        if (maxEvents <= 0) {
            throw new IllegalArgumentException("maxEvents must be positive");
        }
        this.maxEvents = maxEvents;
    }

    @Override
    public AgentInteractionEvent record(AgentInteractionEvent event) {
        AgentInteractionEvent stored = Objects.requireNonNull(event, "event");
        events.addLast(stored);
        eventCount.incrementAndGet();
        trimOldest();
        return stored;
    }

    @Override
    public List<AgentInteractionEvent> query(String tenantId, String correlationId, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return events.stream()
                .filter(event -> tenantId == null || tenantId.equals(event.tenantId()))
                .filter(event -> correlationId == null || correlationId.equals(event.correlationId()))
                .limit(limit)
                .toList();
    }

    @Override
    public long count() {
        return eventCount.get();
    }

    private void trimOldest() {
        while (eventCount.get() > maxEvents) {
            if (events.pollFirst() == null) {
                eventCount.set(0);
                return;
            }
            eventCount.decrementAndGet();
        }
    }
}
