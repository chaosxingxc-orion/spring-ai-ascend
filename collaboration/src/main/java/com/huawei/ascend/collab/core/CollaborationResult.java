package com.huawei.ascend.collab.core;

import java.util.List;
import java.util.Map;

/**
 * The outcome of running a set of sub-tasks through the coordinator: each task's
 * final status, plus the full decision log (dispatch / hand-over / reclaim /
 * validate / token-reject / complete).
 */
public record CollaborationResult(
        Map<String, WorkResult.Status> outcomes,
        List<CoordinationEvent> log) {

    public boolean allCompleted() {
        return !outcomes.isEmpty()
                && outcomes.values().stream().allMatch(s -> s == WorkResult.Status.COMPLETED);
    }

    public long count(WorkResult.Status status) {
        return outcomes.values().stream().filter(s -> s == status).count();
    }

    public long countEvents(CoordinationEvent.Type type) {
        return log.stream().filter(e -> e.type() == type).count();
    }
}
