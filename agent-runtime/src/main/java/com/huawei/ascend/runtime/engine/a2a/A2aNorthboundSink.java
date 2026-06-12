package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Opt-in northbound delivery of the trajectory to the A2A caller. The A2A {@link AgentEmitter}
 * is single-writer and may be touched only on the execute thread, so the drain thread merely
 * {@link #accept(TrajectoryEvent) buffers} already-masked events here; the execute thread later
 * {@link #flush(AgentEmitter, String) drains} the buffer into a dedicated {@code -trajectory}
 * artifact stream, distinct from the answer's {@code -response} stream. Best-effort: a full
 * buffer sheds load rather than blocking the run.
 */
final class A2aNorthboundSink implements TrajectorySink {

    private static final Logger LOG = LoggerFactory.getLogger(A2aNorthboundSink.class);
    private static final int CAPACITY = 10_000;

    private final LinkedBlockingQueue<TrajectoryEvent> buffer = new LinkedBlockingQueue<>(CAPACITY);
    private final AtomicBoolean warnedDrop = new AtomicBoolean(false);

    @Override
    public void accept(TrajectoryEvent event) {
        if (!buffer.offer(event) && warnedDrop.compareAndSet(false, true)) {
            LOG.warn("[A2A] northbound trajectory buffer at capacity={}; shedding further events", CAPACITY);
        }
    }

    /** Drains the buffer into one closing {@code -trajectory} artifact. Execute-thread only. */
    void flush(AgentEmitter emitter, String artifactId) {
        List<TrajectoryEvent> events = new ArrayList<>();
        buffer.drainTo(events);
        if (events.isEmpty()) {
            return;
        }
        List<Part<?>> parts = new ArrayList<>(events.size());
        for (TrajectoryEvent event : events) {
            parts.add(new DataPart(toMap(event)));
        }
        emitter.addArtifact(parts, artifactId, "agent-trajectory", null, false, true);
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
