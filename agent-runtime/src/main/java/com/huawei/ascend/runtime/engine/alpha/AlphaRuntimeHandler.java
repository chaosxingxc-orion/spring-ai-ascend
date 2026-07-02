package com.huawei.ascend.runtime.engine.alpha;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.openjiuwen.core.kernel.model.AgentEvent;
import com.openjiuwen.core.kernel.model.EventType;
import com.openjiuwen.core.kernel.model.TaskId;
import com.openjiuwen.core.meta.AgentDefinition;
import com.openjiuwen.runtime.alpha.PEVAlphaStrategy;
import com.openjiuwen.runtime.core.dispatch.ExecutionStrategy;
import com.openjiuwen.runtime.core.dispatch.TaskContext;
import com.openjiuwen.runtime.core.engine.AgentKernel;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Hosts the openjiuwen 2.0 Alpha execution model behind the framework-neutral
 * {@link com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler} SPI.
 *
 * <p>Dual-mode operation:
 * <ul>
 *   <li><b>PEV mode</b> (kernel present): full Plan-Execute-Verify pipeline via
 *       {@link PEVAlphaStrategy}. Activated when constructed with an
 *       {@link AgentKernel} + {@link AgentDefinition}.</li>
 *   <li><b>Echo mode</b> (kernel absent, backward-compat): echoes user input as
 *       completed result — exercises the event→{@link FluxToResultStream} bridge
 *       without LLM dependency. Used by existing tests.</li>
 * </ul>
 */
public class AlphaRuntimeHandler extends AbstractAgentRuntimeHandler {

    /** Prefix marking text produced by the echo strategy, distinguishing it from user input. */
    static final String ECHO_PREFIX = "[echo] ";

    /** Optional — when present, enables PEV execution mode. Null = echo fallback. */
    private final AgentKernel kernel;

    /** Optional — required for PEV mode alongside kernel. */
    private final AgentDefinition agentDef;

    // ==================== constructors ====================

    /** Echo-mode constructor (backward-compat for tests). */
    public AlphaRuntimeHandler(String agentId) {
        super(agentId);
        this.kernel = null;
        this.agentDef = null;
    }

    /** PEV-mode constructor — connects the real execution pipeline. */
    public AlphaRuntimeHandler(String agentId, AgentKernel kernel, AgentDefinition agentDef) {
        super(agentId);
        this.kernel = kernel;
        this.agentDef = agentDef;
    }

    // ==================== execute ====================

    @Override
    protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
        if (kernel != null) {
            return doExecutePEV(context);
        }
        return doExecuteEcho(context);
    }

    /**
     * PEV execution path: AgentExecutionContext → TaskContext → PEVAlphaStrategy.execute().
     * The PEVAlphaStrategy emits AgentEvent; FluxToResultStream converts them to
     * AgentExecutionResult for the SPI stream contract.
     */
    private Stream<?> doExecutePEV(AgentExecutionContext context) {
        TaskContext taskContext = AlphaContextMapper.toTaskContext(
                context, agentId(), kernel, agentDef, Map.of());
        ExecutionStrategy strategy = new PEVAlphaStrategy();
        return new FluxToResultStream().apply(strategy.execute(taskContext));
    }

    /**
     * Echo fallback path: echoes user text through the event→result bridge.
     * Preserved for backward compatibility with existing tests.
     */
    private Stream<?> doExecuteEcho(AgentExecutionContext context) {
        String input = context.lastUserText();
        TaskId taskId = resolveTaskId(context);
        String echo = ECHO_PREFIX + input;
        Flux<AgentEvent> events = Flux.just(
                AgentEvent.of(taskId, EventType.TASK_CREATED, input),
                AgentEvent.of(taskId, EventType.THINKING_DELTA, echo),
                AgentEvent.of(taskId, EventType.TASK_COMPLETED, echo));
        return new FluxToResultStream().apply(events);
    }

    @Override
    public StreamAdapter resultAdapter() {
        // doExecute already yields AgentExecutionResult elements via FluxToResultStream;
        // the adapter only re-types the raw stream the runtime hands it.
        return raw -> raw.map(r -> (AgentExecutionResult) r);
    }

    private static TaskId resolveTaskId(AgentExecutionContext context) {
        String runtimeTaskId = context.getScope().taskId();
        if (runtimeTaskId != null && !runtimeTaskId.isBlank()) {
            return new TaskId(runtimeTaskId);
        }
        return TaskId.generate();
    }
}
