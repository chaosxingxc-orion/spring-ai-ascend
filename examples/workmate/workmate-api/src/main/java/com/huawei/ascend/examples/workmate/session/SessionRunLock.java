package com.huawei.ascend.examples.workmate.session;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SessionRunLock {

    private final ConcurrentHashMap<UUID, String> activeRuns = new ConcurrentHashMap<>();

    public boolean tryAcquire(UUID sessionId, String runId) {
        return activeRuns.putIfAbsent(sessionId, runId) == null;
    }

    public void release(UUID sessionId, String runId) {
        activeRuns.computeIfPresent(sessionId, (id, current) -> current.equals(runId) ? null : current);
    }

    public boolean isLocked(UUID sessionId) {
        return activeRuns.containsKey(sessionId);
    }
}
