package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.service.RemoteAgentInvocationService;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler;
import com.huawei.ascend.runtime.engine.spi.CompositeTrajectorySink;
import com.huawei.ascend.runtime.engine.spi.TrajectoryChannel;
import com.huawei.ascend.runtime.engine.spi.TrajectoryLevel;
import com.huawei.ascend.runtime.engine.spi.TrajectorySettings;
import com.huawei.ascend.runtime.engine.spi.TrajectorySink;
import com.huawei.ascend.runtime.engine.spi.TrajectorySinkFactory;
import com.huawei.ascend.runtime.engine.spi.TrajectorySource;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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
    private static final String TRAJECTORY_LEVEL_METADATA = "trajectory.level";
    /** Request opts into northbound trajectory delivery (a second artifact stream) by setting this true. */
    private static final String TRAJECTORY_NORTHBOUND_METADATA = "trajectory.northbound";

    /** Version of the structured-error payload carried on the failure DataPart/metadata. */
    private static final String ERROR_SCHEMA_VERSION = "1";

    private final AgentRuntimeHandler handler;
    private final RemoteSupport remoteSupport;
    private final BooleanSupplier readiness;
    private final A2aParentTaskProjector parentProjector = new A2aParentTaskProjector();
    private final ConcurrentHashMap<String, InFlightExecution> inFlight = new ConcurrentHashMap<>();
    private final Executor trajectoryExecutor;
    private final TrajectorySettings defaultTrajectorySettings;
    private final List<TrajectorySinkFactory> sinkFactories;

    public A2aAgentExecutor(AgentRuntimeHandler handler) {
        this(handler, null, () -> true, null, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteSupport remoteSupport) {
        this(handler, remoteSupport, () -> true, null, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, BooleanSupplier readiness) {
        this(handler, null, readiness, null, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteSupport remoteSupport, BooleanSupplier readiness) {
        this(handler, remoteSupport, readiness, null, TrajectorySettings.off(), List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, Executor trajectoryExecutor,
            TrajectorySettings defaultTrajectorySettings) {
        this(handler, null, () -> true, trajectoryExecutor, defaultTrajectorySettings, List.of());
    }

    public A2aAgentExecutor(AgentRuntimeHandler handler, RemoteSupport remoteSupport, BooleanSupplier readiness,
            Executor trajectoryExecutor, TrajectorySettings defaultTrajectorySettings,
            List<TrajectorySinkFactory> sinkFactories) {
        this.handler = handler;
        this.remoteSupport = remoteSupport;
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.trajectoryExecutor = trajectoryExecutor;
        this.defaultTrajectorySettings =
                defaultTrajectorySettings != null ? defaultTrajectorySettings : TrajectorySettings.off();
        this.sinkFactories = sinkFactories != null ? List.copyOf(sinkFactories) : List.of();
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
        TrajectoryPipeline pipeline = TrajectoryPipeline.NONE;
        TrajectoryChannel channel = TrajectoryChannel.NOOP;
        try {
            LOG.info("[A2A] execute start taskId={} sessionId={} agentId={}", taskId, sessionId, agentId);

            // -- (received) -> SUBMITTED -> WORKING --
            emitter.submit();
            LOG.info("[A2A] task state=SUBMITTED taskId={}", taskId);
            emitter.startWork();
            LOG.info("[A2A] task state=WORKING taskId={}", taskId);

            String inputText = extractText(ctx);
            LOG.info("[A2A] input parsed taskId={} textChars={}", taskId, inputText.length());

            if (parentProjector.isRemoteContinuation(ctx)) {
                handleRemoteContinuation(ctx, emitter, taskId, artifactId, firstArtifact, cancelled);
                LOG.info("[A2A] execute finish taskId={} durationMs={}",
                        taskId, (System.nanoTime() - startedNanos) / 1_000_000L);
                return;
            }

            AgentExecutionContext context = toExecutionContext(ctx, inputText);
            pipeline = openTrajectory(ctx, context);
            channel = pipeline.channel();

            RouteDecision decision = consumeHandler(context, emitter, taskId, artifactId, firstArtifact, true,
                    cancelled);

            // The full trajectory (through RUN_END) is only complete now; deliver it to the caller
            // before the answer's terminal so it lands while the task can still accept artifacts.
            deliverNorthbound(pipeline.northbound(), channel, pipeline.drain(), emitter, trajectoryArtifactId, taskId);

            if (decision.remoteInvocation() != null) {
                handleRemoteInvocation(ctx, decision.remoteInvocation(), emitter, taskId, artifactId,
                        firstArtifact, cancelled);
            } else if (decision.terminalAction() != null) {
                decision.terminalAction().run();
            } else if (!decision.terminalRouted()) {
                completeDrainedStream(taskId, emitter);
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
            deliverNorthbound(pipeline.northbound(), channel, pipeline.drain(), emitter, trajectoryArtifactId, taskId);
            try {
                emitter.fail(failureMessage(emitter, code.name(), detail, code.retryable()));
                LOG.info("[A2A] task state=FAILED taskId={}", taskId);
            } catch (RuntimeException ignored) {
                LOG.warn("[A2A] could not emit terminal failure taskId={}", taskId);
            }
        } finally {
            channel.close();
            MDC.remove(MDC_CONTEXT_ID);
            MDC.remove(MDC_TASK_ID);
        }
    }

    /**
     * Opens a per-invocation trajectory channel for a {@link TrajectorySource} handler and starts
     * a drain that fans events to the JSONL track. Returns a no-op channel when trajectory is
     * disabled, the handler is not a source, or no drain executor is wired.
     */
    private TrajectoryPipeline openTrajectory(RequestContext ctx, AgentExecutionContext context) {
        if (trajectoryExecutor == null || !(handler instanceof TrajectorySource source)) {
            return TrajectoryPipeline.NONE;
        }
        TrajectorySettings settings = resolveSettings(metadata(ctx, TRAJECTORY_LEVEL_METADATA, null));
        if (settings.level() == TrajectoryLevel.OFF) {
            return TrajectoryPipeline.NONE;
        }
        TrajectoryChannel channel = source.openTrajectory(context, settings);
        List<TrajectorySink> sinks = new ArrayList<>();
        sinks.add(new JsonlLogSink());
        for (TrajectorySinkFactory factory : sinkFactories) {
            sinks.add(factory.create());
        }
        A2aNorthboundSink northbound = null;
        if ("true".equalsIgnoreCase(metadata(ctx, TRAJECTORY_NORTHBOUND_METADATA, "false"))) {
            northbound = new A2aNorthboundSink();
            sinks.add(northbound);
        }
        TrajectorySink sink = new CompositeTrajectorySink(sinks);
        try {
            Map<String, String> mdc = MDC.getCopyOfContextMap();
            TrajectoryChannel draining = channel;
            String contextId = ctx.getContextId();
            String taskId = ctx.getTaskId();
            CompletableFuture<Void> drain = CompletableFuture.runAsync(
                    () -> drainTrajectory(draining, mdc, sink, contextId, taskId), trajectoryExecutor);
            return new TrajectoryPipeline(channel, northbound, drain);
        } catch (RuntimeException e) {
            // Trajectory must never break the run: if the drain can't be scheduled (e.g. the pool is
            // shutting down), close the channel so the adapter's publishes are dropped, and degrade to
            // no trajectory rather than letting the failure escape execute().
            LOG.warn("[A2A] trajectory drain not scheduled taskId={} message={}", ctx.getTaskId(), e.getMessage());
            channel.close();
            return TrajectoryPipeline.NONE;
        }
    }

    /** The per-invocation trajectory wiring: the channel, the optional northbound sink, and the joinable drain. */
    private record TrajectoryPipeline(TrajectoryChannel channel, A2aNorthboundSink northbound,
            CompletableFuture<Void> drain) {
        static final TrajectoryPipeline NONE = new TrajectoryPipeline(TrajectoryChannel.NOOP, null, null);
    }

    /**
     * When the caller opted into northbound trajectory, close the channel, wait (bounded) for the
     * drain to buffer every event, and flush them as the {@code -trajectory} artifact - all on the
     * execute thread, the only thread allowed to touch the single-writer emitter. A failure here
     * must never break the answer: it degrades to no northbound trajectory.
     */
    private static void deliverNorthbound(A2aNorthboundSink northbound, TrajectoryChannel channel,
            CompletableFuture<Void> drain, AgentEmitter emitter, String artifactId, String taskId) {
        if (northbound == null) {
            return;
        }
        try {
            channel.close();
            if (drain != null) {
                drain.get(5, TimeUnit.SECONDS);
            }
            northbound.flush(emitter, artifactId);
        } catch (Exception e) {
            LOG.warn("[A2A] northbound trajectory delivery failed taskId={} message={}", taskId, e.getMessage());
        }
    }

    private TrajectorySettings resolveSettings(String requestOverride) {
        if (defaultTrajectorySettings.level() == TrajectoryLevel.OFF) {
            return TrajectorySettings.off();
        }
        TrajectoryLevel level = TrajectoryLevel.from(requestOverride, defaultTrajectorySettings.level());
        if (level == TrajectoryLevel.OFF) {
            return TrajectorySettings.off();
        }
        return new TrajectorySettings(level, defaultTrajectorySettings.maskKeyPattern(),
                defaultTrajectorySettings.truncateChars());
    }

    private static void drainTrajectory(TrajectoryChannel channel, Map<String, String> mdc, TrajectorySink sink,
            String contextId, String taskId) {
        // Restore the worker thread's prior MDC on exit rather than clearing it: this drain runs on a
        // shared pool, so wiping the whole map could clobber MDC a reused thread legitimately holds.
        Map<String, String> prior = MDC.getCopyOfContextMap();
        if (mdc != null) {
            MDC.setContextMap(mdc);
        }
        try {
            sink.onOpen(contextId, taskId);
            channel.drain().forEach(sink::accept);
        } catch (RuntimeException e) {
            LOG.warn("[A2A] trajectory drain failed message={}", e.getMessage());
        } finally {
            sink.onClose();
            if (prior != null) {
                MDC.setContextMap(prior);
            } else {
                MDC.clear();
            }
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
            if (remoteSupport != null && parentProjector.isRemoteContinuation(ctx)) {
                try {
                    RemoteAgentInvocationService.RemoteRoute route = parentProjector.remoteRoute(ctx.getTask());
                    remoteSupport.invocationService.cancel(new RemoteAgentInvocationService.RemoteTaskReference(
                            route.remoteAgentId(), route.remoteTaskId(), route.remoteContextId()));
                } catch (RuntimeException error) {
                    LOG.warn("[A2A] remote cancel propagation failed taskId={} errorClass={} message={}",
                            taskId, error.getClass().getSimpleName(), error.getMessage());
                }
            }
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

    private RouteDecision consumeHandler(AgentExecutionContext context, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, boolean remoteInvocationAllowed,
            AtomicBoolean cancelled) {
        InFlightExecution execution = null;
        try (Stream<?> raw = executeAgent(context);
             Stream<AgentExecutionResult> results = handler.resultAdapter().adapt(raw)) {
            execution = new InFlightExecution(raw, cancelled);
            inFlight.put(taskId, execution);
            Iterator<AgentExecutionResult> iterator = results.iterator();
            while (iterator.hasNext()) {
                AgentExecutionResult result = iterator.next();
                LOG.info("[A2A] result taskId={} type={} outputChars={}",
                        taskId, result.type(),
                        result.outputContent() != null ? result.outputContent().length() : 0);
                RouteDecision decision = route(result, emitter, taskId, artifactId, firstArtifact,
                        remoteInvocationAllowed);
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

    private Stream<?> executeAgent(AgentExecutionContext context) {
        return handler.execute(context);
    }

    /**
     * Streams an OUTPUT chunk immediately; for the terminal kinds the decision carries the terminal
     * emission as a deferred action the caller runs after any northbound trajectory has been
     * flushed, so the trajectory artifact lands before the task reaches its terminal state.
     */
    private RouteDecision route(AgentExecutionResult result, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, boolean remoteInvocationAllowed) {
        switch (result.type()) {
            case OUTPUT -> {
                String text = outputText(result);
                LOG.info("[A2A] output stream taskId={} textChars={}", taskId, text.length());
                // First chunk opens the artifact (append=false); later chunks append to the same
                // artifactId so the stream forms one growing artifact rather than many fragments.
                boolean append = !firstArtifact.getAndSet(false);
                emitter.addArtifact(List.<Part<?>>of(new TextPart(text)),
                        artifactId, "agent-response", null, append, false);
                // state stays WORKING - more output may follow; the terminal status closes the stream
                return RouteDecision.continueRoute();
            }
            case COMPLETED -> {
                String text = outputText(result);
                return RouteDecision.terminal(() -> {
                    if (!text.isBlank()) {
                        LOG.info("[A2A] complete with final output taskId={} textChars={}", taskId, text.length());
                        emitter.complete(emitter.newAgentMessage(List.<Part<?>>of(new TextPart(text)), null));
                    } else {
                        emitter.complete();
                    }
                    LOG.info("[A2A] task state=COMPLETED taskId={}", taskId);
                });
            }
            case FAILED -> {
                String code = result.errorCode() == null ? "RUNTIME_ERROR" : result.errorCode();
                String msg = result.errorMessage() == null ? code : result.errorMessage();
                return RouteDecision.terminal(() -> {
                    LOG.warn("[A2A] task state=FAILED taskId={} code={} message={}", taskId, code, msg);
                    // Adapter-supplied codes pass through unchanged; retryability is unknown -> conservative false.
                    emitter.fail(failureMessage(emitter, code, result.errorMessage(), false));
                });
            }
            case INTERRUPTED -> {
                String prompt = result.prompt() == null ? "" : result.prompt();
                return RouteDecision.terminal(() -> {
                    LOG.info("[A2A] task state=INPUT_REQUIRED taskId={} prompt={}", taskId, prompt);
                    Message message = prompt.isBlank()
                            ? null
                            : emitter.newAgentMessage(List.<Part<?>>of(new TextPart(prompt)), null);
                    emitter.requiresInput(message, false);
                });
            }
            case REMOTE_INVOCATION -> {
                if (!remoteInvocationAllowed) {
                    return RouteDecision.terminal(() -> emitter.fail(failureMessage(
                            emitter,
                            "NESTED_REMOTE_INVOCATION_UNSUPPORTED",
                            "remote A2A invocation after REMOTE_RESUME is not supported",
                            false)));
                }
                return RouteDecision.remote(result.remoteInvocation());
            }
        }
        throw new IllegalStateException("Unsupported result type: " + result.type());
    }

    private void handleRemoteInvocation(RequestContext requestContext,
            AgentExecutionResult.RemoteInvocation invocation, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, AtomicBoolean cancelled) {
        if (remoteSupport == null) {
            emitter.fail(failureMessage(emitter, "REMOTE_NOT_CONFIGURED",
                    "remote A2A invocation is not configured", false));
            return;
        }
        List<RemoteAgentInvocationService.RemoteAgentResult> results =
                remoteSupport.invocationService.invoke(invocation,
                        result -> parentProjector.projectRemoteProgress(result, emitter));
        handleRemoteResults(requestContext, invocation, results, emitter, taskId, artifactId, firstArtifact,
                cancelled);
    }

    private void handleRemoteContinuation(RequestContext ctx, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, AtomicBoolean cancelled) {
        if (remoteSupport == null) {
            emitter.fail(failureMessage(emitter, "REMOTE_NOT_CONFIGURED",
                    "remote A2A invocation is not configured", false));
            return;
        }
        RemoteAgentInvocationService.RemoteRoute route;
        try {
            route = parentProjector.remoteRoute(ctx.getTask());
        } catch (IllegalArgumentException error) {
            emitter.fail(failureMessage(emitter, "REMOTE_ROUTE_METADATA_MISSING", error.getMessage(), false));
            return;
        }
        List<RemoteAgentInvocationService.RemoteAgentResult> results =
                remoteSupport.invocationService.resumeRemoteInput(route, extractText(ctx),
                        result -> parentProjector.projectRemoteProgress(result, emitter));
        AgentExecutionResult.RemoteInvocation invocation = parentProjector.remoteInvocation(route);
        handleRemoteResults(ctx, invocation, results, emitter, taskId, artifactId, firstArtifact, cancelled);
    }

    private void handleRemoteResults(RequestContext requestContext, AgentExecutionResult.RemoteInvocation invocation,
            List<RemoteAgentInvocationService.RemoteAgentResult> results, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, AtomicBoolean cancelled) {
        A2aParentTaskProjector.RemoteOutcome outcome =
                parentProjector.projectRemoteOutcome(invocation, results, emitter);
        if (outcome.waitingForRemoteInput()) {
            return;
        }
        AgentExecutionContext resumeContext =
                parentProjector.remoteResumeContext(requestContext, handler.agentId(), invocation, outcome.toolResult());
        RouteDecision decision = consumeHandler(resumeContext, emitter, taskId, artifactId, firstArtifact, false,
                cancelled);
        if (decision.terminalAction() != null) {
            decision.terminalAction().run();
        } else if (!decision.terminalRouted()) {
            completeDrainedStream(taskId, emitter);
        }
    }

    private static void completeDrainedStream(String taskId, AgentEmitter emitter) {
        // The handler stream drained without a terminal result (e.g. the upstream runtime
        // replied with no events). DefaultRequestHandler never forces a terminal state, so
        // finalize here or the task stays WORKING forever and polling clients hang.
        LOG.warn("[A2A] result stream ended without terminal result taskId={} - completing", taskId);
        emitter.complete();
    }

    private static String outputText(AgentExecutionResult result) {
        return result.outputContent() != null ? result.outputContent() : "";
    }

    /**
     * Builds an agent message carrying the failure both as human-readable text (a {@link TextPart})
     * and as a machine-readable {@link DataPart} ({@code code}, {@code message}, {@code retryable},
     * {@code schema_version}) so an A2A client can render the reason and branch on it
     * programmatically. The same structure is mirrored on the message metadata for clients that read
     * {@code status.message.metadata} rather than the message parts.
     */
    private static Message failureMessage(AgentEmitter emitter, String code, String detail, boolean retryable) {
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
    }

    /**
     * Outcome of routing one adapted result. {@code terminalAction} carries the deferred terminal
     * emission (run by the caller after trajectory delivery); {@code terminalRouted} is also true
     * for terminals already emitted elsewhere (the cancel teardown path).
     */
    private record RouteDecision(boolean stop, AgentExecutionResult.RemoteInvocation remoteInvocation,
            boolean terminalRouted, Runnable terminalAction) {
        static RouteDecision continueRoute() {
            return new RouteDecision(false, null, false, null);
        }

        static RouteDecision drained() {
            return new RouteDecision(true, null, false, null);
        }

        static RouteDecision terminal() {
            return new RouteDecision(true, null, true, null);
        }

        static RouteDecision terminal(Runnable terminalAction) {
            return new RouteDecision(true, null, true, terminalAction);
        }

        static RouteDecision remote(AgentExecutionResult.RemoteInvocation invocation) {
            return new RouteDecision(true, invocation, false, null);
        }
    }
}
