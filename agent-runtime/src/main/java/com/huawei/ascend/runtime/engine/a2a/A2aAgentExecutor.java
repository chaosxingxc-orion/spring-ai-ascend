package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.A2aResultRouter.RouteDecision;
import com.huawei.ascend.runtime.engine.a2a.A2aTrajectorySupport.TrajectoryFlow;
import com.huawei.ascend.runtime.engine.service.RemoteAgentInvocationService;
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
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
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

    /**
     * Call-context state key under which the access layer publishes the
     * transport-authenticated tenant. It outranks the client-self-declared
     * params.tenant - a wire client must not be able to choose its tenant.
     */
    public static final String TENANT_STATE_KEY = "tenantId";

    private static final Logger LOG = LoggerFactory.getLogger(A2aAgentExecutor.class);
    private static final String MDC_CONTEXT_ID = "contextId";
    private static final String MDC_TASK_ID = "taskId";

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

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteSupport remoteSupport) {
        this(handler, remoteSupport, () -> true, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, BooleanSupplier readiness) {
        this(handler, null, readiness, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteSupport remoteSupport, BooleanSupplier readiness) {
        this(handler, remoteSupport, readiness, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, TrajectorySettings defaultTrajectorySettings,
            List<TrajectorySinkFactory> sinkFactories) {
        this(handler, null, () -> true, defaultTrajectorySettings, sinkFactories);
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteSupport remoteSupport, BooleanSupplier readiness,
            TrajectorySettings defaultTrajectorySettings, List<TrajectorySinkFactory> sinkFactories) {
        this.handler = handler;
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.trajectory = new A2aTrajectorySupport(defaultTrajectorySettings, sinkFactories);
        this.remote = new A2aRemoteInvocationOrchestrator(
                remoteSupport != null ? remoteSupport.invocationService() : null,
                new A2aParentTaskProjector(),
                handler != null ? handler.agentId() : null);
    }

    /** Cancel state for one in-flight execution: the handler's raw stream plus a torn-down marker. */
    private record InFlightExecution(Stream<?> rawStream, AtomicBoolean cancelled) {
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
        // Per-task local state (this bean is a shared singleton - never hoist to a field).
        AtomicBoolean firstArtifact = new AtomicBoolean(true);
        String artifactId = taskId + "-response";
        String trajectoryArtifactId = taskId + "-trajectory";
        AtomicBoolean cancelled = new AtomicBoolean(false);
        TrajectoryFlow flow = TrajectoryFlow.NONE;
        try {
            LOG.info("[A2A] execute start taskId={} sessionId={} agentId={}", taskId, sessionId, agentId);

            // -- (received) -> SUBMITTED -> WORKING --
            emitter.submit();
            LOG.info("[A2A] task state=SUBMITTED taskId={}", taskId);
            emitter.startWork();
            LOG.info("[A2A] task state=WORKING taskId={}", taskId);

            String inputText = extractText(ctx);
            LOG.info("[A2A] input parsed taskId={} textChars={}", taskId, inputText.length());

            if (remote.isRemoteContinuation(ctx)) {
                remote.continueRemote(ctx, emitter, taskId, artifactId, firstArtifact, cancelled,
                        this::consumeForResume);
                LOG.info("[A2A] execute finish taskId={} durationMs={}",
                        taskId, (System.nanoTime() - startedNanos) / 1_000_000L);
                return;
            }

            AgentExecutionContext context = toExecutionContext(ctx, inputText);
            flow = trajectory.open(ctx, context, handler);

            RouteDecision decision = consumeHandler(context, emitter, taskId, artifactId, firstArtifact, true,
                    cancelled);

            // The full trajectory (through RUN_END) is only complete now; deliver it to the caller
            // before the answer's terminal so it lands while the task can still accept artifacts.
            A2aTrajectorySupport.deliverNorthbound(flow, emitter, trajectoryArtifactId, taskId);

            if (decision.remoteInvocation() != null) {
                remote.invokeRemote(ctx, decision.remoteInvocation(), emitter, taskId, artifactId,
                        firstArtifact, cancelled, this::consumeForResume);
            } else if (decision.terminalAction() != null) {
                decision.terminalAction().run();
            } else if (!decision.terminalRouted()) {
                A2aResultRouter.completeDrainedStream(taskId, emitter);
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
                    taskId, code, e.getClass().getSimpleName(), e.getMessage(), e);
            A2aTrajectorySupport.deliverNorthbound(flow, emitter, trajectoryArtifactId, taskId);
            try {
                emitter.fail(failureMessage(emitter, code.name(), detail, code.retryable()));
                LOG.info("[A2A] task state=FAILED taskId={}", taskId);
            } catch (RuntimeException ignored) {
                LOG.warn("[A2A] could not emit terminal failure taskId={}", taskId);
            }
        } finally {
            A2aTrajectorySupport.closeQuietly(flow, taskId);
            MDC.remove(MDC_CONTEXT_ID);
            MDC.remove(MDC_TASK_ID);
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
                LOG.warn("[A2A] handler cancel failed taskId={} message={}", taskId, e.getMessage(), e);
            }
        }
        try {
            emitter.cancel();
            remote.propagateCancel(ctx, taskId);
            LOG.info("[A2A] task state=CANCELED taskId={}", taskId);
        } catch (Exception e) {
            LOG.error("[A2A] cancel failed taskId={} message={}", taskId, e.getMessage(), e);
        }
        if (execution != null) {
            // Tear the transport down last so the CANCELED state has already
            // landed when the execute thread observes the closed stream.
            execution.rawStream().close();
        }
    }

    private RouteDecision consumeForResume(AgentExecutionContext resumeContext, AgentEmitter emitter,
            String taskId, String artifactId, AtomicBoolean firstArtifact, AtomicBoolean cancelled) {
        return consumeHandler(resumeContext, emitter, taskId, artifactId, firstArtifact, false, cancelled);
    }

    private RouteDecision consumeHandler(AgentExecutionContext context, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, boolean remoteInvocationAllowed,
            AtomicBoolean cancelled) {
        InFlightExecution execution = null;
        try (Stream<?> raw = handler.execute(context);
             Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(raw)) {
            execution = new InFlightExecution(raw, cancelled);
            inFlight.put(taskId, execution);
            Iterator<AgentExecutionResult> iterator = results.iterator();
            while (iterator.hasNext()) {
                AgentExecutionResult result = iterator.next();
                LOG.info("[A2A] result taskId={} type={} outputChars={}",
                        taskId, result.type(),
                        result.outputContent() != null ? result.outputContent().length() : 0);
                RouteDecision decision = A2aResultRouter.route(result, emitter, taskId, artifactId,
                        firstArtifact, remoteInvocationAllowed);
                if (decision.stop()) {
                    return decision;
                }
            }
            return RouteDecision.drained();
        } catch (RuntimeException e) {
            if (cancelled.get()) {
                return RouteDecision.terminal();
            }
            throw e;
        } finally {
            if (execution != null) {
                inFlight.remove(taskId, execution);
            }
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
        List<Message> messages = List.of(Message.builder()
                .role(Message.Role.ROLE_USER)
                .parts(List.<Part<?>>of(new TextPart(text)))
                .build());
        String sessionId = ctx.getContextId() != null ? ctx.getContextId() : ctx.getTaskId();
        return new AgentExecutionContext(
                new RuntimeIdentity(
                        metadata(ctx, "tenantId", "default"),
                        metadata(ctx, "userId", "system"),
                        sessionId,
                        ctx.getTaskId(),
                        metadata(ctx, "agentId", handler.agentId())),
                "USER_MESSAGE",
                messages,
                // In A2A every message/send of a conversation opens a NEW task within
                // the same contextId, so the framework conversation key must follow the
                // session - keying it by taskId would start a fresh framework
                // conversation each turn and checkpointer restore would never fire.
                Map.of(AgentExecutionContext.AGENT_STATE_KEY_VARIABLE, sessionId));
    }

    private static String extractText(RequestContext ctx) {
        return Messages.text(ctx.getMessage());
    }

    /**
     * Canonical request-context value resolution shared with {@link A2aParentTaskProjector}
     * so the remote-resume re-entry resolves the same tenant as the first local segment.
     * For the tenant key the transport-authenticated tenant outranks client-declared metadata.
     */
    static String metadata(RequestContext ctx, String key, String fallback) {
        if (TENANT_STATE_KEY.equals(key)) {
            Object transportTenant = ctx.getCallContext() == null
                    ? null : ctx.getCallContext().getState().get(TENANT_STATE_KEY);
            if (hasText(transportTenant)) {
                return String.valueOf(transportTenant);
            }
            if (hasText(ctx.getTenant())) {
                return ctx.getTenant();
            }
        }
        Map<String, Object> md = ctx.getMetadata();
        Object value = md == null ? null : md.get(key);
        return hasText(value) ? String.valueOf(value) : fallback;
    }

    private static boolean hasText(Object value) {
        return value != null && !String.valueOf(value).isBlank();
    }

    public static final class RemoteSupport {
        private final RemoteAgentInvocationService invocationService;

        public RemoteSupport(RemoteAgentInvocationService invocationService) {
            this.invocationService = Objects.requireNonNull(invocationService, "invocationService");
        }

        public static RemoteSupport forOutbound(RemoteAgentInvocationService.OutboundPort outboundPort) {
            return new RemoteSupport(new RemoteAgentInvocationService(outboundPort));
        }

        RemoteAgentInvocationService invocationService() {
            return invocationService;
        }
    }
}
