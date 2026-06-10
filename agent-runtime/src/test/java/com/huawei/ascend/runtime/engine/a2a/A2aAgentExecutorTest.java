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
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
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

        assertThat(failureText(emitter)).isEqualTo("RUNTIME_ERROR: boom");
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
}
