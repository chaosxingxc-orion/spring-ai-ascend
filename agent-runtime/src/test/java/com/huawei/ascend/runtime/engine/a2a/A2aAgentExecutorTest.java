package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryLevel;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class A2aAgentExecutorTest {

    /** A FAILED result must surface its code+message to the A2A wire, not a bare fail(). */
    @Test
    void failedResult_carriesErrorReasonToTheWire() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of(new Object()));
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.failed("OUT_OF_DOMAIN", "no skill for request"));
        when(handler.resultAdapter()).thenReturn(adapter);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        assertThat(failureText(emitter)).isEqualTo("OUT_OF_DOMAIN: no skill for request");
    }

    /** An exception thrown during execution must also fail with a reason, not silently. */
    @Test
    void executionException_failsWithReason() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenThrow(new IllegalStateException("boom"));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        // An unrecognised exception is classified INTERNAL by RuntimeErrorCode.
        assertThat(failureText(emitter)).isEqualTo("INTERNAL: boom");
    }

    /** A thrown exception must reach the client as a machine-readable DataPart, not only free text. */
    @Test
    void executionException_carriesStructuredErrorDataPart() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenThrow(new IllegalArgumentException("missing slot"));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        Map<String, Object> data = failureData(emitter);
        assertThat(data).containsEntry("code", "INVALID_INPUT");
        assertThat(data).containsEntry("retryable", false);
        assertThat(data).containsEntry("message", "missing slot");
    }

    /** A retryable failure (upstream unavailable) must surface retryable=true to the client. */
    @Test
    void executionException_marksUpstreamFailureRetryable() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenThrow(new RuntimeException(new java.io.IOException("conn reset")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        Map<String, Object> data = failureData(emitter);
        assertThat(data).containsEntry("code", "UPSTREAM_UNAVAILABLE");
        assertThat(data).containsEntry("retryable", true);
    }

    /** No registered handler is a rejection, not a runtime failure: reject() with a reason, never a bare fail(). */
    @Test
    void noHandler_rejectsTaskWithReason() {
        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(null).execute(requestContext(), emitter);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).reject(captor.capture());
        String text = captor.getValue().parts().stream()
                .filter(TextPart.class::isInstance)
                .map(p -> ((TextPart) p).text())
                .reduce("", String::concat);
        assertThat(text).contains("NO_HANDLER");
    }

    /** The lifecycle must announce SUBMITTED before WORKING, matching the A2A task lifecycle. */
    @Test
    void submitPrecedesStartWork() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of(new Object()));
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.completed("ok"));
        when(handler.resultAdapter()).thenReturn(adapter);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        InOrder inOrder = inOrder(emitter);
        inOrder.verify(emitter).submit();
        inOrder.verify(emitter).startWork();
    }

    /** Streamed OUTPUT chunks must form one growing artifact: same id, append false→true, never last-chunk. */
    @Test
    void streamingOutputFormsSingleAppendingArtifact() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of(new Object()));
        StreamAdapter adapter = raw -> Stream.of(
                AgentExecutionResult.output("part-1 "),
                AgentExecutionResult.output("part-2 "),
                AgentExecutionResult.completed("done"));
        when(handler.resultAdapter()).thenReturn(adapter);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> appendCaptor = ArgumentCaptor.forClass(Boolean.class);
        ArgumentCaptor<Boolean> lastChunkCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(emitter, times(2)).addArtifact(
                anyList(), idCaptor.capture(), anyString(), any(),
                appendCaptor.capture(), lastChunkCaptor.capture());

        assertThat(idCaptor.getAllValues()).containsExactly("task-1-response", "task-1-response");
        assertThat(appendCaptor.getAllValues()).containsExactly(false, true);
        assertThat(lastChunkCaptor.getAllValues()).containsOnly(false);
    }

    /**
     * With trajectory enabled, the executor opens a per-invocation channel, drains it on the
     * supplied executor, and fans lifecycle events to the dedicated JSONL trajectory logger —
     * each carrying the invocation's contextId/taskId propagated to the drain thread via MDC.
     */
    @Test
    void trajectoryEnabled_drainsLifecycleEventsToTrajectoryLoggerWithMdc() throws Exception {
        ch.qos.logback.classic.Logger trajLogger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger("com.huawei.ascend.runtime.trajectory");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        trajLogger.addAppender(appender);
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            TrajectorySettings settings = new TrajectorySettings(TrajectoryLevel.SUMMARY,
                    Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
            AgentEmitter emitter = newEmitter();

            new A2aAgentExecutor(new ToolEmittingHandler(), exec, settings).execute(requestContext(), emitter);

            exec.shutdown();
            assertThat(exec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            assertThat(appender.list).isNotEmpty();
            assertThat(appender.list).allSatisfy(e -> assertThat(e.getMDCPropertyMap())
                    .containsEntry("taskId", "task-1").containsEntry("contextId", "ctx-1"));
            List<String> kinds = appender.list.stream()
                    .map(ILoggingEvent::getArgumentArray)
                    .filter(java.util.Objects::nonNull)
                    .flatMap(java.util.Arrays::stream)
                    .map(String::valueOf)
                    .filter(s -> s.startsWith("kind="))
                    .map(s -> s.substring("kind=".length()))
                    .toList();
            assertThat(kinds).contains("RUN_START", "TOOL_CALL_START", "RUN_END");
        } finally {
            trajLogger.detachAppender(appender);
            exec.shutdownNow();
        }
    }

    /** When the request opts in, the trajectory is delivered to the caller as a second artifact, before terminal. */
    @Test
    void trajectoryNorthbound_deliversTrajectoryArtifactBeforeTerminal() throws Exception {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            TrajectorySettings settings = new TrajectorySettings(TrajectoryLevel.SUMMARY,
                    Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
            AgentEmitter emitter = newEmitter();
            RequestContext ctx = requestContext();
            when(ctx.getMetadata()).thenReturn(Map.of("trajectory.northbound", "true"));

            new A2aAgentExecutor(new ToolEmittingHandler(), exec, settings).execute(ctx, emitter);

            exec.shutdown();
            assertThat(exec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            ArgumentCaptor<List> partsCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
            verify(emitter).addArtifact(partsCaptor.capture(), idCaptor.capture(), anyString(), any(), any(), any());
            assertThat(idCaptor.getValue()).isEqualTo("task-1-trajectory");
            assertThat(partsCaptor.getValue()).isNotEmpty().allMatch(p -> p instanceof DataPart);

            // The trajectory artifact lands before the answer's terminal completion.
            InOrder order = inOrder(emitter);
            order.verify(emitter).addArtifact(anyList(), anyString(), anyString(), any(), any(), any());
            order.verify(emitter).complete(any());
        } finally {
            exec.shutdownNow();
        }
    }

    /** Without the opt-in, the caller gets no trajectory artifact — only the answer terminal. */
    @Test
    void trajectory_noNorthboundByDefault() throws Exception {
        ExecutorService exec = Executors.newSingleThreadExecutor();
        try {
            TrajectorySettings settings = new TrajectorySettings(TrajectoryLevel.SUMMARY,
                    Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
            AgentEmitter emitter = newEmitter();

            new A2aAgentExecutor(new ToolEmittingHandler(), exec, settings).execute(requestContext(), emitter);

            exec.shutdown();
            assertThat(exec.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

            verify(emitter, org.mockito.Mockito.never()).addArtifact(any(), anyString(), anyString(), any(), any(), any());
            verify(emitter).complete(any());
        } finally {
            exec.shutdownNow();
        }
    }

    private static RequestContext requestContext() {
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getTaskId()).thenReturn("task-1");
        when(ctx.getContextId()).thenReturn("ctx-1");
        when(ctx.getMessage()).thenReturn(
                Message.builder().role(Message.Role.ROLE_USER).parts(List.<Part<?>>of(new TextPart("hi"))).build());
        return ctx;
    }

    private static AgentEmitter newEmitter() {
        AgentEmitter emitter = mock(AgentEmitter.class);
        when(emitter.newAgentMessage(anyList(), any())).thenAnswer(inv -> {
            List<Part<?>> parts = inv.getArgument(0);
            return Message.builder().role(Message.Role.ROLE_AGENT).parts(parts).build();
        });
        return emitter;
    }

    /** Capture the Message handed to fail(Message) and concatenate its text. */
    private static String failureText(AgentEmitter emitter) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).fail(captor.capture());
        return captor.getValue().parts().stream()
                .filter(TextPart.class::isInstance)
                .map(p -> ((TextPart) p).text())
                .reduce("", String::concat);
    }

    /** A trajectory-source handler that emits one tool call between RUN_START and RUN_END. */
    private static final class ToolEmittingHandler extends AbstractAgentRuntimeHandler {
        private ToolEmittingHandler() {
            super("agent-x");
        }

        @Override
        protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
            trajectory.emit(TrajectoryDraft.toolCallStart("search", Map.of("q", "hi")));
            return Stream.of("answer");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(o -> AgentExecutionResult.completed(String.valueOf(o)));
        }
    }

    /** Capture the structured error DataPart handed to fail(Message). */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> failureData(AgentEmitter emitter) {
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).fail(captor.capture());
        return captor.getValue().parts().stream()
                .filter(DataPart.class::isInstance)
                .map(p -> (Map<String, Object>) ((DataPart) p).data())
                .findFirst()
                .orElseThrow(() -> new AssertionError("fail(Message) carried no structured DataPart"));
    }
}
