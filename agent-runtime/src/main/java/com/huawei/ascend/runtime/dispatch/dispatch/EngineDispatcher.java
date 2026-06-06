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
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves the {@link AgentDriver} for a command, runs it through the neutral
 * {@link RunCoordinator}, and routes each emitted {@link RunEvent} (mapped to an engine
 * execution event) to the task-control and access-layer clients. This is the direct A2A
 * execution path on the rebuilt neutral core — no legacy {@code AgentHandler} indirection.
 *
 * <p>Every non-cancel command is guaranteed to route exactly one terminal event
 * (COMPLETED / FAILED, or WAITING_INPUT when the run suspends): a driver stream that errors,
 * times out, is interrupted, or completes without a terminal {@link RunEvent} is converted into
 * an {@link EngineFailedEvent}, so a run is never left RUNNING after {@code markRunning}.
 */
public class EngineDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(EngineDispatcher.class);

    /** Upper bound on how long a single driver stream may take before it is failed as timed out. */
    private static final long STREAM_TIMEOUT_SECONDS = 120L;

    private final AgentDriverRegistry registry;
    private final TaskControlClient taskControlClient;
    private final AccessLayerClient accessLayerClient;
    private final long streamTimeoutSeconds;

    public EngineDispatcher(
            AgentDriverRegistry registry,
            TaskControlClient taskControlClient,
            AccessLayerClient accessLayerClient) {
        this(registry, taskControlClient, accessLayerClient, STREAM_TIMEOUT_SECONDS);
    }

    /** Test seam: a shorter stream timeout keeps timeout-path tests fast. */
    EngineDispatcher(
            AgentDriverRegistry registry,
            TaskControlClient taskControlClient,
            AccessLayerClient accessLayerClient,
            long streamTimeoutSeconds) {
        this.registry = registry;
        this.taskControlClient = taskControlClient;
        this.accessLayerClient = accessLayerClient;
        this.streamTimeoutSeconds = streamTimeoutSeconds;
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
            route(failed(scope, "AGENT_NOT_FOUND",
                    "No AgentDriver registered for agentId=" + scope.agentId()));
            return;
        }
        InvocationRequest request = new InvocationRequest(
                scope.taskId(), scope.agentId(), scope.sessionId(), lastUserText(command.getInput()));
        LOGGER.info("engine driver start tenantId={} sessionId={} taskId={} agentId={} framework={} inputType={} inputMessages={}",
                scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId(),
                driver.frameworkId(), command.getInput().inputType(), command.getInput().messages().size());
        route(new EngineStartedEvent(newId(), scope, Instant.now()));
        boolean terminalRouted = false;
        try {
            RunCoordinator coordinator = new RunCoordinator(driver);
            coordinator.start();
            StreamCollection collected = collect(coordinator.stream(request));
            for (RunEvent runEvent : collected.events()) {
                EngineExecutionEvent mapped = toEvent(scope, runEvent);
                if (mapped != null) {
                    route(mapped);
                    terminalRouted = terminalRouted || isTerminal(mapped);
                }
            }
            if (collected.error() != null) {
                route(failed(scope, collected.error().getClass().getSimpleName(), describe(collected.error())));
                terminalRouted = true;
            } else if (collected.timedOut()) {
                route(failed(scope, "RUN_TIMEOUT",
                        "driver stream did not complete within " + streamTimeoutSeconds + "s"));
                terminalRouted = true;
            } else if (collected.interrupted()) {
                route(failed(scope, "RUN_INTERRUPTED",
                        "engine dispatch thread was interrupted while awaiting the driver stream"));
                terminalRouted = true;
            }
            if (!terminalRouted) {
                // The stream completed but never produced a terminal RunEvent — never leave RUNNING.
                route(failed(scope, "NO_TERMINAL_EVENT",
                        "driver stream completed without a terminal RunEvent"));
            }
            LOGGER.info("trace stage=engine-driver-finish tenantId={} sessionId={} taskId={} agentId={} commandType={} framework={} durationMs={}",
                    scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId(),
                    command.getCommandType(), driver.frameworkId(), elapsedMs(startedNanos));
        } catch (Throwable ex) {
            // Includes Errors (e.g. LinkageError / NoSuchMethodError from a broken adapter classpath):
            // a silently dying dispatch thread would leave the run RUNNING with no terminal event.
            LOGGER.warn("engine driver failed tenantId={} sessionId={} taskId={} agentId={} errorClass={} message={}",
                    scope.tenantId(), scope.sessionId(), scope.taskId(), scope.agentId(),
                    ex.getClass().getSimpleName(), ex.getMessage());
            if (!terminalRouted) {
                route(failed(scope, ex.getClass().getSimpleName(), describe(ex)));
            }
            if (ex instanceof VirtualMachineError vmError) {
                // OOM / StackOverflow / InternalError are unrecoverable — record the failure, then propagate.
                throw vmError;
            }
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

    private static boolean isTerminal(EngineExecutionEvent event) {
        return event instanceof EngineCompletedEvent
                || event instanceof EngineFailedEvent
                || event instanceof EngineInterruptedEvent;
    }

    private EngineFailedEvent failed(EngineExecutionScope scope, String code, String message) {
        return new EngineFailedEvent(newId(), scope, Instant.now(), code,
                message == null || message.isBlank() ? code : message);
    }

    private static String describe(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
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

    /** Outcome of draining a driver stream: the events seen plus how the stream ended. */
    private record StreamCollection(List<RunEvent> events, Throwable error,
                                    boolean timedOut, boolean interrupted) {
    }

    private StreamCollection collect(Flow.Publisher<RunEvent> publisher) {
        List<RunEvent> out = new ArrayList<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
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
                error.set(throwable == null ? new IllegalStateException("driver stream error") : throwable);
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        });
        boolean completed;
        boolean interrupted = false;
        try {
            completed = done.await(streamTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            interrupted = true;
            completed = false;
        }
        boolean timedOut = !completed && !interrupted;
        // Reactive Streams serialises onNext/onComplete/onError; the latch barrier publishes them
        // to this thread, so a plain list read after await() sees every delivered event.
        return new StreamCollection(List.copyOf(out), error.get(), timedOut, interrupted);
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
