package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.A2aResultRouter.RouteDecision;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the remote-A2A-tool legs of one task: the outbound invocation a local run
 * requested, the inbound continuation of a task parked on remote INPUT_REQUIRED, and the
 * re-entry into the local handler with the remote tool result. Invoked synchronously on the
 * execute thread only; never holds the single-writer {@link AgentEmitter} as a field. The
 * re-entry goes through {@link LocalResume} so in-flight registration and cancel-through stay
 * owned by {@link A2aAgentExecutor}.
 */
final class A2aRemoteInvocationOrchestrator {

    private static final Logger LOG = LoggerFactory.getLogger(A2aRemoteInvocationOrchestrator.class);

    /** Re-enters the local handler with a resume context; remote invocations are disallowed inside. */
    @FunctionalInterface
    interface LocalResume {
        RouteDecision consume(AgentExecutionContext resumeContext, AgentEmitter emitter, String taskId,
                String artifactId, AtomicBoolean firstArtifact, AtomicBoolean cancelled);
    }

    private final RemoteAgentInvocationService invocationService;
    private final A2aParentTaskProjector parentProjector;
    private final String agentId;
    private final int maxRemoteLegs;
    private final ConcurrentHashMap<String, ActiveRemoteTaskHandle> activeRemoteTasks = new ConcurrentHashMap<>();

    A2aRemoteInvocationOrchestrator(RemoteAgentInvocationService invocationService,
            A2aParentTaskProjector parentProjector, String agentId, int maxRemoteLegs) {
        this.invocationService = invocationService;
        this.parentProjector = parentProjector;
        this.agentId = agentId;
        this.maxRemoteLegs =
                com.huawei.ascend.runtime.boot.AgentRuntimeProperties.clampRemoteInvocationMaxLegs(maxRemoteLegs);
    }

    void invokeRemote(RequestContext requestContext, AgentExecutionResult.RemoteInvocation invocation,
            AgentEmitter emitter, String taskId, String artifactId, AtomicBoolean firstArtifact,
            AtomicBoolean cancelled, LocalResume resume, Runnable beforeTerminal) {
        if (invocationService == null) {
            beforeTerminal.run();
            emitter.fail(A2aAgentExecutor.failureMessage(emitter, "REMOTE_NOT_CONFIGURED",
                    "remote A2A invocation is not configured", false));
            return;
        }
        LOG.info("[A2A] remote tool invocation start taskId={} toolName={} remoteAgentId={} toolCallId={}",
                taskId, invocation.toolName(), invocation.remoteAgentId(), invocation.toolCallId());
        Map<String, Object> requestMetadata = outboundMetadata(requestContext);
        runRemoteLegsUntilLocalTerminal(requestContext, invocation, requestMetadata, emitter, taskId, artifactId,
                firstArtifact, cancelled, resume, beforeTerminal);
    }

    void continueRemote(RequestContext ctx, AgentEmitter emitter, String taskId, String artifactId,
            AtomicBoolean firstArtifact, AtomicBoolean cancelled, LocalResume resume, Runnable beforeTerminal) {
        if (invocationService == null) {
            beforeTerminal.run();
            emitter.fail(A2aAgentExecutor.failureMessage(emitter, "REMOTE_NOT_CONFIGURED",
                    "remote A2A invocation is not configured", false));
            return;
        }
        RemoteAgentInvocationService.RemoteRoute route;
        try {
            route = parentProjector.remoteRoute(ctx.getTask());
        } catch (IllegalArgumentException error) {
            beforeTerminal.run();
            emitter.fail(A2aAgentExecutor.failureMessage(
                    emitter, "REMOTE_ROUTE_METADATA_MISSING", error.getMessage(), false));
            return;
        }
        LOG.info("[A2A] remote tool invocation resume taskId={} remoteAgentId={} remoteTaskId={} toolCallId={}",
                taskId, route.remoteAgentId(), route.remoteTaskId(), route.toolCallId());
        Map<String, Object> requestMetadata = outboundMetadata(ctx);
        AgentExecutionResult.RemoteInvocation invocation = parentProjector.remoteInvocation(route);
        runRemoteLegsUntilLocalTerminal(ctx, invocation,
                activeHandle -> invocationService.resumeRemoteInput(route, Messages.text(ctx.getMessage()),
                        requestMetadata, result -> observeAndProject(result, activeHandle, emitter, cancelled)),
                requestMetadata, emitter, taskId, artifactId, firstArtifact, cancelled, resume, beforeTerminal);
    }

