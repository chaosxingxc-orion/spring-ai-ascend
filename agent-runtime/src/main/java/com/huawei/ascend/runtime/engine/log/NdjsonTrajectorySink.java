package com.huawei.ascend.runtime.engine.log;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEventJson;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured-log rail: writes one JSON object per {@link TrajectoryEvent} to a dedicated
 * logger ({@value #TRAJECTORY_LOGGER}) so a log agent (Fluent Bit / Filebeat) can ship the
 * NDJSON stream into ELK / Loki, correlated to the OTLP span rail by the shared
 * {@code traceId}/{@code spanId}. The payload is already masked upstream by the emitter — this
 * sink never re-masks. The field set is the canonical {@link TrajectoryEventJson} mapping,
 * identical to the A2A rail; nulls are omitted. Best-effort: a serialization fault is logged to
 * the class logger (never the trajectory stream) and swallowed, because trajectory must not
 * break the run. One instance per invocation; the dedicated logger name lets an appender target
 * exactly this stream with {@code additivity=false}.
 */
public final class NdjsonTrajectorySink implements TrajectorySink {

    /** Dedicated logger an appender binds to (with {@code additivity=false}) to isolate the NDJSON stream. */
    public static final String TRAJECTORY_LOGGER = "com.huawei.ascend.runtime.trajectory";

    private static final Logger TRAJECTORY = LoggerFactory.getLogger(TRAJECTORY_LOGGER);
    private static final Logger LOG = LoggerFactory.getLogger(NdjsonTrajectorySink.class);

    private final ObjectMapper mapper;

    public NdjsonTrajectorySink(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** A null-omitting mapper that serializes one event per line. */
    public static ObjectMapper defaultMapper() {
        return JsonMapper.builder().serializationInclusion(JsonInclude.Include.NON_NULL).build();
    }

    @Override
    public void accept(TrajectoryEvent event) {
        if (!TRAJECTORY.isInfoEnabled()) {
            return;
        }
        try {
            TRAJECTORY.info(mapper.writeValueAsString(TrajectoryEventJson.toMap(event)));
        } catch (JsonProcessingException | RuntimeException e) {
            LOG.debug("ndjson trajectory serialization failed seq={} errorClass={}",
                    event.seq(), e.getClass().getSimpleName());
        }
    }
}
