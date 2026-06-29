package com.huawei.ascend.runtime.engine.alpha;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.openjiuwen.core.kernel.model.AgentEvent;
import com.openjiuwen.core.kernel.model.EventType;
import com.openjiuwen.core.kernel.model.TaskId;
import reactor.core.publisher.Flux;

import java.util.stream.Stream;

/**
 * Hosts the openjiuwen 2.0 Alpha execution model behind the framework-neutral
 * {@link com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler} SPI.
 *
 * <p>This skeleton echoes the user's input back as a completed result — it exercises the
 * full bridge (event model → {@link FluxToResultStream} → SPI result stream) without an
 * LLM, so the hosting path can be hardened before the real Alpha strategy lands. The
 * event stream carries the user text through {@code THINKING_DELTA} (an OUTPUT chunk)
 * and terminates with {@code TASK_COMPLETED}; the task id is taken from the runtime
 * identity when present so execution is traceable across the runtime's lifecycle.
 */
public class AlphaRuntimeHandler extends AbstractAgentRuntimeHandler {

    /** Prefix marking text produced by the echo strategy, distinguishing it from user input. */
    static final String ECHO_PREFIX = "[echo] ";

    public AlphaRuntimeHandler(String agentId) {
        super(agentId);
    }

    @Override
    protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
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
