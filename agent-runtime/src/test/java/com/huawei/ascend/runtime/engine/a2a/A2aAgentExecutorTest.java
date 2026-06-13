package com.huawei.ascend.runtime.engine.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.spi.AbstractAgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.StreamAdapter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryDraft;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEmitter;
import com.huawei.ascend.runtime.engine.spi.TrajectoryEvent;
import com.huawei.ascend.runtime.engine.spi.TrajectoryMasking;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class A2aAgentExecutorTest {

    /**
     * Context construction throws on wire-controllable input (blank contextId to
     * blank sessionId). That must FAIL the task; an escape after startWork
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

        assertThat(failureText(emitter)).startsWith("INVALID_INPUT:");
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

    /** The header-derived call-context tenant must outrank the client-self-declared params.tenant. */
    @Test
    void transportTenantOutranksClientDeclaredTenant() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of(new Object()));
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.completed("ok"));
        when(handler.resultAdapter()).thenReturn(adapter);

        RequestContext ctx = requestContext();
        when(ctx.getTenant()).thenReturn("client-declared");
        when(ctx.getCallContext()).thenReturn(new org.a2aproject.sdk.server.ServerCallContext(
                null, java.util.Map.of(A2aAgentExecutor.TENANT_STATE_KEY, "transport-tenant"), java.util.Set.of()));

        new A2aAgentExecutor(handler).execute(ctx, newEmitter());

        ArgumentCaptor<com.huawei.ascend.runtime.engine.AgentExecutionContext> captor =
                ArgumentCaptor.forClass(com.huawei.ascend.runtime.engine.AgentExecutionContext.class);
        verify(handler).execute(captor.capture());
        assertThat(captor.getValue().getScope().tenantId()).isEqualTo("transport-tenant");
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
     * sentinel) must still finalize the task; otherwise it stays WORKING forever.
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

        verify(emitter, times(2))
                .addArtifact(anyList(), anyString(), anyString(), any(), any(Boolean.class), any(Boolean.class));
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

    /**
     * While the runtime readiness gate is closed (boot not finished, or drain in
     * progress) executions must be rejected retryable — the client may retry
     * against this or another instance once it is ready.
     */
    @Test
    @SuppressWarnings("unchecked")
    void closedReadinessGate_rejectsExecutionRetryable() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, () -> false).execute(requestContext(), emitter);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).reject(captor.capture());
        Map<String, Object> data = captor.getValue().parts().stream()
                .filter(DataPart.class::isInstance)
                .map(p -> (Map<String, Object>) ((DataPart) p).data())
                .findFirst()
                .orElseThrow(() -> new AssertionError("reject(Message) carried no structured DataPart"));
        assertThat(data).containsEntry("code", "RUNTIME_NOT_READY");
        assertThat(data).containsEntry("retryable", true);
        verify(handler, org.mockito.Mockito.never()).execute(any());
    }

    /** Cancel must reach the handler cooperatively even when nothing is in flight any more. */
    @Test
    void cancelNotifiesHandlerAndCancelsEmitter() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler).cancel(requestContext(), emitter);

        verify(handler).cancel("task-1");
        verify(emitter).cancel();
    }

    /**
     * Cancel of an in-flight execution must close the handler's raw stream (tearing
     * the transport) and the execute thread must NOT report the resulting teardown
     * exception as a task failure — the task already reached CANCELED.
     */
    @Test
    void cancelClosesInFlightStreamAndSuppressesTeardownFailure() throws Exception {
        java.util.concurrent.CountDownLatch streamPulled = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch released = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean rawClosed = new java.util.concurrent.atomic.AtomicBoolean();

        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.generate(() -> {
            streamPulled.countDown();
            try {
                released.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("transport torn down");
        }).onClose(() -> rawClosed.set(true)));
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.output("unreached"));
        when(handler.resultAdapter()).thenReturn(adapter);

        A2aAgentExecutor executor = new A2aAgentExecutor(handler);
        AgentEmitter emitter = newEmitter();
        Thread executeThread = new Thread(() -> executor.execute(requestContext(), emitter), "a2a-test-execute");
        executeThread.start();
        assertThat(streamPulled.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        executor.cancel(requestContext(), emitter);
        released.countDown();
        executeThread.join(5_000);

        assertThat(executeThread.isAlive()).isFalse();
        assertThat(rawClosed).isTrue();
        verify(handler).cancel("task-1");
        verify(emitter).cancel();
        verify(emitter, org.mockito.Mockito.never()).fail(any(Message.class));
    }

    /**
     * A cancel that lands while the handler is still connecting (before it has
     * returned a stream) must still reach the cancelled flag: the execute
     * thread then tears its own stream down instead of streaming results into
     * the CANCELED task and misreporting the teardown as INTERNAL.
     */
    @Test
    void cancelDuringHandlerConnect_isNotLost() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        java.util.concurrent.atomic.AtomicBoolean consumed = new java.util.concurrent.atomic.AtomicBoolean();
        StreamAdapter adapter = raw -> raw.map(o -> {
            consumed.set(true);
            return AgentExecutionResult.completed("late");
        });
        when(handler.resultAdapter()).thenReturn(adapter);

        java.util.concurrent.atomic.AtomicReference<A2aAgentExecutor> executorRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        RequestContext ctx = requestContext();
        AgentEmitter cancelEmitter = newEmitter();
        when(handler.execute(any())).thenAnswer(inv -> {
            // The cancel request arrives while the handler is still connecting.
            executorRef.get().cancel(ctx, cancelEmitter);
            return Stream.of(new Object());
        });

        A2aAgentExecutor executor = new A2aAgentExecutor(handler);
        executorRef.set(executor);
        AgentEmitter executeEmitter = newEmitter();
        executor.execute(ctx, executeEmitter);

        verify(cancelEmitter).cancel();
        verify(handler).cancel("task-1");
        assertThat(consumed).as("no result may stream into the CANCELED task").isFalse();
        verify(executeEmitter, org.mockito.Mockito.never()).fail(any(Message.class));
        verify(executeEmitter, org.mockito.Mockito.never()).complete();
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

    /** Streamed OUTPUT chunks must form one growing artifact: same id, append false to true, never last-chunk. */
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
     * With trajectory enabled, the executor wires the factory sinks and the handler feeds them
     * synchronously — every lifecycle event arrives with full correlation before execute returns.
     */
    @Test
    void trajectoryEnabled_feedsFactorySinksSynchronouslyWithCorrelation() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        List<TrajectoryEvent> events = new ArrayList<>();
        TrajectorySinkFactory factory = () -> events::add;
        AgentEmitter emitter = newEmitter();

        new A2aAgentExecutor(new ToolEmittingHandler(), settings, List.of(factory))
                .execute(requestContext(), emitter);

        assertThat(events).isNotEmpty();
        assertThat(events).allSatisfy(e -> {
            assertThat(e.taskId()).isEqualTo("task-1");
            assertThat(e.contextId()).isEqualTo("ctx-1");
        });
        assertThat(events).extracting(e -> String.valueOf(e.kind()))
                .contains("RUN_START", "TOOL_CALL_START", "RUN_END");
    }

    /** When the request opts in, the trajectory is delivered to the caller as a second artifact, before terminal. */
    @Test
    void trajectoryNorthbound_deliversTrajectoryArtifactBeforeTerminal() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        AgentEmitter emitter = newEmitter();
        RequestContext ctx = requestContext();
        when(ctx.getMetadata()).thenReturn(Map.of("trajectory.northbound", "true"));

        new A2aAgentExecutor(new ToolEmittingHandler(), settings, List.of()).execute(ctx, emitter);

        ArgumentCaptor<List> partsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(emitter).addArtifact(partsCaptor.capture(), idCaptor.capture(), anyString(), any(), any(), any());
        assertThat(idCaptor.getValue()).isEqualTo("task-1-trajectory");
        assertThat(partsCaptor.getValue()).isNotEmpty().allMatch(p -> p instanceof DataPart);

        // The trajectory artifact lands before the answer's terminal completion.
        InOrder order = inOrder(emitter);
        order.verify(emitter).addArtifact(anyList(), anyString(), anyString(), any(), any(), any());
        order.verify(emitter).complete(any());
    }

    /** Without the opt-in (and with no factory sinks), the caller gets no trajectory artifact. */
    @Test
    void trajectory_noNorthboundByDefault() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        AgentEmitter emitter = newEmitter();

        new A2aAgentExecutor(new ToolEmittingHandler(), settings, List.of()).execute(requestContext(), emitter);

        verify(emitter, org.mockito.Mockito.never()).addArtifact(any(), anyString(), anyString(), any(), any(), any());
        verify(emitter).complete(any());
    }

    /** A request can opt a single call out via the legacy trajectory.level=off metadata key. */
    @Test
    void trajectoryLevelOffMetadataOptsTheRequestOut() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        List<TrajectoryEvent> events = new ArrayList<>();
        TrajectorySinkFactory factory = () -> events::add;
        AgentEmitter emitter = newEmitter();
        RequestContext ctx = requestContext();
        when(ctx.getMetadata()).thenReturn(Map.of("trajectory.level", "off"));

        new A2aAgentExecutor(new ToolEmittingHandler(), settings, List.of(factory)).execute(ctx, emitter);

        assertThat(events).isEmpty();
        verify(emitter).complete(any());
    }

    /**
     * Logs are the first triage surface in a multi-tenant deployment: every line inside the
     * execute window must be attributable to tenant+agent, not only to context+task, and the
     * keys must not leak into the next task served by the same pooled thread.
     */
    @Test
    void executeScopesFourMdcKeysAndClearsThemAfter() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        java.util.concurrent.atomic.AtomicReference<Map<String, String>> mdcDuringExecute =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(handler.execute(any())).thenAnswer(inv -> {
            mdcDuringExecute.set(org.slf4j.MDC.getCopyOfContextMap());
            return Stream.of(new Object());
        });
        StreamAdapter adapter = raw -> raw.map(o -> AgentExecutionResult.completed("ok"));
        when(handler.resultAdapter()).thenReturn(adapter);

        new A2aAgentExecutor(handler).execute(requestContext(), newEmitter());

        assertThat(mdcDuringExecute.get())
                .containsEntry("contextId", "ctx-1")
                .containsEntry("taskId", "task-1")
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("agentId", "agent-x")
                .doesNotContainKeys("traceId", "spanId");
        assertThat(org.slf4j.MDC.get("contextId")).isNull();
        assertThat(org.slf4j.MDC.get("taskId")).isNull();
        assertThat(org.slf4j.MDC.get("tenantId")).isNull();
        assertThat(org.slf4j.MDC.get("agentId")).isNull();
    }

    /**
     * A run that goes through a remote tool leg must not lose the second half of its
     * trajectory: the northbound artifact spans both legs and closes with the resume
     * leg's RUN_END, and it still lands before the answer's terminal state.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void remoteResumeLeg_isCapturedInNorthboundTrajectoryClosedByRunEnd() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        RemoteRequestingTrajectoryHandler handler = new RemoteRequestingTrajectoryHandler();
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote done")));
        AgentEmitter emitter = newEmitter();
        RequestContext ctx = requestContext();
        when(ctx.getMetadata()).thenReturn(Map.of("trajectory.northbound", "true"));

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound), () -> true,
                settings, List.of()).execute(ctx, emitter);

        ArgumentCaptor<List> partsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(emitter).addArtifact(partsCaptor.capture(), idCaptor.capture(), anyString(), any(), any(), any());
        assertThat(idCaptor.getValue()).isEqualTo("task-1-trajectory");
        List<String> kinds = ((List<?>) partsCaptor.getValue()).stream()
                .map(p -> String.valueOf(((Map<String, Object>) ((DataPart) p).data()).get("kind")))
                .toList();
        assertThat(kinds).as("both legs emit their lifecycle").filteredOn("RUN_START"::equals).hasSize(2);
        assertThat(kinds.get(kinds.size() - 1)).isEqualTo("RUN_END");

        // Both legs ran with a live emitter — the resume leg's NOOP default would have
        // silently skipped rail registration and dropped every resume-leg event.
        assertThat(handler.legEmitters).hasSize(2).allMatch(e -> e != TrajectoryEmitter.NOOP);

        InOrder order = inOrder(emitter);
        order.verify(emitter).addArtifact(anyList(), anyString(), anyString(), any(), any(), any());
        order.verify(emitter).complete(any(Message.class));
    }

    /**
     * A continuation request (task parked on remote INPUT_REQUIRED, user follow-up arrives)
     * is a fresh invocation with no prior flow: its northbound opt-in must open a trajectory
     * for the resume leg instead of being silently ignored.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void remoteContinuation_withNorthboundOptIn_deliversResumeLegTrajectoryArtifact() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        RemoteRequestingTrajectoryHandler handler = new RemoteRequestingTrajectoryHandler();
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));
        AgentEmitter emitter = newEmitter();
        RequestContext ctx = remoteContinuationContext();
        when(ctx.getMetadata()).thenReturn(Map.of("trajectory.northbound", "true"));

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound), () -> true,
                settings, List.of()).execute(ctx, emitter);

        ArgumentCaptor<List> partsCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
        verify(emitter).addArtifact(partsCaptor.capture(), idCaptor.capture(), anyString(), any(), any(), any());
        assertThat(idCaptor.getValue()).isEqualTo("task-1-trajectory");
        List<String> kinds = ((List<?>) partsCaptor.getValue()).stream()
                .map(p -> String.valueOf(((Map<String, Object>) ((DataPart) p).data()).get("kind")))
                .toList();
        assertThat(kinds).isNotEmpty().contains("RUN_START");
        assertThat(kinds.get(kinds.size() - 1)).isEqualTo("RUN_END");
        assertThat(handler.legEmitters).hasSize(1).allMatch(e -> e != TrajectoryEmitter.NOOP);
        verify(emitter).complete(any(Message.class));
    }

    /**
     * A continuation leg flushes into the same {@code -trajectory} artifact the parked
     * leg already wrote; the SDK replaces an existing artifact on append=false, so the
     * second flush must append or the parked leg's events vanish from the task snapshot.
     */
    @Test
    void remoteContinuation_trajectoryFlushAppendsWhenParkedTaskAlreadyCarriesTheArtifact() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        RemoteRequestingTrajectoryHandler handler = new RemoteRequestingTrajectoryHandler();
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));
        AgentEmitter emitter = newEmitter();
        RequestContext ctx = remoteContinuationContext(List.of(Artifact.builder()
                .artifactId("task-1-trajectory")
                .name("agent-trajectory")
                .parts(List.<Part<?>>of(new TextPart("first leg")))
                .build()));
        when(ctx.getMetadata()).thenReturn(Map.of("trajectory.northbound", "true"));

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound), () -> true,
                settings, List.of()).execute(ctx, emitter);

        ArgumentCaptor<Boolean> appendCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(emitter).addArtifact(anyList(), eq("task-1-trajectory"), anyString(), any(),
                appendCaptor.capture(), eq(true));
        assertThat(appendCaptor.getValue()).isTrue();
        verify(emitter).complete(any(Message.class));
    }

    /**
     * A continuation whose parked task carries no trajectory artifact (the first leg did
     * not opt into northbound) must open the artifact — an append to a missing artifact
     * would drop the chunk.
     */
    @Test
    void remoteContinuation_trajectoryFlushOpensArtifactWhenParkedTaskCarriesNone() {
        TrajectorySettings settings = new TrajectorySettings(true,
                Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        RemoteRequestingTrajectoryHandler handler = new RemoteRequestingTrajectoryHandler();
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));
        AgentEmitter emitter = newEmitter();
        RequestContext ctx = remoteContinuationContext();
        when(ctx.getMetadata()).thenReturn(Map.of("trajectory.northbound", "true"));

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound), () -> true,
                settings, List.of()).execute(ctx, emitter);

        ArgumentCaptor<Boolean> appendCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(emitter).addArtifact(anyList(), eq("task-1-trajectory"), anyString(), any(),
                appendCaptor.capture(), eq(true));
        assertThat(appendCaptor.getValue()).isFalse();
    }

    /**
     * The answer stream has the same replace-on-append-false hazard: a continuation
     * leg's first OUTPUT chunk must append to the {@code -response} artifact the parked
     * leg already streamed instead of replacing it.
     */
    @Test
    void remoteContinuation_firstOutputChunkAppendsWhenParkedTaskCarriesResponseArtifact() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("resumed"));
        when(handler.resultAdapter()).thenReturn(raw -> Stream.of(
                AgentExecutionResult.output("second leg "),
                AgentExecutionResult.completed("done")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));
        AgentEmitter emitter = newEmitter();
        RequestContext ctx = remoteContinuationContext(List.of(Artifact.builder()
                .artifactId("task-1-response")
                .name("agent-response")
                .parts(List.<Part<?>>of(new TextPart("first leg ")))
                .build()));

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(ctx, emitter);

        ArgumentCaptor<Boolean> appendCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(emitter).addArtifact(anyList(), eq("task-1-response"), anyString(), any(),
                appendCaptor.capture(), any(Boolean.class));
        assertThat(appendCaptor.getValue()).isTrue();
        verify(emitter).complete(any(Message.class));
    }

    /** A continuation whose parked task carries no response artifact opens one (append=false). */
    @Test
    void remoteContinuation_firstOutputChunkOpensResponseArtifactWhenParkedTaskCarriesNone() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("resumed"));
        when(handler.resultAdapter()).thenReturn(raw -> Stream.of(
                AgentExecutionResult.output("second leg "),
                AgentExecutionResult.completed("done")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));
        AgentEmitter emitter = newEmitter();

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(remoteContinuationContext(), emitter);

        ArgumentCaptor<Boolean> appendCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(emitter).addArtifact(anyList(), eq("task-1-response"), anyString(), any(),
                appendCaptor.capture(), any(Boolean.class));
        assertThat(appendCaptor.getValue()).isFalse();
        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteInvocationInvokesOutboundThenReentersLocalHandlerWithToolResult() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            if ("REMOTE_RESUME".equals(context.getInputType())) {
                assertThat(context.getVariables()).containsEntry("runtime.remoteToolResult", "remote done");
                return Stream.of("resumed");
            }
            return Stream.of("remote");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> "remote".equals(value)
                    ? remoteInterrupt(new AgentExecutionResult.RemoteInvocation(
                            "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                            "task-1", "ctx-1", "task-1", Map.of("message", "hello remote")))
                    : AgentExecutionResult.completed("local final after remote")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE, "remote progress"),
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote done")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(requestContext(), emitter);

        assertThat(outbound.requests).hasSize(1);
        assertThat(outbound.requests.get(0).message()).isEqualTo("hello remote");
        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteProgressIsProjectedWhileOutboundInvocationIsStillRunning() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            return Stream.of("REMOTE_RESUME".equals(context.getInputType()) ? "resumed" : "remote");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> "remote".equals(value)
                ? remoteInterrupt(new AgentExecutionResult.RemoteInvocation(
                        "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                        "task-1", "ctx-1", "conversation-1", Map.of("message", "hello remote")))
                : AgentExecutionResult.completed("local final after remote")));

        AtomicBoolean projectedBeforeOutboundReturned = new AtomicBoolean(false);
        AgentEmitter emitter = newEmitter();
        doAnswer(inv -> {
            projectedBeforeOutboundReturned.set(true);
            return null;
        }).when(emitter).addArtifact(anyList());

        RemoteAgentInvocationService.OutboundPort outbound = new RemoteAgentInvocationService.OutboundPort() {
            @Override
            public List<RemoteAgentInvocationService.RemoteAgentResult> invoke(
                    RemoteAgentInvocationService.RemoteAgentRequest request,
                    Consumer<RemoteAgentInvocationService.RemoteAgentResult> eventConsumer) {
                eventConsumer.accept(remoteResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE, "remote progress"));
                assertThat(projectedBeforeOutboundReturned).isTrue();
                return List.of(remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote done"));
            }

            @Override
            public void cancel(RemoteAgentInvocationService.RemoteTaskReference reference) {
            }
        };

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(requestContext(), emitter);

        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteInputRequiredDoesNotReenterLocalHandlerAndUsesNonFinalInputRequired() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("remote"));
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> remoteInterrupt(
                new AgentExecutionResult.RemoteInvocation(
                    "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                    "task-1", "ctx-1", "task-1", Map.of("message", "needs user")))));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED,
                        "remote asks for more",
                        "remote-task-1",
                        "remote-ctx-1",
                        Map.of())));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(requestContext(), emitter);

        ArgumentCaptor<TaskStatusUpdateEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusUpdateEvent.class);
        verify(emitter).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().status().state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED);
        assertThat(eventCaptor.getValue().isFinal()).isFalse();
        assertThat(eventCaptor.getValue().metadata())
                .containsEntry("runtime.waitingTarget", "REMOTE_AGENT")
                .containsEntry("runtime.remoteTaskId", "remote-task-1")
                .containsEntry("runtime.remoteContextId", "remote-ctx-1")
                .containsEntry("runtime.toolCallId", "tool-call-1")
                .containsEntry("runtime.remoteInvocationId", "tool-call-1");
        org.mockito.Mockito.verify(handler, org.mockito.Mockito.times(1)).execute(any());
    }

    @Test
    void remoteInvocationWithoutTerminalResultReentersLocalHandlerWithToolError() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            if ("REMOTE_RESUME".equals(context.getInputType())) {
                assertThat(context.getVariables().get("runtime.remoteToolResult").toString())
                        .contains("REMOTE_TERMINAL_RESULT_MISSING");
                return Stream.of("resumed");
            }
            return Stream.of("remote");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> "remote".equals(value)
                ? remoteInterrupt(new AgentExecutionResult.RemoteInvocation(
                        "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                        "task-1", "ctx-1", "conversation-1", Map.of("message", "hello remote")))
                : AgentExecutionResult.completed("local final after missing terminal")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.MESSAGE, "remote progress only")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(requestContext(), emitter);

        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteContinuationResumesExistingRemoteTaskWithoutCallingLocalHandlerFirst() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> {
            var context = (com.huawei.ascend.runtime.engine.AgentExecutionContext) inv.getArgument(0);
            assertThat(context.getInputType()).isEqualTo("REMOTE_RESUME");
            assertThat(context.getVariables()).containsEntry("runtime.remoteToolResult", "remote final");
            assertThat(context.getScope().tenantId()).isEqualTo("tenant-a");
            assertThat(context.getScope().userId()).isEqualTo("user-a");
            return Stream.of("resumed");
        });
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> AgentExecutionResult.completed("local final")));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(remoteContinuationContext(), emitter);

        assertThat(outbound.requests).hasSize(1);
        RemoteAgentInvocationService.RemoteAgentRequest request = outbound.requests.get(0);
        assertThat(request.remoteAgentId()).isEqualTo("remote-agent");
        assertThat(request.remoteTaskId()).isEqualTo("remote-task-1");
        assertThat(request.remoteContextId()).isEqualTo("remote-ctx-1");
        assertThat(request.toolCallId()).isEqualTo("tool-call-1");
        assertThat(request.message()).isEqualTo("user follow-up");
        verify(emitter).complete(any(Message.class));
    }

    @Test
    void remoteContinuationMissingRouteMetadataFailsWithoutCallingRemote() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "should not be called")));

        RequestContext ctx = requestContext();
        when(ctx.getTask()).thenReturn(new Task(
                "task-1",
                "ctx-1",
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED),
                List.of(),
                List.of(),
                Map.of("runtime.waitingTarget", "REMOTE_AGENT")));
        AgentEmitter emitter = newEmitter();

        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(ctx, emitter);

        assertThat(outbound.requests).isEmpty();
        assertThat(failureText(emitter)).contains("REMOTE_ROUTE_METADATA_MISSING");
    }

    @Test
    void nestedRemoteInvocationAfterRemoteResumeFailsParentTask() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("resumed"));
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> remoteInterrupt(
                new AgentExecutionResult.RemoteInvocation(
                        "remote-agent-2",
                        "a2a_remote_remote_agent_2",
                        "tool-call-2",
                        "task-1",
                        "ctx-1",
                        "conversation-1",
                        Map.of("message", "nested")))));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                remoteResult(RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED, "remote final")));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(remoteContinuationContext(), emitter);

        assertThat(outbound.requests).hasSize(1);
        assertThat(failureText(emitter))
                .isEqualTo("NESTED_REMOTE_INVOCATION_UNSUPPORTED: remote A2A invocation after REMOTE_RESUME is not supported");
    }

    @Test
    void inputRequiredMetadataMergesRemoteResultMetadata() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("remote"));
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> remoteInterrupt(
                new AgentExecutionResult.RemoteInvocation(
                    "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                    "task-1", "ctx-1", "task-1", Map.of("message", "needs user")))));
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of(
                new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.INPUT_REQUIRED,
                        "remote asks for more",
                        "remote-task-1",
                        "remote-ctx-1",
                        Map.of("remote.promptVersion", "v2"))));

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .execute(requestContext(), emitter);

        ArgumentCaptor<TaskStatusUpdateEvent> eventCaptor = ArgumentCaptor.forClass(TaskStatusUpdateEvent.class);
        verify(emitter).emitEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().metadata())
                .containsEntry("runtime.waitingTarget", "REMOTE_AGENT")
                .containsEntry("runtime.remoteTaskId", "remote-task-1")
                .containsEntry("remote.promptVersion", "v2");
    }

    /**
     * A cancel landing while the outbound remote leg is still running (after the
     * local stream drained, before the remote returned) must reach the cancelled
     * flag: the remote return must not re-enter the local handler against the
     * CANCELED task, the terminal must not be overwritten, and the remote task
     * gets a best-effort cancel.
     */
    @Test
    void cancelDuringRemoteSegmentSkipsLocalResumeAndCancelsRemoteTask() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        when(handler.execute(any())).thenAnswer(inv -> Stream.of("remote"));
        when(handler.resultAdapter()).thenReturn(raw -> raw.map(value -> AgentExecutionResult.interrupted(
                new AgentExecutionResult.RemoteInvocation(
                        "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                        "task-1", "ctx-1", "conversation-1", Map.of("message", "hello remote")))));

        RequestContext ctx = requestContext();
        AgentEmitter cancelEmitter = newEmitter();
        java.util.concurrent.atomic.AtomicReference<A2aAgentExecutor> executorRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        List<RemoteAgentInvocationService.RemoteTaskReference> canceled = new ArrayList<>();
        RemoteAgentInvocationService.OutboundPort outbound = new RemoteAgentInvocationService.OutboundPort() {
            @Override
            public List<RemoteAgentInvocationService.RemoteAgentResult> invoke(
                    RemoteAgentInvocationService.RemoteAgentRequest request,
                    Consumer<RemoteAgentInvocationService.RemoteAgentResult> eventConsumer) {
                // The cancel request lands while the remote leg is still running.
                executorRef.get().cancel(ctx, cancelEmitter);
                return List.of(new RemoteAgentInvocationService.RemoteAgentResult(
                        RemoteAgentInvocationService.RemoteAgentResult.Type.COMPLETED,
                        "remote done", "remote-task-1", "remote-ctx-1", Map.of()));
            }

            @Override
            public void cancel(RemoteAgentInvocationService.RemoteTaskReference reference) {
                canceled.add(reference);
            }
        };

        A2aAgentExecutor executor =
                new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound));
        executorRef.set(executor);
        AgentEmitter executeEmitter = newEmitter();
        executor.execute(ctx, executeEmitter);

        verify(cancelEmitter).cancel();
        verify(handler, times(1)).execute(any());
        verify(executeEmitter, org.mockito.Mockito.never()).complete();
        verify(executeEmitter, org.mockito.Mockito.never()).complete(any(Message.class));
        verify(executeEmitter, org.mockito.Mockito.never()).fail(any(Message.class));
        assertThat(canceled).hasSize(1);
        assertThat(canceled.get(0).remoteAgentId()).isEqualTo("remote-agent");
        assertThat(canceled.get(0).remoteTaskId()).isEqualTo("remote-task-1");
    }

    @Test
    void cancelPropagatesToRemoteTaskWhenParentIsWaitingForRemoteAgent() {
        AgentRuntimeHandler handler = mock(AgentRuntimeHandler.class);
        when(handler.agentId()).thenReturn("agent-x");
        RecordingRemoteOutbound outbound = new RecordingRemoteOutbound(List.of());

        AgentEmitter emitter = newEmitter();
        new A2aAgentExecutor(handler, new RemoteAgentInvocationService(outbound))
                .cancel(remoteContinuationContext(), emitter);

        verify(emitter).cancel();
        assertThat(outbound.canceled).hasSize(1);
        assertThat(outbound.canceled.get(0).remoteAgentId()).isEqualTo("remote-agent");
        assertThat(outbound.canceled.get(0).remoteTaskId()).isEqualTo("remote-task-1");
        assertThat(outbound.canceled.get(0).remoteContextId()).isEqualTo("remote-ctx-1");
    }

    private static RequestContext requestContext() {
        RequestContext ctx = mock(RequestContext.class);
        when(ctx.getTaskId()).thenReturn("task-1");
        when(ctx.getContextId()).thenReturn("ctx-1");
        when(ctx.getTenant()).thenReturn("tenant-a");
        when(ctx.getMetadata()).thenReturn(Map.of("userId", "user-a", "agentId", "agent-x"));
        when(ctx.getMessage()).thenReturn(
                Message.builder().role(Message.Role.ROLE_USER).parts(List.<Part<?>>of(new TextPart("hi"))).build());
        return ctx;
    }

    private static RequestContext remoteContinuationContext() {
        RequestContext ctx = requestContext();
        when(ctx.getMessage()).thenReturn(Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.<Part<?>>of(new TextPart("user follow-up")))
                .build());
        when(ctx.getTask()).thenReturn(new Task(
                "task-1",
                "ctx-1",
                new TaskStatus(TaskState.TASK_STATE_INPUT_REQUIRED),
                List.of(),
                List.of(),
                Map.of(
                        "runtime.waitingTarget", "REMOTE_AGENT",
                        "runtime.remoteAgentId", "remote-agent",
                        "runtime.remoteTaskId", "remote-task-1",
                        "runtime.remoteContextId", "remote-ctx-1",
                        "runtime.toolCallId", "tool-call-1",
                        "runtime.localConversationId", "conversation-1")));
        return ctx;
    }

    /** A continuation context whose parked task already carries the given artifacts. */
    private static RequestContext remoteContinuationContext(List<Artifact> parkedArtifacts) {
        RequestContext ctx = remoteContinuationContext();
        Task parked = ctx.getTask();
        when(ctx.getTask()).thenReturn(new Task(parked.id(), parked.contextId(), parked.status(),
                parkedArtifacts, parked.history(), parked.metadata()));
        return ctx;
    }

    private static AgentEmitter newEmitter() {
        AgentEmitter emitter = mock(AgentEmitter.class);
        when(emitter.getTaskId()).thenReturn("task-1");
        when(emitter.getContextId()).thenReturn("ctx-1");
        when(emitter.newAgentMessage(anyList(), any())).thenAnswer(inv -> {
            List<Part<?>> parts = inv.getArgument(0);
            return Message.builder().role(Message.Role.ROLE_AGENT).parts(parts).build();
        });
        return emitter;
    }

    private static RemoteAgentInvocationService.RemoteAgentResult remoteResult(
            RemoteAgentInvocationService.RemoteAgentResult.Type type, String text) {
        return new RemoteAgentInvocationService.RemoteAgentResult(type, text, null, null, Map.of());
    }

    private static AgentExecutionResult remoteInterrupt(AgentExecutionResult.RemoteInvocation invocation) {
        AgentExecutionResult result = AgentExecutionResult.interrupted(invocation);
        assertThat(result.interruptPayload()).isInstanceOf(AgentExecutionResult.RemoteAgentInterrupt.class);
        return result;
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

    /**
     * A trajectory-source handler whose first leg requests a remote tool and whose resume
     * leg completes; records the emitter each leg ran with.
     */
    private static final class RemoteRequestingTrajectoryHandler extends AbstractAgentRuntimeHandler {
        private final List<TrajectoryEmitter> legEmitters = new ArrayList<>();

        private RemoteRequestingTrajectoryHandler() {
            super("agent-x");
        }

        @Override
        protected Stream<?> doExecute(AgentExecutionContext context, TrajectoryEmitter trajectory) {
            legEmitters.add(trajectory);
            return Stream.of("REMOTE_RESUME".equals(context.getInputType()) ? "resumed" : "remote");
        }

        @Override
        public StreamAdapter resultAdapter() {
            return raw -> raw.map(value -> "remote".equals(value)
                    ? AgentExecutionResult.interrupted(new AgentExecutionResult.RemoteInvocation(
                            "remote-agent", "a2a_remote_remote_agent", "tool-call-1",
                            "task-1", "ctx-1", "conversation-1", Map.of("message", "hello remote")))
                    : AgentExecutionResult.completed("local final after remote"));
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

    private static final class RecordingRemoteOutbound implements RemoteAgentInvocationService.OutboundPort {
        private final List<RemoteAgentInvocationService.RemoteAgentResult> results;
        private final List<RemoteAgentInvocationService.RemoteAgentRequest> requests = new ArrayList<>();
        private final List<RemoteAgentInvocationService.RemoteTaskReference> canceled = new ArrayList<>();

        private RecordingRemoteOutbound(List<RemoteAgentInvocationService.RemoteAgentResult> results) {
            this.results = results;
        }

        @Override
        public List<RemoteAgentInvocationService.RemoteAgentResult> invoke(
                RemoteAgentInvocationService.RemoteAgentRequest request,
                Consumer<RemoteAgentInvocationService.RemoteAgentResult> eventConsumer) {
            requests.add(request);
            if (eventConsumer != null) {
                results.forEach(eventConsumer);
            }
            return results;
        }

        @Override
        public void cancel(RemoteAgentInvocationService.RemoteTaskReference reference) {
            canceled.add(reference);
        }
    }
}
