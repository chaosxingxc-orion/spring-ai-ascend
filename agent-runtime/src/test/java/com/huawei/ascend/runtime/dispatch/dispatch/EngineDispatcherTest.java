package com.huawei.ascend.runtime.dispatch.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.huawei.ascend.runtime.common.InvocationRequest;
import com.huawei.ascend.runtime.common.RunEvent;
import com.huawei.ascend.runtime.common.RunEventType;
import com.huawei.ascend.runtime.common.RunPhase;
import com.huawei.ascend.runtime.dispatch.event.EngineCommandEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineCompletedEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineFailedEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineInterruptedEvent;
import com.huawei.ascend.runtime.dispatch.event.EngineOutputEvent;
import com.huawei.ascend.runtime.dispatch.model.EngineExecutionScope;
import com.huawei.ascend.runtime.dispatch.model.EngineInput;
import com.huawei.ascend.runtime.dispatch.port.AccessLayerClient;
import com.huawei.ascend.runtime.dispatch.port.TaskControlClient;
import com.huawei.ascend.runtime.engine.registry.AgentDriverRegistry;
import com.huawei.ascend.runtime.engine.registry.DefaultAgentDriverRegistry;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentDriver;
import com.huawei.ascend.runtime.engine.spi.AgentDriver;
import com.huawei.ascend.runtime.engine.spi.OutputConverter;
import com.huawei.ascend.runtime.schema.Message;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;

/**
 * R1: the native driver path must always reach exactly one terminal outcome — a stream that errors,
 * times out, or completes without a terminal RunEvent becomes a FAILED run, never a stuck RUNNING.
 */
class EngineDispatcherTest {

    @Test
    void completedStreamMarksSucceeded() {
        RecordingTaskControl tc = new RecordingTaskControl();
        RecordingAccess access = new RecordingAccess();
        dispatcher(tc, access, driver("agent-x",
                publisher(List.of(RunEvent.accepted(), RunEvent.completed(1, "pong")), null, true)))
                .dispatch(execute("agent-x"));

        assertTrue(tc.running, "EngineStartedEvent should markRunning");
        assertNotNull(tc.succeeded, "a completed stream should markSucceeded");
        assertEquals("pong", access.completed.getFinalOutput().getContent());
        assertNull(tc.failed);
    }

    @Test
    void asyncOnErrorBecomesFailedRun() {
        RecordingTaskControl tc = new RecordingTaskControl();
        RecordingAccess access = new RecordingAccess();
        dispatcher(tc, access, driver("agent-x",
                publisher(List.of(RunEvent.accepted(), RunEvent.chunk(1, "po")),
                        new RuntimeException("transport boom"), false)))
                .dispatch(execute("agent-x"));

        assertTrue(tc.running);
        assertEquals("po", access.appended.get(0).getOutput().getContent(), "chunk before error is still routed");
        assertNotNull(tc.failed, "Publisher.onError must produce markFailed");
        assertNotNull(access.failed, "Publisher.onError must produce failOutput");
        assertTrue(access.failed.getErrorMessage().contains("transport boom"));
    }

    @Test
    void streamWithoutTerminalEventFailsClosed() {
        RecordingTaskControl tc = new RecordingTaskControl();
        RecordingAccess access = new RecordingAccess();
        dispatcher(tc, access, driver("agent-x",
                publisher(List.of(RunEvent.accepted(), RunEvent.chunk(1, "po")), null, true)))
                .dispatch(execute("agent-x"));

        assertNotNull(tc.failed, "a stream completing without a terminal RunEvent must fail closed");
        assertEquals("NO_TERMINAL_EVENT", access.failed.getErrorCode());
    }

    @Test
    void timeoutBecomesFailedRun() {
        RecordingTaskControl tc = new RecordingTaskControl();
        RecordingAccess access = new RecordingAccess();
        // 1s timeout + a publisher that emits ACCEPTED then never completes.
        EngineDispatcher dispatcher = new EngineDispatcher(
                registryOf(driver("agent-x", publisher(List.of(RunEvent.accepted()), null, false))),
                tc, access, 1L);
        dispatcher.dispatch(execute("agent-x"));

        assertNotNull(tc.failed, "a never-completing stream must time out into a failed run");
        assertEquals("RUN_TIMEOUT", access.failed.getErrorCode());
    }

    @Test
    void waitingInputMarksWaitingAndRequestsUserInput() {
        RecordingTaskControl tc = new RecordingTaskControl();
        RecordingAccess access = new RecordingAccess();
        RunEvent waiting = new RunEvent(1, RunEventType.CHUNK, RunPhase.WAITING_INPUT, "need input", null);
        dispatcher(tc, access, driver("agent-x",
                publisher(List.of(RunEvent.accepted(), waiting), null, true)))
                .dispatch(execute("agent-x"));

        assertNotNull(tc.waiting, "WAITING_INPUT must markWaiting");
        assertNotNull(access.userInput, "WAITING_INPUT must requestUserInput");
        assertNull(tc.failed); // WAITING_INPUT is a valid suspension, not a failure
    }

