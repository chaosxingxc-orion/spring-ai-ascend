package com.huawei.ascend.runtime.engine.a2a;

import com.huawei.ascend.runtime.engine.spi.AgentExecutionResult;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maps one adapted {@link AgentExecutionResult} onto the A2A task surface. Invoked
 * synchronously on the execute thread only; never holds the single-writer
 * {@link AgentEmitter} — terminal emissions are deferred into the decision's action so the
 * caller can land the trajectory artifact first.
 */
final class A2aResultRouter {

    private static final Logger LOG = LoggerFactory.getLogger(A2aResultRouter.class);

    private A2aResultRouter() {
    }

    /**
     * Outcome of routing one adapted result. {@code terminalAction} carries the deferred terminal
     * emission (run by the caller after trajectory delivery); {@code terminalRouted} is also true
     * for terminals already emitted elsewhere (the cancel teardown path).
     */
    record RouteDecision(boolean stop, AgentExecutionResult.RemoteInvocation remoteInvocation,
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

    /**
     * Streams an OUTPUT chunk immediately; for the terminal kinds the decision carries the terminal
     * emission as a deferred action the caller runs after any northbound trajectory has been
     * flushed, so the trajectory artifact lands before the task reaches its terminal state.
     */
    static RouteDecision route(AgentExecutionResult result, AgentEmitter emitter, String taskId,
            String artifactId, AtomicBoolean firstArtifact, boolean remoteInvocationAllowed) {
        AgentExecutionResult.Target target = result.target();
        switch (result.type()) {
            case OUTPUT -> {
                String text = outputText(result);
                LOG.info("[A2A] output stream taskId={} textChars={} target={}", taskId, text.length(), target);
                if (target == AgentExecutionResult.Target.USER
                        || target == AgentExecutionResult.Target.BOTH) {
                    boolean append = !firstArtifact.getAndSet(false);
                    Map<String, Object> artifactMd = Map.of("a2a.target", target.name());
                    emitter.addArtifact(List.<Part<?>>of(new TextPart(text)),
                            artifactId, "agent-response", artifactMd, append, false);
                }
                return RouteDecision.continueRoute();
            }
            case COMPLETED -> {
                String text = outputText(result);
                boolean showToUser = target == AgentExecutionResult.Target.USER
                        || target == AgentExecutionResult.Target.BOTH;
                Map<String, Object> completeMetadata = Map.of("a2a.target", target.name());
                return RouteDecision.terminal(() -> {
                    if (showToUser && !text.isBlank()) {
                        LOG.info("[A2A] complete with final output taskId={} textChars={} target={}",
                                taskId, text.length(), target);
                    } else if (!showToUser) {
                        LOG.info("[A2A] complete target=LLM — final content not shown to user taskId={}",
                                taskId);
                    }
                    // Always include a TextPart — the A2A SDK Message constructor
                    // rejects an empty parts list (IllegalArgumentException:
                    // Parts cannot be empty) even when the target is LLM and
                    // the text is intentionally blank.
                    emitter.complete(emitter.newAgentMessage(
                            List.<Part<?>>of(new TextPart(text)), completeMetadata));
                    LOG.info("[A2A] task state=COMPLETED taskId={} target={} textLen={} metadata={}",
                            taskId, target, text.length(), completeMetadata);
                });
            }
            case FAILED -> {
                String code = result.errorCode() == null ? "RUNTIME_ERROR" : result.errorCode();
                String msg = result.errorMessage() == null ? code : result.errorMessage();
                return RouteDecision.terminal(() -> {
                    LOG.warn("[A2A] task state=FAILED taskId={} code={} message={} target={}",
                            taskId, code, A2aLogMasking.mask(msg), target);
                    emitter.fail(A2aAgentExecutor.failureMessage(emitter, code, result.errorMessage(), false));
                });
            }
            case INTERRUPTED -> {
                if (result.interruptPayload() instanceof AgentExecutionResult.RemoteAgentInterrupt remote) {
                    if (!remoteInvocationAllowed) {
                        return RouteDecision.terminal(() -> emitter.fail(A2aAgentExecutor.failureMessage(
                                emitter,
                                "NESTED_REMOTE_INVOCATION_UNSUPPORTED",
                                "remote A2A invocation after REMOTE_RESUME is not supported",
                                false)));
                    }
                    return RouteDecision.remote(remote.remoteInvocation());
                }
                String prompt = result.prompt() == null ? "" : result.prompt();
                return RouteDecision.terminal(() -> {
                    LOG.info("[A2A] task state=INPUT_REQUIRED taskId={} promptChars={} target={}",
                            taskId, prompt.length(), target);
                    Map<String, Object> inputReqMetadata = Map.of("a2a.target", target.name());
                    boolean showPrompt = target == AgentExecutionResult.Target.USER
                            || target == AgentExecutionResult.Target.BOTH;
                    String userPrompt = (showPrompt && !prompt.isBlank()) ? prompt : "";
                    emitter.requiresInput(
                            emitter.newAgentMessage(
                                    List.<Part<?>>of(new TextPart(userPrompt)),
                                    inputReqMetadata),
                            true);
                });
            }
        }
        throw new IllegalStateException("Unsupported result type: " + result.type());
    }

    /** Finalize a stream that drained without a terminal result, or the task stays WORKING forever. */
    static void completeDrainedStream(String taskId, AgentEmitter emitter) {
        LOG.warn("[A2A] result stream ended without terminal result taskId={} - completing", taskId);
        emitter.complete();
    }

    private static String outputText(AgentExecutionResult result) {
        return result.outputContent() != null ? result.outputContent() : "";
    }
}
