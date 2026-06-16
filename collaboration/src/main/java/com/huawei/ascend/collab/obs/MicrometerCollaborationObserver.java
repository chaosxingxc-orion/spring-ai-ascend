package com.huawei.ascend.collab.obs;

import com.huawei.ascend.collab.core.CollaborationObserver;
import com.huawei.ascend.collab.core.CoordinationEvent;
import com.huawei.ascend.collab.core.WorkResult;
import io.micrometer.core.instrument.Metrics;
import java.time.Duration;

/**
 * Emits collaboration metrics via Micrometer's global registry (surfaces at
 * {@code /actuator/prometheus} when a registry is bound; harmless no-op in the
 * standalone eval). Low-cardinality tags only — outcome / event-type / capability.
 *
 * <ul>
 *   <li>{@code collab.tasks{outcome}} — completed task count by final status</li>
 *   <li>{@code collab.task.latency} — per-task coordination latency</li>
 *   <li>{@code collab.events{type}} — coordination decisions (dispatch/handover/reclaim/...)</li>
 * </ul>
 */
public final class MicrometerCollaborationObserver implements CollaborationObserver {

    @Override
    public void onEvent(CoordinationEvent event) {
        Metrics.counter("collab.events", "type", event.type().name()).increment();
    }

    @Override
    public void onTaskCompleted(String taskId, WorkResult.Status status, long durationMs) {
        Metrics.counter("collab.tasks", "outcome", status.name()).increment();
        Metrics.timer("collab.task.latency").record(Duration.ofMillis(durationMs));
    }
}
