package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.A2aResultRouter.RouteDecision;
import com.huawei.ascend.runtime.engine.a2a.A2aTrajectorySupport.TrajectoryFlow;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Bridges the A2A SDK's {@link AgentExecutor} to the {@link AgentRuntimeHandler} SPI: owns the
 * task lifecycle states, the readiness gate, in-flight registration for cancel-through, and the
 * consumption of the handler's result stream. Trajectory wiring, result routing, and remote-tool
 * orchestration are delegated to package-private collaborators invoked synchronously on the
 * execute thread; the single-writer {@link AgentEmitter} is only ever passed down, never stored.
 */
public final class A2aAgentExecutor implements AgentExecutor {

    /** Request-level metadata key for the ordinary tenant passthrough value. */
    public static final String TENANT_STATE_KEY = "tenantId";

    private static final Logger LOG = LoggerFactory.getLogger(A2aAgentExecutor.class);
    private static final String MDC_CONTEXT_ID = "contextId";
    private static final String MDC_TASK_ID = "taskId";
    private static final String MDC_TENANT_ID = "tenantId";
    private static final String MDC_AGENT_ID = "agentId";
    private static final String USER_ID_METADATA_KEY = "userId";
    private static final String AGENT_ID_METADATA_KEY = "agentId";
    private static final String MEMORY_SCOPE_METADATA_KEY = "memoryScope";
    private static final String CORRELATION_ID_METADATA_KEY = "correlationId";
    private static final String TRACE_ID_METADATA_KEY = "traceId";

    /** Version of the structured-error payload carried on the failure DataPart/metadata. */
    private static final String ERROR_SCHEMA_VERSION = "1";

    private final AgentRuntimeHandler handler;
    private final BooleanSupplier readiness;
    private final A2aTrajectorySupport trajectory;
    private final A2aRemoteInvocationOrchestrator remote;
    private final ConcurrentHashMap<String, InFlightExecution> inFlight = new ConcurrentHashMap<>();

