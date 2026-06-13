package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import com.huawei.ascend.runtime.engine.a2a.A2aResultRouter.RouteDecision;
import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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

    A2aRemoteInvocationOrchestrator(RemoteAgentInvocationService invocationService,
            A2aParentTaskProjector parentProjector, String agentId) {
        this.invocationService = invocationService;
        this.parentProjector = parentProjector;
        this.agentId = agentId;
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
        List<RemoteAgentInvocationService.RemoteAgentResult> results =
                invocationService.invoke(invocation,
                        result -> {
                            // A cancel may land while the remote stream is still open; the
                            // task is then CANCELED and must not receive progress artifacts.
                            if (!cancelled.get()) {
                                parentProjector.projectRemoteProgress(result, emitter);
                            }
                        });
        handleRemoteResults(requestContext, invocation, results, emitter, taskId, artifactId, firstArtifact,
                cancelled, resume, beforeTerminal);
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
        List<RemoteAgentInvocationService.RemoteAgentResult> results =
                invocationService.resumeRemoteInput(route, Messages.text(ctx.getMessage()),
                        result -> {
                            if (!cancelled.get()) {
                                parentProjector.projectRemoteProgress(result, emitter);
                            }
                        });
        AgentExecutionResult.RemoteInvocation invocation = parentProjector.remoteInvocation(route);
        handleRemoteResults(ctx, invocation, results, emitter, taskId, artifactId, firstArtifact, cancelled,
                resume, beforeTerminal);
    }

    /** Propagates a local cancel to the remote task a continuation is parked on. Best-effort. */
    void propagateCancel(RequestContext ctx, String taskId) {
        if (invocationService == null || !parentProjector.isRemoteContinuation(ctx)) {
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

    private void handleRemoteResults(RequestContext requestContext,
            AgentExecutionResult.RemoteInvocation invocation,
            List<RemoteAgentInvocationService.RemoteAgentResult> results, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, AtomicBoolean cancelled, LocalResume resume,
            Runnable beforeTerminal) {
        if (cancelled.get()) {
            // The task reached CANCELED while the remote leg was running: re-entering
            // the local handler would burn a full resume against a terminal task, and
            // any emission would fight the terminal state the client already observed.
            // Release the remote side instead so it does not keep running as an orphan.
            cancelRemoteTask(invocation, results, taskId);
            return;
        }
        A2aParentTaskProjector.RemoteOutcome outcome =
                parentProjector.projectRemoteOutcome(invocation, results, emitter);
        if (outcome.waitingForRemoteInput()) {
            LOG.info("[A2A] remote tool invocation waiting-for-input taskId={} remoteAgentId={} toolCallId={}",
                    taskId, invocation.remoteAgentId(), invocation.toolCallId());
            // The task parks on remote INPUT_REQUIRED and this invocation's sinks close on
            // return — flush what this leg collected or it is lost before the continuation.
            beforeTerminal.run();
            return;
        }
        LOG.info("[A2A] remote tool invocation complete taskId={} remoteAgentId={} toolCallId={} resultLen={}",
                taskId, invocation.remoteAgentId(), invocation.toolCallId(),
                outcome.toolResult() != null ? outcome.toolResult().length() : 0);
        AgentExecutionContext resumeContext =
                parentProjector.remoteResumeContext(requestContext, agentId, invocation, outcome.toolResult());
        RouteDecision decision = resume.consume(resumeContext, emitter, taskId, artifactId, firstArtifact,
                cancelled);
        // The trajectory now spans every leg through the resume's RUN_END; deliver it
        // northbound while the task can still accept artifacts, before the terminal lands.
        beforeTerminal.run();
        if (decision.terminalAction() != null) {
            decision.terminalAction().run();
        } else if (!decision.terminalRouted()) {
            A2aResultRouter.completeDrainedStream(taskId, emitter);
        }
    }

    /** Best-effort remote cancel keyed off the remote task id seen in the received results. */
    private void cancelRemoteTask(AgentExecutionResult.RemoteInvocation invocation,
            List<RemoteAgentInvocationService.RemoteAgentResult> results, String taskId) {
        String remoteTaskId = lastNonNull(results, RemoteAgentInvocationService.RemoteAgentResult::remoteTaskId);
        if (remoteTaskId == null) {
            return;
        }
        try {
            invocationService.cancel(new RemoteAgentInvocationService.RemoteTaskReference(
                    invocation.remoteAgentId(), remoteTaskId,
                    lastNonNull(results, RemoteAgentInvocationService.RemoteAgentResult::remoteContextId)));
        } catch (RuntimeException error) {
            LOG.warn("[A2A] remote cancel after local cancel failed taskId={} errorClass={} message={}",
                    taskId, error.getClass().getSimpleName(), A2aLogMasking.mask(error.getMessage()));
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
}
