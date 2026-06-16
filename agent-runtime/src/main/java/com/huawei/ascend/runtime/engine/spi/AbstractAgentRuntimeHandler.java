package com.huawei.ascend.runtime.engine.spi;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.springframework.util.Assert;

/**
 * Base for framework adapters that emit a standardized northbound trajectory. It owns
 * the cross-framework behavior — correlation/seq stamping, masking, the
 * {@code RUN_START → … → RUN_END} lifecycle, and capability-based graceful
 * degradation — so each adapter only maps its native events to {@link TrajectoryDraft}s
 * inside {@link #doExecute}. Extending this is optional: the {@code execute} contract
 * is unchanged, and a handler that does not extend it simply emits no trajectory.
 */
public abstract class AbstractAgentRuntimeHandler implements AgentRuntimeHandler, TrajectorySource {

    private final String agentId;

    protected AbstractAgentRuntimeHandler(String agentId) {
        Assert.hasText(agentId, "agentId must not be blank");
        this.agentId = agentId;
    }

    @Override
    public final String agentId() {
        return agentId;
    }

    @Override
    public boolean isHealthy() {
        return true;
    }

    @Override
    public final void openTrajectory(AgentExecutionContext context, TrajectorySettings settings,
            TrajectorySink sink) {
        if (settings == null || !settings.enabled() || sink == null) {
            return;
        }
        context.setTrajectoryEmitter(
                new StampingTrajectoryEmitter(sink, context.getScope(), settings, supportedKinds()));
    }

    @Override
    public final Stream<?> execute(AgentExecutionContext context) {
        TrajectoryEmitter emitter = context.getTrajectoryEmitter();
        AtomicBoolean ended = new AtomicBoolean(false);
        Runnable end = () -> {
            if (ended.compareAndSet(false, true)) {
                emitter.emit(TrajectoryDraft.runEnd());
            }
        };
        emitter.emit(TrajectoryDraft.runStart());
        try {
            Stream<?> raw = doExecute(context, emitter);
            return raw.onClose(end);
        } catch (RuntimeException e) {
            emitter.emit(TrajectoryDraft.error(null, "RUNTIME_ERROR",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    TrajectoryEvent.ErrorCategory.classify(e), null, false));
            end.run();
            throw e;
        }
    }

    /** Event kinds this adapter can produce. Defaults to the mandatory cross-framework core. */
    protected Set<Kind> supportedKinds() {
        return TrajectoryEvent.MANDATORY_KINDS;
    }

    /**
     * Run the framework agent, pushing trajectory drafts through {@code trajectory}
     * (or peeking a native stream to extract them), and return the framework-specific
     * raw result stream the runtime will adapt.
     */
    protected abstract Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory);
}
