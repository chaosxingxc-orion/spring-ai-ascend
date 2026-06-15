package com.huawei.ascend.runtime.engine.spi;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The canonical northbound field mapping of a {@link TrajectoryEvent}: one ordered map per
 * event, shared by every wire rail (the A2A {@code DataPart} stream and the NDJSON structured
 * log) so the two never drift. {@code attempt}/{@code retryable} remain on the Java record but
 * are intentionally NOT serialized northbound. This is the single source of the
 * "Northbound trajectory wire contract" field set in {@code docs/contracts/contract-catalog.md}.
 */
public final class TrajectoryEventJson {

    private TrajectoryEventJson() {
    }

    public static Map<String, Object> toMap(TrajectoryEvent e) {
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
        m.put("finishReason", e.finishReason());
        m.put("parentTaskId", e.parentTaskId());
        m.put("parentTraceId", e.parentTraceId());
        m.put("schemaVersion", e.schemaVersion());
        return m;
    }
}