    /** Propagates a local cancel to the remote task a continuation is parked on. Best-effort. */
    void propagateCancel(RequestContext ctx, String taskId) {
        if (invocationService == null) {
            return;
        }
        ActiveRemoteTaskHandle activeHandle = activeRemoteTasks.get(taskId);
        if (activeHandle != null && cancelActiveRemoteTask(taskId, activeHandle, null, null)) {
            return;
        }
        if (!parentProjector.isRemoteContinuation(ctx)) {
            return;
        }
        try {
            RemoteAgentInvocationService.RemoteRoute route = parentProjector.remoteRoute(ctx.getTask());
            invocationService.cancel(new RemoteAgentInvocationService.RemoteTaskReference(
                    route.remoteAgentId(), route.remoteTaskId(), route.remoteContextId()));
        } catch (RuntimeException error) {
            LOG.warn("[A2A] remote cancel propagation failed taskId={} errorClass={} message={}",
                    taskId, error.getClass().getSimpleName(), A2aLogMasking.mask(error.getMessage()));
        }
    }

    boolean isRemoteContinuation(RequestContext ctx) {
        return parentProjector.isRemoteContinuation(ctx);
    }

    private void runRemoteLegsUntilLocalTerminal(RequestContext requestContext,
            AgentExecutionResult.RemoteInvocation firstInvocation, Map<String, Object> requestMetadata,
            AgentEmitter emitter, String taskId, String artifactId, AtomicBoolean firstArtifact,
            AtomicBoolean cancelled, LocalResume resume, Runnable beforeTerminal) {
        runRemoteLegsUntilLocalTerminal(requestContext, firstInvocation,
                activeHandle -> invocationService.invoke(firstInvocation, requestMetadata,
                        result -> observeAndProject(result, activeHandle, emitter, cancelled)),
                requestMetadata, emitter, taskId, artifactId, firstArtifact, cancelled, resume, beforeTerminal);
    }

    private void runRemoteLegsUntilLocalTerminal(RequestContext requestContext,
            AgentExecutionResult.RemoteInvocation firstInvocation, RemoteLegInvoker firstLegInvoker,
            Map<String, Object> requestMetadata, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, AtomicBoolean cancelled, LocalResume resume,
            Runnable beforeTerminal) {
        AgentExecutionResult.RemoteInvocation invocation = firstInvocation;
        RemoteLegInvoker legInvoker = firstLegInvoker;
        int legs = 0;
        while (invocation != null) {
            if (++legs > maxRemoteLegs) {
                beforeTerminal.run();
                emitter.fail(A2aAgentExecutor.failureMessage(emitter,
                        "REMOTE_INVOCATION_LIMIT_EXCEEDED",
                        "remote A2A invocation exceeded max legs",
                        false));
                return;
            }
            ActiveRemoteTaskHandle activeHandle = new ActiveRemoteTaskHandle(invocation);
            activeRemoteTasks.put(taskId, activeHandle);
            List<RemoteAgentInvocationService.RemoteAgentResult> results;
            try {
                results = legInvoker.invoke(activeHandle);
            } finally {
                activeRemoteTasks.remove(taskId, activeHandle);
            }
            if (cancelled.get()) {
                cancelActiveRemoteTask(taskId, activeHandle, invocation, results);
                return;
            }
            A2aParentTaskProjector.RemoteOutcome outcome =
                    parentProjector.projectRemoteOutcome(invocation, results, emitter);
            if (outcome.waitingForRemoteInput()) {
                // The task parks on remote INPUT_REQUIRED. Deliver the
                // trajectory BEFORE the terminal requiresInput closes the
                // emitter so the artifact lands while the task can still
                // accept artifacts.
                beforeTerminal.run();
                LOG.info("[A2A] remote tool invocation waiting-for-input taskId={} remoteAgentId={} toolCallId={}",
                        taskId, invocation.remoteAgentId(), invocation.toolCallId());
                return;
            }
            LOG.info("[A2A] remote tool invocation complete taskId={} remoteAgentId={} toolCallId={} resultLen={}",
                    taskId, invocation.remoteAgentId(), invocation.toolCallId(),
                    outcome.toolResult() != null ? outcome.toolResult().length() : 0);
            AgentExecutionContext resumeContext =
                    parentProjector.remoteResumeContext(requestContext, agentId, invocation, outcome.toolResult());
            RouteDecision decision = resume.consume(resumeContext, emitter, taskId, artifactId, firstArtifact,
                    cancelled);
            if (decision.remoteInvocation() == null) {
                // The trajectory now spans every leg through the resume's RUN_END; deliver it
                // northbound while the task can still accept artifacts, before the terminal lands.
                beforeTerminal.run();
                if (decision.terminalAction() != null) {
                    decision.terminalAction().run();
                } else if (!decision.terminalRouted()) {
                    A2aResultRouter.completeDrainedStream(taskId, emitter);
                }
                return;
            }
            invocation = decision.remoteInvocation();
            AgentExecutionResult.RemoteInvocation nextInvocation = invocation;
            legInvoker = nextActiveHandle -> invocationService.invoke(nextInvocation, requestMetadata,
                    result -> observeAndProject(result, nextActiveHandle, emitter, cancelled));
        }
        beforeTerminal.run();
    }

