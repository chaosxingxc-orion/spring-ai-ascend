package com.huawei.ascend.service.engine.dispatch;

import com.huawei.ascend.service.engine.event.EngineAgentCallEvent;
import com.huawei.ascend.service.engine.event.EngineCancelledEvent;
import com.huawei.ascend.service.engine.event.EngineCommandEvent;
import com.huawei.ascend.service.engine.event.EngineCompletedEvent;
import com.huawei.ascend.service.engine.event.EngineExecutionEvent;
import com.huawei.ascend.service.engine.event.EngineFailedEvent;
import com.huawei.ascend.service.engine.event.EngineInterruptedEvent;
import com.huawei.ascend.service.engine.event.EngineOutputEvent;
import com.huawei.ascend.service.engine.event.EngineStartedEvent;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.model.EngineExecutionScope;
import com.huawei.ascend.service.engine.model.InterruptType;
import com.huawei.ascend.service.engine.spi.AccessLayerClient;
import com.huawei.ascend.service.engine.spi.AgentHandler;
import com.huawei.ascend.service.engine.spi.TaskControlClient;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Pulls the {@code AgentHandler} for a command, runs it, and routes each emitted
 * execution event to the task-control and access-layer clients per the state and
 * output mapping in engine model design §13.
 */
public class EngineDispatcher {

    private final AgentHandlerRegistry registry;
    private final TaskControlClient taskControlClient;
    private final AccessLayerClient accessLayerClient;

    public EngineDispatcher(AgentHandlerRegistry registry, TaskControlClient taskControlClient, AccessLayerClient accessLayerClient) {
        this.registry = registry;
        this.taskControlClient = taskControlClient;
        this.accessLayerClient = accessLayerClient;
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
        AgentHandler handler = registry.findByAgentId(command.getScope().agentId());
        AgentExecutionContext context = new AgentExecutionContext(command.getScope(), command.getInput());
        try (Stream<EngineExecutionEvent> events = handler.execute(context)) {
            events.forEach(this::route);
        } catch (RuntimeException ex) {
            // A handler that throws (rather than emitting a failure event) must
            // still produce a terminal outcome, or the caller waits forever and
            // the reply channel leaks. Translate it into a failure event routed
            // to both the task-control and access layers.
            EngineFailedEvent failed = new EngineFailedEvent(
                    UUID.randomUUID().toString(),
                    command.getScope(),
                    Instant.now(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
            route(failed);
        }
    }

    private void cancel(EngineCommandEvent command) {
        EngineExecutionScope scope = command.getScope();
        EngineCancelledEvent event = new EngineCancelledEvent(
                UUID.randomUUID().toString(), scope, Instant.now(), "Cancelled by request");
        taskControlClient.markCancelled(scope, event);
    }

    private void route(EngineExecutionEvent event) {
        EngineExecutionScope scope = event.getScope();
        if (event instanceof EngineStartedEvent) {
            taskControlClient.markRunning(scope);
        } else if (event instanceof EngineOutputEvent e) {
            accessLayerClient.appendOutput(scope, e);
        } else if (event instanceof EngineInterruptedEvent e) {
            taskControlClient.markWaiting(scope, e);
            if (e.getInterruptType() != InterruptType.WAITING_CHILD_AGENT) {
                accessLayerClient.requestUserInput(scope, e);
            }
        } else if (event instanceof EngineCompletedEvent e) {
            taskControlClient.markSucceeded(scope, e);
            accessLayerClient.completeOutput(scope, e);
        } else if (event instanceof EngineFailedEvent e) {
            taskControlClient.markFailed(scope, e);
            accessLayerClient.failOutput(scope, e);
        } else if (event instanceof EngineCancelledEvent e) {
            taskControlClient.markCancelled(scope, e);
        } else if (event instanceof EngineAgentCallEvent) {
            // Agent-to-agent routing is handled from Phase 3 onward.
            throw new UnsupportedOperationException("EngineAgentCallEvent routing not implemented in Phase 1");
        }
    }
}
