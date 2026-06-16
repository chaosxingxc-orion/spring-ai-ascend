package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.ErrorCategory;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent.Kind;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Base for AgentScope adapters. Extends {@link AbstractAgentRuntimeHandler} so AgentScope
 * agents emit the same standardized northbound trajectory as every other framework: the base
 * owns the {@code RUN_START → … → RUN_END} lifecycle plus correlation/span stamping, while this
 * peeks AgentScope's native realized stream to surface the inner steps (OUTPUT→PROGRESS,
 * FAILED→ERROR). The peek is pure observation — it never alters the stream the runtime adapts.
 */
abstract class AbstractAgentScopeRuntimeHandler extends AbstractAgentRuntimeHandler {

    private final AgentScopeMessageAdapter messageAdapter;
    private final AgentScopeStreamAdapter streamAdapter;

    AbstractAgentScopeRuntimeHandler(String agentId, String name, String description) {
        this(agentId, name, description, new AgentScopeMessageAdapter(), new AgentScopeStreamAdapter());
    }

    AbstractAgentScopeRuntimeHandler(
            String agentId,
            String name,
            String description,
            AgentScopeMessageAdapter messageAdapter,
            AgentScopeStreamAdapter streamAdapter) {
        super(agentId);
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        this.messageAdapter = messageAdapter;
        this.streamAdapter = streamAdapter;
    }

    @Override
    public final StreamAdapter resultAdapter() {
        return streamAdapter;
    }

    /** AgentScope's realized stream surfaces run lifecycle, errors, and streaming output deltas. */
    @Override
    protected Set<Kind> supportedKinds() {
        return EnumSet.of(Kind.RUN_START, Kind.RUN_END, Kind.TOOL_CALL_START, Kind.TOOL_CALL_END,
                Kind.ERROR, Kind.PROGRESS, Kind.MODEL_CALL_FIRST_TOKEN);
    }

    @Override
    protected final Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
        Stream<?> raw = streamAgentScopeEvents(messageAdapter.toInvocation(context));
        AtomicBoolean firstTokenSeen = new AtomicBoolean(false);
        return raw.peek(event -> emitTrajectory(event, trajectory, firstTokenSeen));
    }

    /**
     * Classifies a native event through the same {@link AgentScopeStreamAdapter} the result stream
     * uses — so trajectory and results never diverge — and maps it to a neutral draft.
     * COMPLETED/INTERRUPTED emit no inner event: the base owns the terminal RUN_END.
     */
    private void emitTrajectory(Object rawEvent, TrajectoryEmitter trajectory, AtomicBoolean firstTokenSeen) {
        AgentExecutionResult result = streamAdapter.map(rawEvent);
        switch (result.type()) {
            case OUTPUT -> {
                String text = result.outputContent();
                if (text != null && !text.isBlank()) {
                    // The first non-blank streamed output delta marks time-to-first-token.
                    if (firstTokenSeen.compareAndSet(false, true)) {
                        trajectory.emit(TrajectoryDraft.firstToken());
                    }
                    trajectory.emit(TrajectoryDraft.progress(text));
                }
            }
            case FAILED -> trajectory.emit(
                    TrajectoryDraft.error(null, result.errorCode(), result.errorMessage(),
                            ErrorCategory.UNKNOWN, null, false));
            default -> { }
        }
    }

    protected abstract Stream<?> streamAgentScopeEvents(AgentScopeInvocation invocation);
}
