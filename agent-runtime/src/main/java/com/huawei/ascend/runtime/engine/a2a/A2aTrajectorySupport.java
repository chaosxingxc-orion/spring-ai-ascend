package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.CompositeTrajectorySink;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import com.huawei.ascend.runtime.engine.spi.TrajectorySource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Per-invocation trajectory wiring for {@link A2aAgentExecutor}: resolves the effective
 * settings (config default plus the per-request {@code trajectory.level} override), builds
 * the sink fan-out (factory sinks plus the opt-in northbound buffer), and flushes/closes it.
 * Invoked synchronously on the execute thread only; never holds the single-writer
 * {@link AgentEmitter} as a field — it is passed in exactly where a flush may touch it.
 */
final class A2aTrajectorySupport {

    private static final Logger LOG = LoggerFactory.getLogger(A2aTrajectorySupport.class);
    private static final String TRAJECTORY_LEVEL_METADATA = "trajectory.level";
    /** Request opts into northbound trajectory delivery (a second artifact stream) by setting this true. */
    private static final String TRAJECTORY_NORTHBOUND_METADATA = "trajectory.northbound";

    private final TrajectorySettings defaultSettings;
    private final List<TrajectorySinkFactory> sinkFactories;

    A2aTrajectorySupport(TrajectorySettings defaultSettings, List<TrajectorySinkFactory> sinkFactories) {
        this.defaultSettings = defaultSettings != null ? defaultSettings : TrajectorySettings.off();
        this.sinkFactories = sinkFactories != null ? List.copyOf(sinkFactories) : List.of();
    }

    /** The per-invocation trajectory wiring: the sink fan-out and the optional northbound buffer. */
    record TrajectoryFlow(TrajectorySink sink, A2aNorthboundSink northbound) {
        static final TrajectoryFlow NONE = new TrajectoryFlow(null, null);
    }

    /**
     * Wires the trajectory for a {@link TrajectorySource} handler, which then stamps and feeds
     * events synchronously on the emitting thread. Returns {@link TrajectoryFlow#NONE} when
     * trajectory is disabled, the handler is not a source, or no sink would consume the events.
     */
    TrajectoryFlow open(RequestContext ctx, AgentExecutionContext context, AgentRuntimeHandler handler) {
        if (!(handler instanceof TrajectorySource source)) {
            return TrajectoryFlow.NONE;
        }
        TrajectorySettings settings =
                resolveSettings(A2aAgentExecutor.metadata(ctx, TRAJECTORY_LEVEL_METADATA, null));
        if (!settings.enabled()) {
            return TrajectoryFlow.NONE;
        }
        List<TrajectorySink> sinks = new ArrayList<>();
        for (TrajectorySinkFactory factory : sinkFactories) {
            sinks.add(factory.create());
        }
        A2aNorthboundSink northbound = null;
        if ("true".equalsIgnoreCase(A2aAgentExecutor.metadata(ctx, TRAJECTORY_NORTHBOUND_METADATA, "false"))) {
            northbound = new A2aNorthboundSink();
            sinks.add(northbound);
        }
        if (sinks.isEmpty()) {
            // No consumer for the events — skip stamping entirely.
            return TrajectoryFlow.NONE;
        }
        TrajectorySink sink = new CompositeTrajectorySink(sinks);
        sink.onOpen(ctx.getContextId(), ctx.getTaskId());
        source.openTrajectory(context, settings, sink);
        return new TrajectoryFlow(sink, northbound);
    }

    /**
     * When the caller opted into northbound trajectory, flush the buffered events as the
     * {@code -trajectory} artifact — on the execute thread, the only thread allowed to touch the
     * single-writer emitter. A failure here must never break the answer: it degrades to no
     * northbound trajectory.
     */
    static void deliverNorthbound(TrajectoryFlow flow, AgentEmitter emitter, String artifactId, String taskId) {
        if (flow.northbound() == null) {
            return;
        }
        try {
            flow.northbound().flush(emitter, artifactId);
        } catch (Exception e) {
            LOG.warn("[A2A] northbound trajectory delivery failed taskId={} message={}", taskId, e.getMessage());
        }
    }

    static void closeQuietly(TrajectoryFlow flow, String taskId) {
        if (flow.sink() == null) {
            return;
        }
        try {
            flow.sink().onClose();
        } catch (RuntimeException e) {
            LOG.warn("[A2A] trajectory sink close failed taskId={} message={}", taskId, e.getMessage());
        }
    }

    /**
     * The {@code trajectory.level} request metadata historically carried OFF/SUMMARY/FULL; the
     * detail tier is gone, so any value other than {@code off} means enabled (legacy senders keep
     * working) and {@code off} opts the single request out.
     */
    private TrajectorySettings resolveSettings(String requestOverride) {
        if (!defaultSettings.enabled()) {
            return TrajectorySettings.off();
        }
        if (requestOverride != null && "off".equals(requestOverride.trim().toLowerCase(Locale.ROOT))) {
            return TrajectorySettings.off();
        }
        return defaultSettings;
    }
}
