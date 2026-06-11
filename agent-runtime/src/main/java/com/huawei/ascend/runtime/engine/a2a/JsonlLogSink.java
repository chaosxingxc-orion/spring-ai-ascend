package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operator-facing sink: writes each trajectory event as one structured record to the
 * dedicated logger {@code com.huawei.ascend.runtime.trajectory}, routed to the JSONL
 * replay track. The drain thread carries the invocation's {@code contextId}/{@code taskId}
 * in MDC, so every line is correlatable to its task.
 */
final class JsonlLogSink implements TrajectorySink {

    private static final Logger TRAJECTORY_LOG = LoggerFactory.getLogger("com.huawei.ascend.runtime.trajectory");

    @Override
    public void accept(TrajectoryEvent event) {
        TRAJECTORY_LOG.info("trajectory",
                StructuredArguments.keyValue("seq", event.seq()),
                StructuredArguments.keyValue("kind", String.valueOf(event.kind())),
                StructuredArguments.keyValue("tsEpochMillis", event.tsEpochMillis()),
                StructuredArguments.keyValue("durationMs", event.durationMs()),
                StructuredArguments.keyValue("traceId", event.traceId()),
                StructuredArguments.keyValue("spanId", event.spanId()),
                StructuredArguments.keyValue("parentSpanId", event.parentSpanId()),
                StructuredArguments.keyValue("tenantId", event.tenantId()),
                StructuredArguments.keyValue("contextId", event.contextId()),
                StructuredArguments.keyValue("taskId", event.taskId()),
                StructuredArguments.keyValue("object", event.object()),
                StructuredArguments.keyValue("name", event.name()),
                StructuredArguments.keyValue("args", event.args()),
                StructuredArguments.keyValue("result", event.result()),
                StructuredArguments.keyValue("usage", event.usage()),
                StructuredArguments.keyValue("attempt", event.attempt()),
                StructuredArguments.keyValue("retryable", event.retryable()),
                StructuredArguments.keyValue("error", event.error()),
                StructuredArguments.keyValue("reasoning", event.reasoning()),
                StructuredArguments.keyValue("schemaVersion", event.schemaVersion()));
    }
}