    @Test
    void adapterErrorDuringInvokeBecomesFailedRun() {
        RecordingTaskControl tc = new RecordingTaskControl();
        RecordingAccess access = new RecordingAccess();
        // invoke() throws a LinkageError (the broken-adapter-classpath case) — must not kill the thread silently.
        AgentDriver broken = new AbstractAgentDriver() {
            @Override
            public String name() {
                return "agent-x";
            }

            @Override
            public String frameworkId() {
                return "broken";
            }

            @Override
            public Object invoke(InvocationRequest request) {
                throw new NoSuchMethodError("AgentCard.builder()");
            }

            @Override
            public OutputConverter outputConverter() {
                return frameworkStream -> sub -> sub.onSubscribe(noopSubscription());
            }
        };
        dispatcher(tc, access, broken).dispatch(execute("agent-x"));

        assertNotNull(tc.failed, "an Error thrown by the adapter must become a failed run, not a hung thread");
        assertEquals("NoSuchMethodError", access.failed.getErrorCode());
    }

    @Test
    void missingDriverFailsClosed() {
        RecordingTaskControl tc = new RecordingTaskControl();
        RecordingAccess access = new RecordingAccess();
        new EngineDispatcher(new DefaultAgentDriverRegistry(), tc, access)
                .dispatch(execute("nobody"));
        assertNotNull(access.failed);
        assertEquals("AGENT_NOT_FOUND", access.failed.getErrorCode());
    }

    // ---- helpers ----

    private static void assertNull(Object value) {
        assertFalse(value != null, "expected null but was: " + value);
    }

    private static EngineDispatcher dispatcher(TaskControlClient tc, AccessLayerClient access, AgentDriver driver) {
        return new EngineDispatcher(registryOf(driver), tc, access);
    }

    private static AgentDriverRegistry registryOf(AgentDriver driver) {
        DefaultAgentDriverRegistry registry = new DefaultAgentDriverRegistry();
        registry.register(driver);
        return registry;
    }

    private static EngineCommandEvent execute(String agentId) {
        EngineExecutionScope scope = new EngineExecutionScope("t1", "u1", "s1", "task1", agentId);
        EngineInput input = new EngineInput("USER_MESSAGE", List.of(Message.user("ping")), Map.of());
        return new EngineCommandEvent("EXECUTE", scope, input, Instant.now());
    }

    private static AgentDriver driver(String agentId, Flow.Publisher<RunEvent> publisher) {
        return new AbstractAgentDriver() {
            @Override
            public String name() {
                return agentId;
            }

            @Override
            public String frameworkId() {
                return "test";
            }

            @Override
            public Object invoke(InvocationRequest request) {
                return request;
            }

            @Override
            public OutputConverter outputConverter() {
                return frameworkStream -> publisher;
            }
        };
    }

    private static Flow.Publisher<RunEvent> publisher(List<RunEvent> events, Throwable error, boolean complete) {
        return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
            private boolean emitted = false;

            @Override
            public void request(long n) {
                if (emitted) {
                    return;
                }
                emitted = true;
                for (RunEvent event : events) {
                    subscriber.onNext(event);
                }
                if (error != null) {
                    subscriber.onError(error);
                } else if (complete) {
                    subscriber.onComplete();
                }
                // else: never terminates — exercises the dispatcher timeout path.
            }

            @Override
            public void cancel() {
                // no-op
            }
        });
    }

    private static Flow.Subscription noopSubscription() {
        return new Flow.Subscription() {
            @Override
            public void request(long n) {
                // no-op
            }

            @Override
            public void cancel() {
                // no-op
            }
        };
    }

    private static final class RecordingTaskControl implements TaskControlClient {
        boolean running;
        EngineCompletedEvent succeeded;
        EngineFailedEvent failed;
        EngineInterruptedEvent waiting;

        @Override
        public void markRunning(EngineExecutionScope scope) {
            running = true;
        }

        @Override
        public void markWaiting(EngineExecutionScope scope, EngineInterruptedEvent event) {
            waiting = event;
        }

        @Override
        public void markSucceeded(EngineExecutionScope scope, EngineCompletedEvent event) {
            succeeded = event;
        }

        @Override
        public void markFailed(EngineExecutionScope scope, EngineFailedEvent event) {
            failed = event;
        }

        @Override
        public void markCancelled(EngineExecutionScope scope,
                com.huawei.ascend.runtime.dispatch.event.EngineCancelledEvent event) {
            // unused in these tests
        }
    }

    private static final class RecordingAccess implements AccessLayerClient {
        final List<EngineOutputEvent> appended = new ArrayList<>();
        EngineCompletedEvent completed;
        EngineFailedEvent failed;
        EngineInterruptedEvent userInput;

        @Override
        public void appendOutput(EngineExecutionScope scope, EngineOutputEvent event) {
            appended.add(event);
        }

        @Override
        public void completeOutput(EngineExecutionScope scope, EngineCompletedEvent event) {
            completed = event;
        }

        @Override
        public void failOutput(EngineExecutionScope scope, EngineFailedEvent event) {
            failed = event;
        }

        @Override
        public void requestUserInput(EngineExecutionScope scope, EngineInterruptedEvent event) {
            userInput = event;
        }
    }
}