    public A2aAgentExecutor(AgentRuntimeHandler handler) {
        this(handler, null, () -> true, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteAgentInvocationService remoteInvocationService) {
        this(handler, remoteInvocationService, () -> true, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, BooleanSupplier readiness) {
        this(handler, null, readiness, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteAgentInvocationService remoteInvocationService,
            BooleanSupplier readiness) {
        this(handler, remoteInvocationService, readiness, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, TrajectorySettings defaultTrajectorySettings,
            List<TrajectorySinkFactory> sinkFactories) {
        this(handler, null, () -> true, defaultTrajectorySettings, sinkFactories);
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteAgentInvocationService remoteInvocationService,
            BooleanSupplier readiness, TrajectorySettings defaultTrajectorySettings,
            List<TrajectorySinkFactory> sinkFactories) {
        this.handler = handler;
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.trajectory = new A2aTrajectorySupport(defaultTrajectorySettings, sinkFactories);
        this.remote = new A2aRemoteInvocationOrchestrator(
                remoteInvocationService,
                new A2aParentTaskProjector(),
                handler != null ? handler.agentId() : null);
    }

    /**
     * Cancel state for one in-flight execution. The stream slot is empty while the
     * handler is still connecting; a cancel in that window sets the flag and the
     * execute thread tears its own stream down once the handler returns it.
     */
    private record InFlightExecution(AtomicReference<Stream<?>> rawStream, AtomicBoolean cancelled) {
    }

    @Override
    public void execute(RequestContext ctx, AgentEmitter emitter) {
        String taskId = ctx.getTaskId();
        if (handler == null) {
            LOG.warn("[A2A] no handler registered taskId={}", taskId);
            emitter.reject(failureMessage(emitter, "NO_HANDLER",
                    "no agent handler registered for this task", false));
            LOG.info("[A2A] task state=REJECTED taskId={}", taskId);
            return;
        }
        if (!readiness.getAsBoolean()) {
            // Boot has not finished or a drain is in progress: the handler may be
            // mid start/stop, so executing now could run against half-open
            // resources. Retryable - the client may try again once ready.
            LOG.warn("[A2A] runtime not ready taskId={}", taskId);
            emitter.reject(failureMessage(emitter, "RUNTIME_NOT_READY",
                    "runtime is not accepting executions", true));
            LOG.info("[A2A] task state=REJECTED taskId={}", taskId);
            return;
        }
        long startedNanos = System.nanoTime();
        String sessionId = ctx.getContextId();
        String agentId = handler.agentId();
        MDC.put(MDC_CONTEXT_ID, sessionId != null ? sessionId : "");
        MDC.put(MDC_TASK_ID, taskId != null ? taskId : "");
        MDC.put(MDC_TENANT_ID, metadata(ctx, TENANT_STATE_KEY, "default"));
        MDC.put(MDC_AGENT_ID, agentId != null ? agentId : "");
        // Per-task local state (this bean is a shared singleton - never hoist to a field).
        // A continuation leg (task parked on remote INPUT_REQUIRED) re-enters execute with
        // artifacts the first leg already flushed; the SDK replaces an existing artifact on
        // append=false, so both streams must append when their artifact is already on the
        // task snapshot — and must not append when it is absent (an append to a missing
        // artifact drops the chunk).
        String artifactId = taskId + "-response";
        String trajectoryArtifactId = taskId + "-trajectory";
        AtomicBoolean firstArtifact = new AtomicBoolean(!hasArtifact(ctx.getTask(), artifactId));
        boolean appendTrajectory = hasArtifact(ctx.getTask(), trajectoryArtifactId);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        // Registered before any handler/remote work so cancel() always finds the flag,
        // even in the window between consumeHandler stream drain and runRemoteSegment
        // registration — the window the old per-method inFlight.put/remove created.
        InFlightExecution execution = new InFlightExecution(new AtomicReference<>(), cancelled);
        inFlight.put(taskId, execution);
        // Holder, not a local: the resume legs re-open the flow from inside the remote
        // orchestration callback, and the catch/finally must see the latest wiring.
        AtomicReference<TrajectoryFlow> flowRef = new AtomicReference<>(TrajectoryFlow.NONE);
        // Northbound delivery is deferred until the run converges (including any remote
        // legs) so the artifact carries the resume legs through their RUN_END; it must
        // still land before the terminal state while the task can accept artifacts.
        Runnable northboundDelivery = () -> A2aTrajectorySupport.deliverNorthbound(
                flowRef.get(), emitter, trajectoryArtifactId, taskId, appendTrajectory);
        try {
            LOG.info("[A2A] execute start taskId={} sessionId={} agentId={}", taskId, sessionId, agentId);

            // -- (received) -> SUBMITTED -> WORKING --
            emitter.submit();
            LOG.info("[A2A] task state=SUBMITTED taskId={}", taskId);
            emitter.startWork();
            LOG.info("[A2A] task state=WORKING taskId={}", taskId);

            String inputText = extractText(ctx);
            LOG.info("[A2A] input parsed taskId={} textChars={}", taskId, inputText.length());

            if (inputText.isBlank() && !remote.isRemoteContinuation(ctx)) {
                LOG.warn("[A2A] rejecting task with empty query taskId={}", taskId);
                emitter.fail(failureMessage(emitter, "INVALID_INPUT",
                        "message contains no text content", false));
                LOG.info("[A2A] task state=FAILED taskId={}", taskId);
                return;
            }

            if (remote.isRemoteContinuation(ctx)) {
                runRemoteSegment(taskId, cancelled,
                        () -> remote.continueRemote(ctx, emitter, taskId, artifactId, firstArtifact, cancelled,
                                resumeConsumer(ctx, flowRef, execution), northboundDelivery), execution);
                LOG.info("[A2A] execute finish (remote continuation) taskId={} durationMs={}",
                        taskId, (System.nanoTime() - startedNanos) / 1_000_000L);
                return;
            }

            AgentExecutionContext context = toExecutionContext(ctx, inputText);
            flowRef.set(trajectory.open(ctx, context, handler));

            RouteDecision decision = consumeHandler(context, emitter, taskId, artifactId, firstArtifact, true,
                    cancelled, execution);

            if (decision.remoteInvocation() != null) {
                // The orchestrator runs the northbound delivery itself, after the remote leg
                // and the local resume converge — flushing here would cut the artifact short.
                runRemoteSegment(taskId, cancelled,
                        () -> remote.invokeRemote(ctx, decision.remoteInvocation(), emitter, taskId, artifactId,
                                firstArtifact, cancelled, resumeConsumer(ctx, flowRef, execution), northboundDelivery),
                        execution);
            } else {
                // The full trajectory (through RUN_END) is only complete now; deliver it to the caller
                // before the answer's terminal so it lands while the task can still accept artifacts.
                northboundDelivery.run();
                if (decision.terminalAction() != null) {
                    decision.terminalAction().run();
                } else if (!decision.terminalRouted()) {
                    A2aResultRouter.completeDrainedStream(taskId, emitter);
                }
            }

            LOG.info("[A2A] execute finish taskId={} durationMs={}",
                    taskId, (System.nanoTime() - startedNanos) / 1_000_000L);

        } catch (Exception e) {
            if (cancelled.get()) {
                // The cancel path already moved the task to CANCELED and tore the
                // stream down; reporting the teardown as a failure would fight the
                // terminal state the client just observed.
                LOG.info("[A2A] execute torn down by cancel taskId={}", taskId);
                return;
            }
            RuntimeErrorCode code = RuntimeErrorCode.classify(e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            LOG.error("[A2A] execute failed taskId={} code={} errorClass={} message={}",
                    taskId, code, e.getClass().getSimpleName(), A2aLogMasking.mask(e.getMessage()), e);
            northboundDelivery.run();
            try {
                emitter.fail(failureMessage(emitter, code.name(), detail, code.retryable()));
                LOG.info("[A2A] task state=FAILED taskId={}", taskId);
            } catch (RuntimeException ignored) {
                LOG.warn("[A2A] could not emit terminal failure taskId={}", taskId);
            }
        } finally {
            inFlight.remove(taskId, execution);
            A2aTrajectorySupport.closeQuietly(flowRef.get(), taskId);
            MDC.remove(MDC_CONTEXT_ID);
            MDC.remove(MDC_TASK_ID);
            MDC.remove(MDC_TENANT_ID);
            MDC.remove(MDC_AGENT_ID);
        }
    }

    @Override
    public void cancel(RequestContext ctx, AgentEmitter emitter) {
        String taskId = ctx.getTaskId();
        LOG.info("[A2A] cancel requested taskId={}", taskId);
        InFlightExecution execution = inFlight.get(taskId);
        if (execution != null) {
            execution.cancelled().set(true);
        }
        if (handler != null) {
            try {
                handler.cancel(taskId);
            } catch (RuntimeException e) {
                LOG.warn("[A2A] handler cancel failed taskId={} message={}",
                        taskId, A2aLogMasking.mask(e.getMessage()), e);
            }
        }
        try {
            emitter.cancel();
            remote.propagateCancel(ctx, taskId);
            LOG.info("[A2A] task state=CANCELED taskId={}", taskId);
        } catch (Exception e) {
            LOG.error("[A2A] cancel failed taskId={} message={}", taskId, A2aLogMasking.mask(e.getMessage()), e);
        }
        if (execution != null) {
            // Tear the transport down last so the CANCELED state has already
            // landed when the execute thread observes the closed stream. A null
            // slot means the handler is still connecting; the execute thread
            // observes the cancelled flag and closes the stream itself.
            Stream<?> raw = execution.rawStream().get();
            if (raw != null) {
                raw.close();
            }
        }
    }

    /**
     * Local re-entry after a remote leg. The resume context is freshly built, so its
     * trajectory emitter defaults to NOOP — without re-opening, the entire second half
     * of the run (and the handler's rail registration that keys off a non-NOOP emitter)
     * would silently vanish from every sink.
     */
    private A2aRemoteInvocationOrchestrator.LocalResume resumeConsumer(RequestContext ctx,
            AtomicReference<TrajectoryFlow> flowRef, InFlightExecution execution) {
        return (resumeContext, emitter, taskId, artifactId, firstArtifact, cancelled) -> {
            flowRef.set(trajectory.openForResume(ctx, resumeContext, handler, flowRef.get()));
            return consumeHandler(resumeContext, emitter, taskId, artifactId, firstArtifact, false,
                    cancelled, execution);
        };
    }

    /**
     * Keeps the task registered in-flight across a remote A2A leg. The local
     * registration in {@link #consumeHandler} ends when the handler stream drains,
     * but the outbound remote call happens after that — a cancel landing in that
     * window must still find the cancelled flag, or the remote return would re-run
     * the local handler against an already-CANCELED task. The placeholder carries
     * no stream slot; the cancel path tolerates the empty slot.
     */
    private void runRemoteSegment(String taskId, AtomicBoolean cancelled, Runnable remoteSegment,
            InFlightExecution execution) {
        try {
            remoteSegment.run();
        } finally {
            // in-flight registration is owned by execute(); we just run the segment.
        }
    }

    private RouteDecision consumeHandler(AgentExecutionContext context, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, boolean remoteInvocationAllowed,
            AtomicBoolean cancelled, InFlightExecution execution) {
        try (Stream<?> raw = handler.execute(context);
             Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(raw)) {
            execution.rawStream().set(raw);
            Iterator<AgentExecutionResult> iterator = results.iterator();
            while (!cancelled.get() && iterator.hasNext()) {
                AgentExecutionResult result = iterator.next();
                String outputContent = result.outputContent();
                LOG.info("[A2A] result taskId={} type={} outputChars={}",
                        taskId, result.type(),
                        outputContent != null ? outputContent.length() : 0);
                RouteDecision decision = A2aResultRouter.route(result, emitter, taskId, artifactId,
                        firstArtifact, remoteInvocationAllowed);
                if (decision.stop()) {
                    return decision;
                }
            }
            // A cancel observed here already moved the task to CANCELED; emitting
            // a drained-completion would fight the terminal the client just saw.
            if (cancelled.get()) {
                LOG.info("[A2A] handler stream cancelled taskId={}", taskId);
                return RouteDecision.terminal();
            }
            LOG.info("[A2A] handler stream drained without terminal — falling back to complete taskId={}", taskId);
            return RouteDecision.drained();
        } catch (RuntimeException e) {
            if (cancelled.get()) {
                return RouteDecision.terminal();
            }
            throw e;
        } finally {
            // in-flight registration is owned by execute(); cleaned up there.
        }
    }

    /**
     * Builds an agent message carrying the failure both as human-readable text (a {@link TextPart})
     * and as a machine-readable {@link DataPart} ({@code code}, {@code message}, {@code retryable},
     * {@code schema_version}) so an A2A client can render the reason and branch on it
     * programmatically. The same structure is mirrored on the message metadata for clients that read
     * {@code status.message.metadata} rather than the message parts.
     */
    static Message failureMessage(AgentEmitter emitter, String code, String detail, boolean retryable) {
        String message = (detail == null || detail.isBlank()) ? code : detail;
        String text = (detail == null || detail.isBlank()) ? code : code + ": " + detail;
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("kind", "error");
        error.put("code", code);
        error.put("message", message);
        error.put("retryable", retryable);
        error.put("schema_version", ERROR_SCHEMA_VERSION);
        List<Part<?>> parts = List.of(new TextPart(text), new DataPart(error));
        return emitter.newAgentMessage(parts, Map.of("a2a.error", error));
    }

    private AgentExecutionContext toExecutionContext(RequestContext ctx, String text) {
        // The wire Message is reduced to its text right here: the protocol-neutral
        // RuntimeMessage is the only message type that crosses into the SPI.
        List<RuntimeMessage> messages = List.of(RuntimeMessage.user(text));
        String sessionId = ctx.getContextId() != null ? ctx.getContextId() : ctx.getTaskId();
        Map<String, Object> variables = mergeVariables(ctx);
        return new AgentExecutionContext(
                new RuntimeIdentity(
                        asString(variables.get(TENANT_STATE_KEY)),
                        asString(variables.get(USER_ID_METADATA_KEY)),
                        sessionId,
                        ctx.getTaskId(),
                        asString(variables.get(AGENT_ID_METADATA_KEY))),
                "USER_MESSAGE",
                messages,
                variables);
    }

    /**
     * Merge A2A request and message metadata into an immutable variables map.
     * Request-level metadata owns runtime fields. Message-level metadata remains
     * available for business adapter fields, but never overwrites request-level
     * runtime identity or middleware scope.
     */
    private Map<String, Object> mergeVariables(RequestContext ctx) {
        Map<String, Object> merged = normalizedMetadata(ctx, handler.agentId());
        String sessionId = ctx.getContextId() != null ? ctx.getContextId() : ctx.getTaskId();
        LOG.info("[A2A] request received taskId={} sessionId={} textLen={} metadataKeys={}",
                ctx.getTaskId(), sessionId,
                ctx.getMessage() != null ? Messages.text(ctx.getMessage()).length() : 0,
                merged.keySet());
        return merged;
    }

    static Map<String, Object> normalizedMetadata(RequestContext ctx, String handlerAgentId) {
        java.util.LinkedHashMap<String, Object> vars = new java.util.LinkedHashMap<>();
        Map<String, Object> requestMd = ctx.getMetadata();
        if (requestMd != null) {
            vars.putAll(requestMd);
        }
        String sessionId = ctx.getContextId() != null ? ctx.getContextId() : ctx.getTaskId();
        String tenantId = metadata(ctx, TENANT_STATE_KEY, "default");
        String userId = metadata(ctx, USER_ID_METADATA_KEY, "system");
        String agentId = metadata(ctx, AGENT_ID_METADATA_KEY, handlerAgentId);
        putIfBlank(vars, TENANT_STATE_KEY, tenantId);
        putIfBlank(vars, USER_ID_METADATA_KEY, userId);
        putIfBlank(vars, AGENT_ID_METADATA_KEY, agentId);
        String agentStateKey = metadata(ctx, AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, null);
        if (!hasText(agentStateKey)) {
            agentStateKey = defaultAgentStateKey(tenantId, agentId, sessionId);
        }
        vars.put(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, agentStateKey);
        putIfBlank(vars, MEMORY_SCOPE_METADATA_KEY, defaultMemoryScope(tenantId, userId));
        putIfBlank(vars, CORRELATION_ID_METADATA_KEY, firstText(
                metadata(ctx, CORRELATION_ID_METADATA_KEY, null),
                ctx.getMessage() != null ? ctx.getMessage().messageId() : null,
                ctx.getTaskId()));
        putIfBlank(vars, TRACE_ID_METADATA_KEY, firstText(
                metadata(ctx, TRACE_ID_METADATA_KEY, null),
                asString(vars.get(CORRELATION_ID_METADATA_KEY))));
        return Map.copyOf(vars);
    }

    private static String extractText(RequestContext ctx) {
        Message message = ctx.getMessage();
        if (message != null && message.parts() != null) {
            // Only text parts are mapped onto the execution context; dropping the
            // rest silently would let a data-only message degrade into an empty
            // query with nothing in the logs to explain it.
            List<String> dropped = message.parts().stream()
                    .filter(part -> !(part instanceof TextPart))
                    .map(part -> part.getClass().getSimpleName())
                    .toList();
            if (!dropped.isEmpty()) {
                LOG.warn("[A2A] non-text message parts dropped taskId={} kinds={}", ctx.getTaskId(), dropped);
            }
        }
        return Messages.text(message);
    }

    /**
     * Canonical request-context value resolution shared with {@link A2aParentTaskProjector}.
     * Request-level metadata is the only runtime metadata source. Message metadata belongs to the
     * message body and is intentionally ignored by runtime identity, state, memory, and trajectory.
     */
    static String metadata(RequestContext ctx, String key, String fallback) {
        Map<String, Object> md = ctx.getMetadata();
        Object value = md == null ? null : md.get(key);
        if (hasText(value)) {
            return String.valueOf(value);
        }
        return fallback;
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    private static String defaultAgentStateKey(String tenantId, String agentId, String sessionId) {
        return "state:" + tenantId + ":" + agentId + ":" + sessionId;
    }

    private static String defaultMemoryScope(String tenantId, String userId) {
        return "memory:" + tenantId + ":" + userId;
    }

    private static void putIfBlank(Map<String, Object> values, String key, String value) {
        if (!hasText(values.get(key))) {
            values.put(key, value);
        }
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** Whether the task snapshot already carries an artifact with this id (flushed by an earlier leg). */
    static boolean hasArtifact(Task task, String artifactId) {
        if (task == null || task.artifacts() == null) {
            return false;
        }
        for (Artifact artifact : task.artifacts()) {
            if (artifact != null && artifactId.equals(artifact.artifactId())) {
                return true;
            }
        }
        return false;
    }
}
