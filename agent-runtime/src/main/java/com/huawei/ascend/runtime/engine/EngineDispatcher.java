package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.engine.event.EngineAgentCallEvent;
import com.huawei.ascend.runtime.engine.event.EngineCancelledEvent;
import com.huawei.ascend.runtime.engine.event.EngineCommandEvent;
import com.huawei.ascend.runtime.engine.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.engine.event.EngineExecutionEvent;
import com.huawei.ascend.runtime.engine.event.EngineFailedEvent;
import com.huawei.ascend.runtime.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.engine.event.EngineOutputEvent;
import com.huawei.ascend.runtime.engine.event.EngineStartedEvent;
import com.huawei.ascend.runtime.engine.handler.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.model.EngineExecutionScope;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.port.TaskControlClient;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulls the {@code AgentRuntimeHandler} for a command, runs it, and routes each emitted
 * execution event to the task-control and access-layer clients per the state and
 * output mapping in engine model design §13.
 */
public class EngineDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineDispatcher.class);

    private final AgentRuntimeHandlerRegistry registry;
    private final TaskControlClient taskControlClient;

    public EngineDispatcher(AgentRuntimeHandlerRegistry registry, TaskControlClient taskControlClient) {
        this.registry = registry;
        this.taskControlClient = taskControlClient;
    }

    public void dispatch(EngineCommandEvent command) {
        String commandType = command.getCommandType();
        if ("CANCEL".equals(commandType)) {
            cancel(command);
            return;
        }
        // EXECUTE and RESUME both run the handler; on RESUME the underlying agent
        // framework restores prior state by conversation id (design §12.2).
        runHandler(command);
    }

    private void runHandler(EngineCommandEvent command) {
        EngineExecutionScope scope = command.getScope();
        route(new EngineStartedEvent(newId(), scope, Instant.now()));
        AgentRuntimeHandler handler;
        try {
            handler = registry.findByAgentId(scope.agentId());
        } catch (RuntimeException ex) {
            // An unknown agentId must still converge the already-accepted task to a
            // terminal outcome through the single control-plane authority — otherwise
            // the task hangs non-terminal and the SSE caller waits forever. Resolution
            // failures are an invalid-input failure, reported as AGENT_ID_INVALID.
            LOGGER.warn("engine handler unresolved tenantId={} sessionId={} taskId={} agentId={} message={}",
                    scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId(), ex.getMessage());
            route(new EngineFailedEvent(newId(), scope, Instant.now(), "AGENT_ID_INVALID",
                    ex.getMessage() == null ? "no agent handler for agentId=" + scope.agentId() : ex.getMessage()));
            return;
        }
        long startedNanos = System.nanoTime();
        AgentExecutionContext context = new AgentExecutionContext(scope, command.getInput());
        LOGGER.info("engine handler start tenantId={} sessionId={} taskId={} agentId={} handler={} inputType={} inputMessages={}",
                command.getScope().tenantId(),
                command.getScope().sessionId(),
                command.getScope().taskId(),
                command.getScope().agentId(),
                handler.getClass().getName(),
                command.getInput().inputType(),
                command.getInput().messages().size());
        try (Stream<?> rawResults = handler.execute(context);
                Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(rawResults)) {
            results.peek(result -> LOGGER.info("engine handler result tenantId={} sessionId={} taskId={} agentId={} resultType={} outputLength={}",
                            command.getScope().tenantId(),
                            command.getScope().sessionId(),
                            command.getScope().taskId(),
                            command.getScope().agentId(),
                            result.type(),
                            result.output() == null || result.output().getContent() == null
                                    ? 0
                                    : result.output().getContent().length()))
                    .map(result -> toEvent(command.getScope(), result))
                    .forEach(this::route);
            LOGGER.info("trace stage=engine-handler-finish tenantId={} sessionId={} taskId={} agentId={} commandType={} handler={} durationMs={}",
                    command.getScope().tenantId(),
                    command.getScope().sessionId(),
                    command.getScope().taskId(),
                    command.getScope().agentId(),
                    command.getCommandType(),
                    handler.getClass().getName(),
                    elapsedMs(startedNanos));
        } catch (RuntimeException ex) {
            LOGGER.warn("engine handler failed tenantId={} sessionId={} taskId={} agentId={} errorClass={} message={}",
                    command.getScope().tenantId(),
                    command.getScope().sessionId(),
                    command.getScope().taskId(),
                    command.getScope().agentId(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            // A handler that throws (rather than emitting a failure event) must
            // still produce a terminal outcome, or the caller waits forever and
            // the reply channel leaks. Translate it into a failure event routed
            // to both the task-control and access layers.
            EngineFailedEvent failed = new EngineFailedEvent(
                    newId(),
                    command.getScope(),
                    Instant.now(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            route(failed);
            LOGGER.info("trace stage=engine-handler-finish tenantId={} sessionId={} taskId={} agentId={} commandType={} handler={} durationMs={} failed=true",
                    command.getScope().tenantId(),
                    command.getScope().sessionId(),
                    command.getScope().taskId(),
                    command.getScope().agentId(),
                    command.getCommandType(),
                    handler.getClass().getName(),
                    elapsedMs(startedNanos));
        }
    }

    private EngineExecutionEvent toEvent(EngineExecutionScope scope, AgentExecutionResult result) {
        return switch (result.type()) {
            case OUTPUT -> new EngineOutputEvent(newId(), scope, Instant.now(), result.output());
            case COMPLETED -> new EngineCompletedEvent(newId(), scope, Instant.now(), result.output());
            case FAILED -> new EngineFailedEvent(newId(), scope, Instant.now(), result.errorCode(), result.errorMessage());
            case INTERRUPTED -> new EngineInterruptedEvent(newId(), scope, Instant.now(),
                    result.interruptType(), result.prompt());
        };
    }

    private void cancel(EngineCommandEvent command) {
        EngineExecutionScope scope = command.getScope();
        EngineCancelledEvent event = new EngineCancelledEvent(
                newId(), scope, Instant.now(), "Cancelled by request");
        taskControlClient.markCancelled(scope, event);
    }

    private void route(EngineExecutionEvent event) {
        EngineExecutionScope scope = event.getScope();
        LOGGER.info("engine route event={} tenantId={} sessionId={} taskId={} agentId={}",
                event.getClass().getSimpleName(),
                scope.tenantId(),
                scope.sessionId(),
                scope.taskId(),
                scope.agentId());
        // Single outbound write: the engine reports every outcome to the control plane only.
        // Control is the sole authority and fans out caller-facing egress (gated on acceptance),
        // so authority and output are never written twice from here.
        if (event instanceof EngineStartedEvent) {
            taskControlClient.markRunning(scope);
        } else if (event instanceof EngineOutputEvent e) {
            taskControlClient.appendOutput(scope, e);
        } else if (event instanceof EngineInterruptedEvent e) {
            taskControlClient.markWaiting(scope, e);
        } else if (event instanceof EngineCompletedEvent e) {
            taskControlClient.markSucceeded(scope, e);
        } else if (event instanceof EngineFailedEvent e) {
            taskControlClient.markFailed(scope, e);
        } else if (event instanceof EngineCancelledEvent e) {
            taskControlClient.markCancelled(scope, e);
        } else if (event instanceof EngineAgentCallEvent) {
            // Agent-to-agent routing is handled from Phase 3 onward.
            throw new UnsupportedOperationException("EngineAgentCallEvent routing not implemented in Phase 1");
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
