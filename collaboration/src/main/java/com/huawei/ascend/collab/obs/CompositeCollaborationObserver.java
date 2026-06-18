package com.huawei.ascend.collab.obs;

import com.huawei.ascend.collab.core.CollaborationObserver;
import com.huawei.ascend.collab.core.CoordinationEvent;
import com.huawei.ascend.collab.core.WorkResult;
import java.util.List;

/**
 * Fans coordination callbacks out to several observers (e.g. {@link MicrometerCollaborationObserver}
 * for metrics and {@link Slf4jCollaborationObserver} for the structured trail) — the coordinator
 * takes a single observer, so this composes them. A failing delegate never breaks the others or
 * the coordinator: its exception is swallowed (observability must not crash the work).
 */
public final class CompositeCollaborationObserver implements CollaborationObserver {

    private final List<CollaborationObserver> delegates;

    public CompositeCollaborationObserver(CollaborationObserver... delegates) {
        this.delegates = List.of(delegates);
    }

    public static CompositeCollaborationObserver of(CollaborationObserver... delegates) {
        return new CompositeCollaborationObserver(delegates);
    }

    @Override
    public void onEvent(CoordinationEvent event) {
        for (CollaborationObserver d : delegates) {
            try {
                d.onEvent(event);
            } catch (RuntimeException ignored) {
                // observability is best-effort; never let it break the collaboration
            }
        }
    }

    @Override
    public void onTaskCompleted(String taskId, WorkResult.Status status, long durationMs) {
        for (CollaborationObserver d : delegates) {
            try {
                d.onTaskCompleted(taskId, status, durationMs);
            } catch (RuntimeException ignored) {
                // observability is best-effort; never let it break the collaboration
            }
        }
    }
}
