package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in northbound delivery of the trajectory to the A2A caller. The A2A {@link AgentEmitter}
 * is single-writer and may be touched only on the execute thread, so the drain thread merely
 * {@link #accept(TrajectoryEvent) buffers} already-masked events here; the execute thread later
 * {@link #flush(AgentEmitter, String, boolean) drains} the buffer into a dedicated
 * {@code -trajectory} artifact stream, distinct from the answer's {@code -response} stream.
 *
 * <p>Best-effort with bounded memory: at most {@value #CAPACITY} events are buffered per
 * invocation, and a full buffer sheds load rather than blocking the run. Shedding is never
 * silent toward the caller: the flushed artifact ends with a {@code {kind: "TRUNCATED",
 * droppedCount: N}} marker part so an auditing consumer can tell a complete trajectory from a
 * cut one. The last {@value #TERMINAL_RESERVE} slots are reserved for the terminal kinds
 * ({@code RUN_END}/{@code ERROR}) so an over-long run still closes with its outcome instead of
 * dropping exactly the events an auditor needs most.
 */
final class A2aNorthboundSink implements TrajectorySink {

    private static final Logger LOG = LoggerFactory.getLogger(A2aNorthboundSink.class);
    private static final int CAPACITY = 10_000;
    private static final int TERMINAL_RESERVE = 16;

    private final LinkedBlockingQueue<TrajectoryEvent> buffer = new LinkedBlockingQueue<>(CAPACITY);
    private final AtomicBoolean warnedDrop = new AtomicBoolean(false);
    private final AtomicInteger dropped = new AtomicInteger();

    @Override
    public void accept(TrajectoryEvent event) {
        // The reserve check then offer is not atomic, but the stamping emitter serializes
        // emission, so no two accepts race for the same slot within one invocation.
        boolean accepted = isTerminal(event.kind())
                ? buffer.offer(event)
                : buffer.remainingCapacity() > TERMINAL_RESERVE && buffer.offer(event);
        if (!accepted) {
            dropped.incrementAndGet();
            if (warnedDrop.compareAndSet(false, true)) {
                LOG.warn("[A2A] northbound trajectory buffer at capacity={} taskId={}; shedding further events",
                        CAPACITY, event.taskId());
            }
        }
    }

    /**
     * Drains the buffer into one closing {@code -trajectory} artifact. Execute-thread only.
     * {@code append} must be true when the task already carries this artifact from an earlier
     * leg (a continuation after a remote INPUT_REQUIRED park): the SDK replaces an existing
     * artifact on {@code append=false}, which would silently erase the parked leg's events
     * from the task snapshot.
     */
    void flush(AgentEmitter emitter, String artifactId, boolean append) {
        List<TrajectoryEvent> events = new ArrayList<>();
        buffer.drainTo(events);
        if (events.isEmpty()) {
            return;
        }
        List<Part<?>> parts = new ArrayList<>(events.size() + 1);
        for (TrajectoryEvent event : events) {
            parts.add(new DataPart(toMap(event)));
        }
        int droppedCount = dropped.getAndSet(0);
        if (droppedCount > 0) {
            parts.add(new DataPart(Map.of("kind", "TRUNCATED", "droppedCount", droppedCount)));
        }
        emitter.addArtifact(parts, artifactId, "agent-trajectory", null, append, true);
    }

    private static boolean isTerminal(TrajectoryEvent.Kind kind) {
        return kind == TrajectoryEvent.Kind.RUN_END || kind == TrajectoryEvent.Kind.ERROR;
    }

    private static Map<String, Object> toMap(TrajectoryEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("seq", e.seq());
        m.put("kind", String.valueOf(e.kind()));
        m.put("tsEpochMillis", e.tsEpochMillis());
        m.put("durationMs", e.durationMs());
        m.put("traceId", e.traceId());
        m.put("spanId", e.spanId());
        m.put("parentSpanId", e.parentSpanId());
        m.put("tenantId", e.tenantId());
        m.put("contextId", e.contextId());
        m.put("taskId", e.taskId());
        m.put("object", e.object());
        m.put("name", e.name());
        m.put("args", e.args());
        m.put("result", e.result());
        m.put("usage", e.usage());
        m.put("error", e.error());
        m.put("reasoning", e.reasoning());
        m.put("schemaVersion", e.schemaVersion());
        return m;
    }
}
