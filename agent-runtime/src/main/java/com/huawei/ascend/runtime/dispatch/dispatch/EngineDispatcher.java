package com.huawei.ascend.runtime.dispatch.dispatch;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.common.RunEventType;
import com.huawei.ascend.runtime.common.RunPhase;
import com.huawei.ascend.runtime.dispatch.event.EngineAgentCallEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineCancelledEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineCommandEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineExecutionEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineFailedEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineOutputEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineStartedEvent;
import com.huawei.ascend.runtime.dispatch.model.EngineExecutionScope;
import com.huawei.ascend.runtime.dispatch.model.EngineInput;
import com.huawei.ascend.runtime.dispatch.model.EngineOutput;
import com.huawei.ascend.runtime.dispatch.model.InterruptType;
import com.huawei.ascend.runtime.dispatch.port.AccessLayerClient;
import com.huawei.ascend.runtime.dispatch.port.TaskControlClient;
import com.huawei.ascend.runtime.engine.RunCoordinator;
import com.huawei.ascend.runtime.engine.registry.AgentDriverRegistry;
import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import com.huawei.ascend.runtime.schema.Message;
import com.huawei.ascend.runtime.schema.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the {@link AgentDriver} for a command, runs it through the neutral
 * {@link RunCoordinator}, and routes each emitted {@link RunEvent} (mapped to an engine
 * execution event) to the task-control and access-layer clients. This is the direct A2A
 * execution path on the rebuilt neutral core — no legacy {@code AgentHandler} indirection.
 */
public class EngineDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineDispatcher.class);

    private final AgentDriverRegistry registry;
    private final TaskControlClient taskControlClient;
    private final AccessLayerClient accessLayerClient;

    public EngineDispatcher(
            AgentDriverRegistry registry,
            TaskControlClient taskControlClient,
            AccessLayerClient accessLayerClient) {
        this.registry = registry;
        this.taskControlClient = taskControlClient;
        this.accessLayerClient = accessLayerClient;
    }

    public void dispatch(EngineCommandEvent command) {
        if ("CANCEL".equals(command.getCommandType())) {
            cancel(command);
            return;
        }
        // EXECUTE and RESUME both run the driver; on RESUME the underlying framework
        // restores prior state by session/conversation id inside the adapter.
        runDriver(command);
    }

    private void runDriver(EngineCommandEvent command) {
        EngineExecutionScope scope = command.getScope();
        long startedNanos = System.nanoTime();
        AgentDriver driver = registry.find(scope.agentId());
        if (driver == null) {
            LOGGER.warn("engine driver missing tenantId={} sessionId={} taskId={} agentId={}",
                    scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId());
            route(new EngineFailedEvent(newId(), scope, Instant.now(),
                    "AGENT_NOT_FOUND", "No AgentDriver registered for agentId=" + scope.agentId()));
            return;
        }
        InvocationRequest request = new InvocationRequest(
                scope.taskId(), scope.agentId(), scope.sessionId(), lastUserText(command.getInput()));
        LOGGER.info("engine driver start tenantId={} sessionId={} taskId={} agentId={} framework={} inputType={} inputMessages={}",
                scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId(),
                driver.frameworkId(), command.getInput().inputType(), command.getInput().messages().size());
        route(new EngineStartedEvent(newId(), scope, Instant.now()));
        RunCoordinator coordinator = new RunCoordinator(driver);
        coordinator.start();
        try {
            for (RunEvent runEvent : collect(coordinator.stream(request))) {
                EngineExecutionEvent mapped = toEvent(scope, runEvent);
                if (mapped != null) {
                    route(mapped);
                }
            }
            LOGGER.info("trace stage=engine-driver-finish tenantId={} sessionId={} taskId={} agentId={} commandType={} framework={} durationMs={}",
                    scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId(),
                    command.getCommandType(), driver.frameworkId(), elapsedMs(startedNanos));
        } catch (RuntimeException ex) {
            LOGGER.warn("engine driver failed tenantId={} sessionId={} taskId={} agentId={} errorClass={} message={}",
                    scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            route(new EngineFailedEvent(newId(), scope, Instant.now(),
                    ex.getClass().getSimpleName(),
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private EngineExecutionEvent toEvent(EngineExecutionScope scope, RunEvent runEvent) {
        if (runEvent.kind() == RunEventType.ACCEPTED) {
            // The EngineStartedEvent already marked the run as running.
            return null;
        }
        if (runEvent.phase() == RunPhase.WAITING_INPUT) {
            return new EngineInterruptedEvent(newId(), scope, Instant.now(),
                    InterruptType.HUMAN_INPUT, runEvent.content());
        }
        return switch (runEvent.kind()) {
            case CHUNK -> new EngineOutputEvent(newId(), scope, Instant.now(),
                    new EngineOutput(runEvent.content(), false));
            case COMPLETED -> new EngineCompletedEvent(newId(), scope, Instant.now(),
                    new EngineOutput(runEvent.content(), true));
            case FAILED -> new EngineFailedEvent(newId(), scope, Instant.now(),
                    "RUN_FAILED", runEvent.error() == null ? "run failed" : runEvent.error());
            case ACCEPTED -> null;
        };
    }

    private static String lastUserText(EngineInput input) {
        List<Message> messages = input == null ? null : input.messages();
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message != null && message.role() == Role.USER) {
                return message.text();
            }
        }
        return messages.get(messages.size() - 1).text();
    }

    private static List<RunEvent> collect(Flow.Publisher<RunEvent> publisher) {
        List<RunEvent> out = new ArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<RunEvent>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(RunEvent item) {
                out.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        try {
            done.await(120, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        return out;
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
                scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId());
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
            throw new UnsupportedOperationException("EngineAgentCallEvent routing not implemented");
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
