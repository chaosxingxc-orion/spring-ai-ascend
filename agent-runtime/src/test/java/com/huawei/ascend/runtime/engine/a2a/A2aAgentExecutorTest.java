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

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import java.util.List;
import java.util.Map;
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

    /**
     * Context construction throws on wire-controllable input (blank contextId →
     * blank sessionId). That must FAIL the task — an escape after startWork
     * strands it in WORKING forever.
     */
    @Test
    void malformedRequestContext_failsTaskInsteadOfStrandingWorking() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getTaskId()).thenReturn("task-1");
        when(ctx.getContextId()).thenReturn("   ");

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(ctx, emitter);

        assertThat(failureText(emitter)).startsWith("RUNTIME_ERROR:");
    }

    /**
     * The framework conversation key must follow the A2A contextId (session), not the
     * taskId: every message/send opens a new task in the same context, so a task-keyed
     * conversation would reset framework checkpointer state on every turn.
     */
    @Test
    void agentStateKeyFollowsContextIdAcrossTasks() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of(new Object()));
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.completed("ok"));
        when(handler.resultAdapter()).thenReturn(adapter);

        new A2aAgentExecutor(handler).execute(requestContext(), newEmitter());

        ArgumentCaptor<com.huawei.ascend.runtime.engine.AgentExecutionContext> captor =
                ArgumentCaptor.forClass(com.huawei.ascend.runtime.engine.AgentExecutionContext.class);
        verify(handler).execute(captor.capture());
        assertThat(captor.getValue().getAgentStateKey()).isEqualTo("ctx-1");
    }

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

    /**
     * An empty handler stream (e.g. upstream replies 204, or 200 with only a [DONE]
     * sentinel) must still finalize the task — otherwise it stays WORKING forever.
     */
    @Test
    void emptyResultStream_finalizesTask() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.empty());
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.output("unreached"));
        when(handler.resultAdapter()).thenReturn(adapter);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        verify(emitter).complete();
    }

    /** A stream of only OUTPUT results (no terminal COMPLETED/FAILED) must also finalize. */
    @Test
    void outputOnlyStream_finalizesTask() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of(new Object(), new Object()));
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.output("chunk"));
        when(handler.resultAdapter()).thenReturn(adapter);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).execute(requestContext(), emitter);

        verify(emitter, times(2)).addArtifact(anyList(), anyString(), anyString(), any(), any(Boolean.class), any(Boolean.class));
        verify(emitter).complete();
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
