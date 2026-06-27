package com.huawei.ascend.examples.workmate.agent;

import com.huawei.ascend.examples.workmate.chat.RecordedRunEvent;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class RunEventBroadcaster {

    private final ConcurrentHashMap<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    public void registerRun(UUID sessionId, String runId) {
        activeRuns.put(runKey(sessionId, runId), new ActiveRun(sessionId, runId));
    }

    public void unregisterRun(UUID sessionId, String runId) {
        ActiveRun run = activeRuns.remove(runKey(sessionId, runId));
        if (run != null) {
            run.complete();
        }
    }

    public boolean isRunActive(UUID sessionId, String runId) {
        return activeRuns.containsKey(runKey(sessionId, runId));
    }

    public String latestRunId(UUID sessionId) {
        return activeRuns.values().stream()
                .filter(run -> run.sessionId.equals(sessionId))
                .map(run -> run.runId)
                .findFirst()
                .orElse(null);
    }

    public void publish(UUID sessionId, String runId, RecordedRunEvent event) {
        ActiveRun run = activeRuns.get(runKey(sessionId, runId));
        if (run == null) {
            return;
        }
        run.publish(event);
    }

    public AutoCloseable subscribe(UUID sessionId, String runId, Consumer<RecordedRunEvent> listener) {
        ActiveRun run = activeRuns.get(runKey(sessionId, runId));
        if (run == null) {
            return () -> {};
        }
        return run.subscribe(listener);
    }

    private static String runKey(UUID sessionId, String runId) {
        return sessionId + ":" + runId;
    }

    private static final class ActiveRun {
        private final UUID sessionId;
        private final String runId;
        private final CopyOnWriteArrayList<Consumer<RecordedRunEvent>> listeners = new CopyOnWriteArrayList<>();
        private volatile boolean completed;

        private ActiveRun(UUID sessionId, String runId) {
            this.sessionId = sessionId;
            this.runId = runId;
        }

        private void publish(RecordedRunEvent event) {
            for (Consumer<RecordedRunEvent> listener : listeners) {
                listener.accept(event);
            }
        }

        private AutoCloseable subscribe(Consumer<RecordedRunEvent> listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        private void complete() {
            completed = true;
            listeners.clear();
        }
    }
}