    private void observeAndProject(RemoteAgentInvocationService.RemoteAgentResult result,
            ActiveRemoteTaskHandle activeHandle, AgentEmitter emitter, AtomicBoolean cancelled) {
        activeHandle.observe(result);
        // A cancel may land while the remote stream is still open; the
        // task is then CANCELED and must not receive progress artifacts.
        if (!cancelled.get()) {
            parentProjector.projectRemoteProgress(result, emitter);
        }
    }

    /** Best-effort remote cancel keyed off the active handle, falling back to received results. */
    private boolean cancelActiveRemoteTask(String taskId, ActiveRemoteTaskHandle activeHandle,
            AgentExecutionResult.RemoteInvocation invocation,
            List<RemoteAgentInvocationService.RemoteAgentResult> results) {
        String remoteAgentId = activeHandle != null ? activeHandle.remoteAgentId() : invocation.remoteAgentId();
        String remoteTaskId = activeHandle != null ? activeHandle.remoteTaskId() : null;
        String remoteContextId = activeHandle != null ? activeHandle.remoteContextId() : null;
        if (remoteTaskId == null && results != null) {
            remoteTaskId = lastNonNull(results, RemoteAgentInvocationService.RemoteAgentResult::remoteTaskId);
        }
        if (remoteContextId == null && results != null) {
            remoteContextId = lastNonNull(results, RemoteAgentInvocationService.RemoteAgentResult::remoteContextId);
        }
        if (remoteTaskId == null) {
            return false;
        }
        if (activeHandle != null && !activeHandle.markCancelIssued()) {
            return true;
        }
        try {
            invocationService.cancel(new RemoteAgentInvocationService.RemoteTaskReference(
                    remoteAgentId, remoteTaskId, remoteContextId));
            return true;
        } catch (RuntimeException error) {
            LOG.warn("[A2A] remote cancel after local cancel failed taskId={} errorClass={} message={}",
                    taskId, error.getClass().getSimpleName(), A2aLogMasking.mask(error.getMessage()));
            return false;
        }
    }

    private static String lastNonNull(List<RemoteAgentInvocationService.RemoteAgentResult> results,
            java.util.function.Function<RemoteAgentInvocationService.RemoteAgentResult, String> extractor) {
        for (int i = results.size() - 1; i >= 0; i--) {
            String value = extractor.apply(results.get(i));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * Extracts the caller's A2A request metadata so it can be forwarded to the
     * remote agent. Message metadata is retained as a legacy/business fallback,
     * but it must not overwrite request-level runtime metadata.
     */
    private Map<String, Object> outboundMetadata(RequestContext ctx) {
        return A2aAgentExecutor.normalizedMetadata(ctx, agentId);
    }

    @FunctionalInterface
    private interface RemoteLegInvoker {
        List<RemoteAgentInvocationService.RemoteAgentResult> invoke(ActiveRemoteTaskHandle activeHandle);
    }

    private static final class ActiveRemoteTaskHandle {
        private final String remoteAgentId;
        private final AtomicReference<String> remoteTaskId = new AtomicReference<>();
        private final AtomicReference<String> remoteContextId = new AtomicReference<>();
        private final AtomicBoolean cancelIssued = new AtomicBoolean();

        private ActiveRemoteTaskHandle(AgentExecutionResult.RemoteInvocation invocation) {
            this.remoteAgentId = invocation.remoteAgentId();
        }

        private void observe(RemoteAgentInvocationService.RemoteAgentResult result) {
            if (result == null) {
                return;
            }
            if (result.remoteTaskId() != null) {
                remoteTaskId.set(result.remoteTaskId());
            }
            if (result.remoteContextId() != null) {
                remoteContextId.set(result.remoteContextId());
            }
        }

        private String remoteAgentId() {
            return remoteAgentId;
        }

        private String remoteTaskId() {
            return remoteTaskId.get();
        }

        private String remoteContextId() {
            return remoteContextId.get();
        }

        private boolean markCancelIssued() {
            return cancelIssued.compareAndSet(false, true);
        }
    }
}
