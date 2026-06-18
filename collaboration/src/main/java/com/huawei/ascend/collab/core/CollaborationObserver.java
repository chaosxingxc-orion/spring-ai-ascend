package com.huawei.ascend.collab.core;

/**
 * Observability hook for the coordinator — every coordination decision and each
 * task completion is reported here, so an implementation can emit metrics /
 * structured audit / spans without coupling the core engine to any backend.
 * Default is a no-op.
 */
public interface CollaborationObserver {

    void onEvent(CoordinationEvent event);

    void onTaskCompleted(String taskId, WorkResult.Status status, long durationMs);

    CollaborationObserver NOOP = new CollaborationObserver() {
        @Override
        public void onEvent(CoordinationEvent event) {
        }

        @Override
        public void onTaskCompleted(String taskId, WorkResult.Status status, long durationMs) {
        }
    };
}
