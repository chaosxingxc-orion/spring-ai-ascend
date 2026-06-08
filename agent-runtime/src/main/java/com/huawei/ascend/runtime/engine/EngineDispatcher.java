package com.huawei.ascend.runtime.engine;

import com.huawei.ascend.runtime.common.Timing;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pulls the {@code AgentRuntimeHandler} for a command, runs it, and routes each
 * resulting {@link EngineEvent} to the control plane. The engine reports every
 * outcome through {@link TaskControlClient} only — the single outbound write —
 * and control fans caller-facing egress out from there.
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
        route(EngineEvent.started(scope));
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
            route(EngineEvent.failed(scope, "AGENT_ID_INVALID",
                    ex.getMessage() == null ? "no agent handler for agentId=" + scope.agentId() : ex.getMessage()));
            return;
        }
        long startedNanos = System.nanoTime();
        AgentExecutionContext context = new AgentExecutionContext(scope, command.getInput());
        LOGGER.info("engine handler start tenantId={} sessionId={} taskId={} agentId={} handler={} inputType={} inputMessages={}",
                scope.tenantId(),
                scope.sessionId(),
                scope.taskId(),
                scope.agentId(),
                handler.getClass().getName(),
                command.getInput().inputType(),
                command.getInput().messages().size());
        try (Stream<?> rawResults = handler.execute(context);
                Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(rawResults)) {
            results.peek(result -> LOGGER.info("engine handler result tenantId={} sessionId={} taskId={} agentId={} resultType={} outputLength={}",
                            scope.tenantId(),
                            scope.sessionId(),
                            scope.taskId(),
                            scope.agentId(),
                            result.type(),
                            result.output() == null || result.output().getContent() == null
                                    ? 0
                                    : result.output().getContent().length()))
                    .map(result -> toEvent(scope, result))
                    .forEach(this::route);
            LOGGER.info("trace stage=engine-handler-finish tenantId={} sessionId={} taskId={} agentId={} commandType={} handler={} durationMs={}",
                    scope.tenantId(),
                    scope.sessionId(),
                    scope.taskId(),
                    scope.agentId(),
                    command.getCommandType(),
                    handler.getClass().getName(),
                    Timing.elapsedMs(startedNanos));
        } catch (RuntimeException ex) {
            LOGGER.warn("engine handler failed tenantId={} sessionId={} taskId={} agentId={} errorClass={} message={}",
                    scope.tenantId(),
                    scope.sessionId(),
                    scope.taskId(),
                    scope.agentId(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            // A handler that throws (rather than emitting a failure result) must
            // still produce a terminal outcome, or the caller waits forever and
            // the reply channel leaks. Translate it into a failure event routed
            // to the control plane.
            route(EngineEvent.failed(scope, ex.getClass().getSimpleName(),
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            LOGGER.info("trace stage=engine-handler-finish tenantId={} sessionId={} taskId={} agentId={} commandType={} handler={} durationMs={} failed=true",
                    scope.tenantId(),
                    scope.sessionId(),
                    scope.taskId(),
                    scope.agentId(),
                    command.getCommandType(),
                    handler.getClass().getName(),
                    Timing.elapsedMs(startedNanos));
        }
    }

    private EngineEvent toEvent(EngineExecutionScope scope, AgentExecutionResult result) {
        return switch (result.type()) {
            case OUTPUT -> EngineEvent.output(scope, result.output());
            case COMPLETED -> EngineEvent.completed(scope, result.output());
            case FAILED -> EngineEvent.failed(scope, result.errorCode(), result.errorMessage());
            case INTERRUPTED -> EngineEvent.interrupted(scope, result.interruptType(), result.prompt());
        };
    }

    private void cancel(EngineCommandEvent command) {
        EngineExecutionScope scope = command.getScope();
        taskControlClient.markCancelled(scope, EngineEvent.cancelled(scope, "Cancelled by request"));
    }

    private void route(EngineEvent event) {
        EngineExecutionScope scope = event.scope();
        LOGGER.info("engine route event={} tenantId={} sessionId={} taskId={} agentId={}",
                event.kind(),
                scope.tenantId(),
                scope.sessionId(),
                scope.taskId(),
                scope.agentId());
        // Single outbound write: the engine reports every outcome to the control plane only.
        // Control is the sole authority and fans out caller-facing egress (gated on acceptance),
        // so authority and output are never written twice from here.
        switch (event.kind()) {
            case STARTED -> taskControlClient.markRunning(scope);
            case OUTPUT -> taskControlClient.appendOutput(scope, event);
            case INTERRUPTED -> taskControlClient.markWaiting(scope, event);
            case COMPLETED -> taskControlClient.markSucceeded(scope, event);
            case FAILED -> taskControlClient.markFailed(scope, event);
            case CANCELLED -> taskControlClient.markCancelled(scope, event);
        }
    }
}
